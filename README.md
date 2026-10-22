# java-testing

[![Tests](https://github.com/NailKarimov/java-testing/actions/workflows/tests.yml/badge.svg)](https://github.com/NailKarimov/java-testing/actions/workflows/tests.yml)
[![Allure report](https://img.shields.io/badge/report-Allure-orange)](https://nailkarimov.github.io/java-testing/)

API, mocking, BDD and contract tests **in Java** on a Library API of our own — written on the JDK alone
(`com.sun.net.httpserver`, a 250-line JSON reader), so the tests have nothing to hide behind:
**REST Assured + JUnit 5**, **WireMock**, **Cucumber**, **Pact JVM**, JSON Schema, Allure, JaCoCo.

| Suite | Tool | What it proves |
| --- | --- | --- |
| `api/MembersApiTest`, `BooksApiTest`, `LoansApiTest` | REST Assured, JUnit 5 parameterized tests, JSON Schema | validation boundaries, ISBN-13 check digits, the 14-day loan, fines 0.25/day capped at 10.00 (8 boundary cases), the 3-book limit, unpaid fines block borrowing, 20 members racing for 5 copies |
| `api/HttpProtocolTest` | REST Assured | invalid JSON (duplicate keys, trailing data, 40-level nesting) is 400, 415 / 413 / 405 / 404 as JSON errors, security headers, no stack traces |
| `mock/CatalogueFaultsTest` | WireMock | the external catalogue answers 404, 500 once, 500 always, slower than the timeout, resets the connection, sends HTML: each test checks the library's answer **and how many calls** the catalogue got |
| `bdd/lending.feature` | Cucumber | the lending rules in Gherkin: borrowing, the last copy, a fines table, paying down to unblock, three books at a time |
| `contract/*Pact*` | Pact JVM | the web front end (consumer) records 4 interactions; the provider test replays them against the real server with provider states |

89 tests in about 15 s; line coverage 88 %. **Mutation check:** 14 planted bugs (15-day loans, no fine cap, an
off-by-one in late days, no retry, no timeout, duplicate JSON keys…), all caught — the four-book bug first by the
Pact provider state. Details: [`docs/java.md`](docs/java.md) · visual guide:
[`docs/java.html`](https://nailkarimov.github.io/java-testing/java.html).

## Running it

```bash
mvn test -Dtest=LibraryWebConsumerPactTest   # writes target/pacts for the provider test
mvn test                                     # everything; reports in target/
python3 scripts/mutants.py                   # 14 planted bugs, one at a time
```

Published on Pages: the [Allure report](https://nailkarimov.github.io/java-testing/),
[Cucumber report](https://nailkarimov.github.io/java-testing/cucumber/),
[JaCoCo coverage](https://nailkarimov.github.io/java-testing/coverage/),
[the pact file](https://nailkarimov.github.io/java-testing/pacts/) and
[the mutation result](https://nailkarimov.github.io/java-testing/mutants/result.md).

Part of a QA portfolio: [github.com/NailKarimov](https://github.com/NailKarimov).
