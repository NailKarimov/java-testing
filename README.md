# java-testing

[![Tests](https://github.com/NailKarimov/java-testing/actions/workflows/tests.yml/badge.svg)](https://github.com/NailKarimov/java-testing/actions/workflows/tests.yml)
[![Allure report](https://img.shields.io/badge/report-Allure-orange)](https://nailkarimov.github.io/java-testing/)
[![Release](https://img.shields.io/github/v/release/NailKarimov/java-testing)](https://github.com/NailKarimov/java-testing/releases)
[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/NailKarimov/java-testing?quickstart=1)

### ▶ Open online

| Page | What it is |
| --- | --- |
| **[Allure test report](https://nailkarimov.github.io/java-testing/)** | latest CI run |
| [Cucumber report](https://nailkarimov.github.io/java-testing/cucumber/) | BDD scenarios |
| [JaCoCo coverage](https://nailkarimov.github.io/java-testing/coverage/) | latest CI run |
| [Pact contract](https://nailkarimov.github.io/java-testing/pacts/library-web-library-api.json) | JSON |
| [Planted bugs](https://nailkarimov.github.io/java-testing/mutants/result.md) | mutation check result |
| [Java tests](https://nailkarimov.github.io/java-testing/java.html) | interactive guide |

Click these links: opening an `.html` file in the file list shows its source code, GitHub does not run it.

API, mocking, BDD and contract tests **in Java**. They run against a Library API of our own, written on the JDK
alone (`com.sun.net.httpserver` and a small JSON reader), so the tests have nothing to hide behind.

| | |
| --- | --- |
| **System under test** | own Library API (`src/main/java`): members, books looked up in an external catalogue, loans, fines |
| **Language and tools** | Java 21 · JUnit 5 · REST Assured · JSON Schema · WireMock · Cucumber · Pact JVM · Allure · JaCoCo · Maven |
| **Types of testing** | functional API, mocking (service virtualisation), BDD, consumer-driven contracts, concurrency |
| **Tests** | 89 (~15 s); line coverage 88 % |
| **Test quality** | 14/14 planted bugs caught (`scripts/mutants.py`, a CI job) |
| **Reports** | [Allure](https://nailkarimov.github.io/java-testing/) · [Cucumber](https://nailkarimov.github.io/java-testing/cucumber/) · [JaCoCo](https://nailkarimov.github.io/java-testing/coverage/) · [pact file](https://nailkarimov.github.io/java-testing/pacts/library-web-library-api.json) · [mutants](https://nailkarimov.github.io/java-testing/mutants/result.md) · [visual guide](https://nailkarimov.github.io/java-testing/java.html) |

## What is tested

| Suite | Tool | What it proves |
| --- | --- | --- |
| `api/MembersApiTest`, `BooksApiTest`, `LoansApiTest` | REST Assured, JUnit 5 parameterized tests, JSON Schema | validation boundaries; ISBN-13 check digits; the 14-day loan; fines of 0.25 per day capped at 10.00 (8 boundary cases); the 3-book limit; unpaid fines block borrowing; 20 members racing for 5 copies |
| `api/HttpProtocolTest` | REST Assured | invalid JSON is 400 (duplicate keys, trailing data, 40 levels of nesting); 415, 413, 405 and 404 as JSON errors; security headers; no stack traces |
| `mock/CatalogueFaultsTest` | WireMock | the external catalogue answers 404, 500 once, 500 always, slower than the timeout, a reset, HTML: each test checks the library's answer **and the number of calls** |
| `bdd/lending.feature` | Cucumber | the lending rules in Gherkin: borrowing, the last copy, a table of fines, paying down to unblock, three books at a time |
| `contract/*Pact*` | Pact JVM | the web front end (consumer) records 4 interactions; the provider test replays them against the real server with provider states |

## Findings

- **Call counts catch what answers hide (M9).** Without the ISBN check the request still fails, with 422 from the
  catalogue instead of 400. Only the WireMock call count shows that an invalid value reached the external
  service.
- **A Pact provider state caught a business bug (M5).** The "four books allowed" mutant failed first in the
  provider test, because the state "member 1 already has 3 books on loan" no longer led to a 409.
- **Built without a local Maven repository.** CI publishes the build logs and surefire reports to `…/diag/` on
  Pages. That is how failures were read when the Actions logs were out of reach.

## Running it

No local setup: the **Open in GitHub Codespaces** badge starts a browser VS Code with this repository, its tools and Docker; the container runs `mvn -q -DskipTests test-compile` on creation, then `mvn test` works in the terminal.

Requirements: JDK 21, Maven 3.9.

```bash
mvn test -Dtest=LibraryWebConsumerPactTest   # writes target/pacts for the provider test
mvn test                                     # everything; reports in target/
mvn test -Dtest=CatalogueFaultsTest          # WireMock only
mvn test -Dtest=RunCucumberTest              # Cucumber only
python3 scripts/mutants.py                   # mutation check
```

## Project structure

```text
src/main/java/.../library/        the system under test: Library, CatalogueClient, LibraryServer, Json, Isbn
src/test/java/.../support/        TestLibrary (server + WireMock + movable clock), Api steps, unique ISBNs
src/test/java/.../api/            REST Assured tests
src/test/java/.../mock/           WireMock fault tests
src/test/java/.../bdd/            Cucumber runner, steps, parameter types
src/test/java/.../contract/       Pact consumer (LibraryWebClient) and provider
src/test/resources/features/      lending.feature
src/test/resources/schemas/       JSON Schemas for members, books, loans, errors
scripts/mutants.py                mutation check
docs/                             test plan, test report, guides
```

## CI

`.github/workflows/tests.yml` runs on push, pull request, nightly at 05:00 UTC and on demand:
- the `test` job compiles, runs the Pact consumer first and then the whole suite, and writes a per-class table to
  the job summary;
- it publishes the Allure report with history, the Cucumber report, JaCoCo, the pact file and diagnostics to
  GitHub Pages;
- the `mutants` job runs the mutation check and publishes `mutants/result.md`.

Releases: committing `.github/releases/<tag>.md` runs the Release workflow, which creates the tag and the GitHub release with that file as its notes.

## Documentation

| Document | What it is |
| --- | --- |
| [`docs/test-plan.md`](docs/test-plan.md) | test plan (ISO/IEC/IEEE 29119-3) |
| [`docs/test-report.md`](docs/test-report.md) | test summary report: latest results, findings, recommendations |
| [`docs/java.md`](docs/java.md) | guide: REST Assured, WireMock fault table, Cucumber, Pact, the mutation table |
| [`docs/java.html`](https://nailkarimov.github.io/java-testing/java.html) | the same guide, interactive: a fine calculator, catalogue faults with call counts, the Pact handshake |

## Part of a portfolio

One of 13 test automation projects by [Nail Karimov](https://github.com/NailKarimov):
[all projects](https://github.com/NailKarimov) ·
[testing map](https://nailkarimov.github.io/api-test-automation/testing-map.html) ·
[toolbox](https://nailkarimov.github.io/api-test-automation/toolbox.html).
