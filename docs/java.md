# Testing in Java: a library API

The service is ours: a **Library API** on the JDK's built-in HTTP server, no framework
(`src/main/java/dev/nailkarimov/library`). Members borrow books for 14 days; late returns cost 0.25 per day, at
most 10.00 per loan; a member holds at most 3 books, never two copies of one title, and cannot borrow while owing
more than 5.00. A new ISBN is looked up in an **external catalogue** service (title, authors) with a 500 ms timeout
and one retry.

| Layer | Tool | Where | Tests |
| --- | --- | --- | ---: |
| API | REST Assured + JUnit 5, JSON Schema | `api/` | 63 |
| Dependency faults | WireMock as the catalogue | `mock/` | 9 |
| Behaviour (BDD) | Cucumber, `lending.feature` | `bdd/` | 9 scenarios |
| Contract | Pact JVM, consumer + provider | `contract/` | 4 + 4 |

Every test class starts its own library on a free port (`TestLibrary`): the real server, a WireMock catalogue and
a clock the test can move — "15 days later" is `lib.clock.plusDays(15)`, not a wait.

## 1. REST Assured

```java
api.borrow(member, isbn)
    .then()
    .statusCode(201)
    .body(matchesJsonSchemaInClasspath("schemas/loan.json"))
    .body("due", equalTo("2026-10-15"))
    .body("returned", nullValue());
```

The fine rule is a table of boundaries — 0, 14 (due date), 15 (first day late), 16, 53 (9.75), 54 (exactly the cap),
55 and 400 days — each checked over HTTP **and** against `Library.fineFor`, so the answer and the rule cannot drift
apart. A race test sends 20 borrow requests for 5 copies at once: exactly 5 loans, 15 refusals, 0 left on the shelf.

## 2. WireMock: a dependency that misbehaves on purpose

| The catalogue | The library answers | Calls |
| --- | --- | ---: |
| 404 | 422 `UNKNOWN_ISBN` (the user's problem) | 1 |
| 500, then 200 | 201, the retry saved it | 2 |
| 500 every time | 503 `CATALOGUE_UNAVAILABLE`, nothing stored | 2 |
| 1.5 s delay | 503 in under 1.4 s (2 × 300 ms timeout) | 2 |
| connection reset, then 200 | 201 | 2 |
| HTML instead of JSON | 502 `CATALOGUE_BAD_RESPONSE` (the catalogue's problem), not retried | 1 |
| a title already on the shelf | 201, no call at all | 0 |
| an invalid ISBN | 400 before any call | 0 |

The call count is half of every assertion: a library that answers correctly but hammers a failing dependency is
still broken.

## 3. Cucumber

```gherkin
Scenario Outline: Fines for late returns
  Given Ann has borrowed "The Master and Margarita"
  And <days> days have passed
  When Ann returns "The Master and Margarita"
  Then the fine is <fine>
  And Ann owes <fine>
```

Steps speak the library's language (`{word} borrows {string}`, `the library refuses with {string}`), a custom
`{isoDate}` parameter type turns dates into `LocalDate`, and names like "Ann" are local to a scenario, so scenarios
never share data. Reports: Allure and Cucumber's own HTML.

## 4. Pact JVM

The consumer is the web front end (`LibraryWebClient`). It records only what it reads: `isbn`, `title`,
`available` of a book; `id` and `due` (a date by regex) of a loan; the `code` of a refusal. Four interactions with
provider states:

- "book 9780140449136 has 2 copies on the shelf"
- "no book 9790000000001"
- "member 1 exists and book 9780140449136 has a free copy"
- "member 1 already has 3 books on loan"

The provider test starts a **fresh** library for every interaction, so "member 1" is really the first member, runs
the `@State` method, and replays the request against the real server. In CI the consumer test runs first and writes
`target/pacts`; the pact file is published on Pages.

## 5. Mutation check

`scripts/mutants.py` plants one bug at a time and runs the whole suite (a CI job, results on Pages):

| # | Bug | Caught by |
| --- | --- | --- |
| M1 | loans last 15 days | loans, Cucumber |
| M2 | no cap on fines | fine table |
| M3 | late days counted one too early | fine table, Cucumber |
| M4 | exactly 5.00 already blocks | `finesBlock` |
| M5 | four books allowed | Pact provider state, loans, Cucumber |
| M6 | two copies of one title | loans |
| M7 | a loan can be returned twice | loans |
| M8 | e-mails compared case-sensitively | members |
| M9 | ISBN check digit not verified | WireMock: the catalogue got a call it should not |
| M10 | no retry | WireMock |
| M11 | catalogue 404 reported as a failure | WireMock |
| M12 | no request timeout | WireMock (`time(lessThan(1400L))`) |
| M13 | any content type accepted | HTTP rules |
| M14 | duplicate JSON keys accepted | HTTP rules |

14 of 14. M9 is a nice case: the API tests reject the bad ISBN anyway — later, with a different error — but only the
WireMock call count shows that an invalid value leaked to a paid external service.

## 6. Building without a local Maven repository

This repository was written in an environment that could not reach Maven Central, so CI is the only build. The
workflow publishes the build logs and surefire reports to `…/diag/` on Pages, which is how a failure is read when
the Actions logs are out of reach.
