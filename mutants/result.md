| # | Bug | Result | First failure |
| --- | --- | --- | --- |
| M1 | loans last 15 days | caught | [ERROR] Tests run: 23, Failures: 8, Errors: 0, Skipped: 0, Time elapsed: 2.337 s <<< FAILURE! -- in dev.nailka |
| M2 | no cap on fines | caught | [ERROR] Tests run: 23, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 2.575 s <<< FAILURE! -- in dev.nailka |
| M3 | fine one day early | caught | [ERROR] Tests run: 23, Failures: 6, Errors: 0, Skipped: 0, Time elapsed: 2.314 s <<< FAILURE! -- in dev.nailka |
| M4 | exactly 5.00 already blocks | caught | [ERROR] Tests run: 23, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 2.546 s <<< FAILURE! -- in dev.nailka |
| M5 | four books allowed | caught | [ERROR] Tests run: 4, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.769 s <<< FAILURE! -- in dev.nailkar |
| M6 | two copies of one title | caught | [ERROR] Tests run: 23, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 2.490 s <<< FAILURE! -- in dev.nailka |
| M7 | a loan can be returned twice | caught | [ERROR] Tests run: 23, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 2.692 s <<< FAILURE! -- in dev.nailka |
| M8 | e-mails compared case-sensitively | caught | [ERROR] Tests run: 11, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 0.449 s <<< FAILURE! -- in dev.nailka |
| M9 | ISBN check digit not verified | caught | [ERROR] Tests run: 9, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 6.458 s <<< FAILURE! -- in dev.nailkar |
| M10 | no retry | caught | [ERROR] Tests run: 9, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 6.256 s <<< FAILURE! -- in dev.nailkar |
| M11 | unknown ISBN reported as a catalogue failure | caught | [ERROR] Tests run: 9, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 6.114 s <<< FAILURE! -- in dev.nailkar |
| M12 | no request timeout | caught | [ERROR] Tests run: 9, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 6.984 s <<< FAILURE! -- in dev.nailkar |
| M13 | any content type accepted | caught | [ERROR] Tests run: 12, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.404 s <<< FAILURE! -- in dev.nailka |
| M14 | duplicate JSON keys accepted | caught | [ERROR] Tests run: 12, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.357 s <<< FAILURE! -- in dev.nailka |

All mutants caught.
