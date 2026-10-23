# Test plan: Library API (java-testing)

The structure follows ISO/IEC/IEEE 29119-3 (test plan), shortened for one project.

## 1. Context

- **System under test:** a Library API of our own on the JDK's HTTP server. A new ISBN is looked up in an
  external catalogue service, with a 500 ms timeout and one retry.
- **Goal:** show that the lending rules hold at every boundary; the library behaves well when the catalogue
  misbehaves; the API keeps its contract with the web front end. Prove that the tests catch real bugs.
- **References:** ISO/IEC 25010, Pact specification v3, JSON Schema draft-07, ISBN-13 (ISO 2108).

## 2. Scope

| In scope | Out of scope |
| --- | --- |
| members, books, loans, returns, fines, payments | a UI (only the front end's API client) |
| the external catalogue: answers, faults, retries, timeout | the catalogue itself (WireMock plays it) |
| HTTP rules: content type, body size, JSON validity, methods | authentication (the API has none) |
| the contract with the web front end | other consumers |

## 3. Quality characteristics (ISO/IEC 25010)

| Characteristic | Covered by |
| --- | --- |
| Functional correctness | REST Assured tests with boundary tables, Cucumber scenarios |
| Compatibility (interoperability) | Pact consumer and provider tests |
| Reliability (fault tolerance) | WireMock fault tests: 404, 500, delays, resets, bad payloads |
| Reliability (integrity under concurrency) | 20 members racing for 5 copies |
| Security | strict JSON parsing, size limits, no internals in errors |

## 4. Approach

- **Levels:** API tests against the real server on a free port (`TestLibrary`); a dependency replaced by WireMock;
  BDD scenarios through the same API; the contract tested on both sides.
- **Techniques:**
  - boundary values: fines on days 0, 14, 15, 16, 53, 54, 55 and 400; copies 1 and 20; the 80-character name;
  - equivalence classes: ISBN, e-mail, JSON errors;
  - a movable clock instead of waiting;
  - call counts as a second assertion.
- **Test data:** unique ISBNs and e-mails per test. Names in Cucumber scenarios are local to a scenario. A fresh
  library per Pact interaction.
- **Test quality:** `scripts/mutants.py` plants 14 bugs and runs the whole suite for each.

## 5. Risks

| Risk | Mitigation |
| --- | --- |
| The provider test runs before the pact exists | CI runs the consumer test first, in its own step |
| Timing assertions (the 300 ms timeout) are flaky | a 1.4 s bound for a ≈ 0.6 s budget |
| Floating-point money | `BigDecimal` in the service; `comparesEqualTo` in the steps |

## 6. Entry and exit criteria

- **Entry:** the project compiles with `-Xlint:all`.
- **Exit:** all tests pass, the provider verifies every interaction, and every planted bug is caught.

## 7. Deliverables

The test suite, the Allure, Cucumber, JaCoCo and pact reports on GitHub Pages, [`test-report.md`](test-report.md),
[`java.md`](java.md) and its visual guide.
