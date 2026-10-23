# Test summary report: Library API (java-testing)

The structure follows ISO/IEC/IEEE 29119-3 (test completion report), shortened.

- **Period:** 22.10.2026 – 23.10.2026
- **Build:** `main`, latest nightly run in GitHub Actions
- **Allure report:** https://nailkarimov.github.io/java-testing/

## 1. Summary

All 89 tests pass, and the provider honours all 4 interactions of the front end's contract. Under every
catalogue fault, the library answers correctly and calls the catalogue exactly as often as designed. Every
planted bug is caught.

## 2. Results

| Metric | Value |
| --- | --- |
| Tests run | 89: 63 API, 9 WireMock, 9 Cucumber scenarios, 4 Pact consumer, 4 Pact provider |
| Passed / failed / skipped | 89 / 0 / 0 |
| Duration | ~15 s |
| Line / branch coverage (JaCoCo) | 88 % / 82 % |
| Mutation check | 14 of 14 planted bugs caught |

## 3. Findings

| ID | Severity | Summary | Status |
| --- | --- | --- | --- |
| JV-1 | info | without the ISBN check, invalid values reach the paid external catalogue; only the WireMock call count shows it | covered by `CatalogueFaultsTest.invalidNeverCalls` |
| JV-2 | info | the four-book bug is caught first by a Pact provider state | kept as a strength of the contract |

## 4. Deviations from the plan

- The project was built and verified only in CI, because Maven Central could not be reached from the authoring
  environment. CI publishes diagnostics to `…/diag/` on Pages for that reason.

## 5. Recommendations

1. Publish the pact to a Pact Broker as soon as a second consumer appears.
2. Keep the call-count assertions in the WireMock tests. They catch bugs that the status codes hide.
