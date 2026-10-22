| # | Bug | Result | Failed test classes |
| --- | --- | --- | --- |
| M1 | loans last 15 days | caught | LoansApiTest, RunCucumberTest |
| M2 | no cap on fines | caught | LoansApiTest, RunCucumberTest |
| M3 | fine one day early | caught | LoansApiTest, RunCucumberTest |
| M4 | exactly 5.00 already blocks | caught | LoansApiTest, RunCucumberTest |
| M5 | four books allowed | caught | LibraryApiProviderPactTest, LoansApiTest, RunCucumberTest |
| M6 | two copies of one title | caught | LoansApiTest |
| M7 | a loan can be returned twice | caught | LoansApiTest |
| M8 | e-mails compared case-sensitively | caught | MembersApiTest |
| M9 | ISBN check digit not verified | caught | BooksApiTest, CatalogueFaultsTest |
| M10 | no retry | caught | CatalogueFaultsTest |
| M11 | unknown ISBN reported as a catalogue failure | caught | CatalogueFaultsTest |
| M12 | no request timeout | caught | CatalogueFaultsTest |
| M13 | any content type accepted | caught | HttpProtocolTest |
| M14 | duplicate JSON keys accepted | caught | HttpProtocolTest |

All mutants caught.
