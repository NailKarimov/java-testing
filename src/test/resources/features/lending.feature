Feature: Lending books
  Members borrow books for 14 days. Late returns cost 0.25 per day, at most 10.00 per loan.
  A member holds at most 3 books and cannot borrow while owing more than 5.00.

  Background:
    Given today is 2026-10-01
    And the library has 1 copy of "The Master and Margarita"
    And Ann is a member

  Scenario: Borrowing takes the copy off the shelf
    When Ann borrows "The Master and Margarita"
    Then the loan is due on 2026-10-15
    And "The Master and Margarita" has 0 copies available

  Scenario: The last copy goes to whoever asks first
    Given Boris is a member
    And Ann has borrowed "The Master and Margarita"
    When Boris borrows "The Master and Margarita"
    Then the library refuses with "NO_COPIES"

  Scenario Outline: Fines for late returns
    Given Ann has borrowed "The Master and Margarita"
    And <days> days have passed
    When Ann returns "The Master and Margarita"
    Then the fine is <fine>
    And Ann owes <fine>

    Examples:
      | days | fine  |
      | 14   | 0.00  |
      | 15   | 0.25  |
      | 30   | 4.00  |
      | 54   | 10.00 |
      | 90   | 10.00 |

  Scenario: Unpaid fines block borrowing until paid down to 5.00
    Given Ann has borrowed "The Master and Margarita"
    And 35 days have passed
    And Ann returns "The Master and Margarita"
    And the library has 1 copy of "Dead Souls"
    When Ann borrows "Dead Souls"
    Then the library refuses with "FINES_UNPAID"
    When Ann pays 0.25
    And Ann borrows "Dead Souls"
    Then the loan is due on 2026-11-19

  Scenario: Three books at a time
    Given the library has 1 copy of each of these books:
      | Anna Karenina   |
      | Fathers and Sons |
      | Oblomov         |
      | The Idiot       |
    And Ann has borrowed "Anna Karenina"
    And Ann has borrowed "Fathers and Sons"
    And Ann has borrowed "Oblomov"
    When Ann borrows "The Idiot"
    Then the library refuses with "LOAN_LIMIT"
    When Ann returns "Oblomov"
    And Ann borrows "The Idiot"
    Then the loan is due on 2026-10-15
