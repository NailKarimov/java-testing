package dev.nailkarimov.library.api;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nailkarimov.library.Library;
import dev.nailkarimov.library.support.Api;
import dev.nailkarimov.library.support.TestLibrary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Loans")
class LoansApiTest {
  static TestLibrary lib;
  static Api api;

  @BeforeAll
  static void start() {
    lib = TestLibrary.start();
    api = new Api(lib);
  }

  @AfterAll
  static void stop() {
    lib.close();
  }

  @BeforeEach
  void resetClock() {
    lib.clock.set(TestLibrary.START);
  }

  @Test
  @DisplayName("a loan is due in 14 days and takes a copy off the shelf")
  void borrow() {
    long member = api.newMember();
    String isbn = api.newBook(2);
    api.borrow(member, isbn)
        .then()
        .statusCode(201)
        .body(matchesJsonSchemaInClasspath("schemas/loan.json"))
        .body("borrowed", equalTo("2026-10-01"))
        .body("due", equalTo("2026-10-15"))
        .body("returned", nullValue());
    api.get("/books/" + isbn).then().body("available", equalTo(1));
    api.get("/members/" + member).then().body("loans", hasSize(1));
  }

  @Test
  @DisplayName("the last copy can be borrowed once; the next member gets 409 NO_COPIES")
  void lastCopy() {
    String isbn = api.newBook(1);
    api.borrow(api.newMember(), isbn).then().statusCode(201);
    api.borrow(api.newMember(), isbn).then().statusCode(409).body("code", equalTo("NO_COPIES"));
  }

  @Test
  @DisplayName("a member cannot hold two copies of the same title")
  void sameTitleTwice() {
    long member = api.newMember();
    String isbn = api.newBook(3);
    api.borrow(member, isbn).then().statusCode(201);
    api.borrow(member, isbn).then().statusCode(409).body("code", equalTo("ALREADY_BORROWED"));
  }

  @Test
  @DisplayName("three books is the limit; returning one frees a place")
  void loanLimit() {
    long member = api.newMember();
    long first = api.borrowed(member, api.newBook(1));
    api.borrowed(member, api.newBook(1));
    api.borrowed(member, api.newBook(1));
    String fourth = api.newBook(1);
    api.borrow(member, fourth).then().statusCode(409).body("code", equalTo("LOAN_LIMIT"));
    api.giveBack(first).then().statusCode(200);
    api.borrow(member, fourth).then().statusCode(201);
  }

  @ParameterizedTest(name = "returned {0} days after borrowing → fine {1}")
  @CsvSource({"0, 0.00", "14, 0.00", "15, 0.25", "16, 0.50", "53, 9.75", "54, 10.00", "55, 10.00", "400, 10.00"})
  void fines(int daysKept, String fine) {
    long member = api.newMember();
    long loan = api.borrowed(member, api.newBook(1));
    lib.clock.plusDays(daysKept);
    api.giveBack(loan).then().statusCode(200).body("fine", equalTo(new BigDecimal(fine).floatValue()));
    assertEquals(
        new BigDecimal(fine),
        Library.fineFor(TestLibrary.START.plusDays(Library.LOAN_DAYS), TestLibrary.START.plusDays(daysKept)),
        "the HTTP answer and the rule agree");
  }

  @Test
  @DisplayName("returning twice is 409 and does not charge the fine twice")
  void returnTwice() {
    long member = api.newMember();
    long loan = api.borrowed(member, api.newBook(1));
    lib.clock.plusDays(20);
    api.giveBack(loan).then().statusCode(200).body("fine", equalTo(1.50f));
    api.giveBack(loan).then().statusCode(409).body("code", equalTo("ALREADY_RETURNED"));
    api.get("/members/" + member).then().body("fines", equalTo(1.50f));
  }

  @Test
  @DisplayName("fines over 5.00 block borrowing until paid down; exactly 5.00 does not")
  void finesBlock() {
    long member = api.newMember();
    long loan = api.borrowed(member, api.newBook(1));
    lib.clock.plusDays(14 + 21); // 21 days late → 5.25
    api.giveBack(loan).then().body("fine", equalTo(5.25f));
    String next = api.newBook(1);
    api.borrow(member, next).then().statusCode(409).body("code", equalTo("FINES_UNPAID"));

    api.post("/members/" + member + "/payments", Map.of("amount", new BigDecimal("0.25")))
        .then()
        .statusCode(200)
        .body("fines", equalTo(5.00f));
    api.borrow(member, next).then().statusCode(201);
  }

  @ParameterizedTest(name = "payment {0} → 400")
  @CsvSource({"0", "-1", "0.001", "1001", "'\"1\"'", "99"})
  void invalidPayment(String amount) {
    long member = api.newMember();
    api.post("/members/" + member + "/payments", "{\"amount\":" + amount + "}")
        .then()
        .statusCode(400)
        .body("code", equalTo("VALIDATION"));
  }

  @Test
  @DisplayName("unknown member, book and loan are 404; a non-positive member id is 400")
  void notFound() {
    String isbn = api.newBook(1);
    api.borrow(999_999, isbn).then().statusCode(404).body("code", equalTo("MEMBER_NOT_FOUND"));
    api.borrow(api.newMember(), "9790000000001").then().statusCode(404).body("code", equalTo("BOOK_NOT_FOUND"));
    api.giveBack(999_999).then().statusCode(404).body("code", equalTo("LOAN_NOT_FOUND"));
    api.borrow(0, isbn).then().statusCode(400);
  }

  @Test
  @DisplayName("20 members race for 5 copies: exactly 5 loans, never a negative shelf")
  void race() throws Exception {
    String isbn = api.newBook(5);
    List<Long> members = new ArrayList<>();
    for (int k = 0; k < 20; k++) {
      members.add(api.newMember());
    }
    ExecutorService pool = Executors.newFixedThreadPool(20);
    try {
      List<Future<Integer>> codes = new ArrayList<>();
      for (long m : members) {
        codes.add(pool.submit(() -> api.borrow(m, isbn).statusCode()));
      }
      int created = 0;
      int conflict = 0;
      for (Future<Integer> f : codes) {
        int c = f.get();
        created += c == 201 ? 1 : 0;
        conflict += c == 409 ? 1 : 0;
      }
      assertEquals(5, created, "loans");
      assertEquals(15, conflict, "refusals");
    } finally {
      pool.shutdownNow();
    }
    api.get("/books/" + isbn).then().body("available", equalTo(0));
  }

  @Test
  @DisplayName("GET /loans/{id} shows the loan as stored")
  void readLoan() {
    long member = api.newMember();
    String isbn = api.newBook(1);
    long loan = api.borrowed(member, isbn);
    api.get("/loans/" + loan).then().statusCode(200).body("memberId", equalTo((int) member)).body("isbn", equalTo(isbn));
    assertEquals(LocalDate.of(2026, 10, 15).toString(), api.get("/loans/" + loan).jsonPath().getString("due"));
  }
}
