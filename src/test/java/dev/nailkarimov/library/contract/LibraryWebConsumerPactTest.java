package dev.nailkarimov.library.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Consumer side: the web front end writes down what it needs from the library API. The pact file lands in
 * target/pacts and the provider test replays it against the real server.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "library-api", pactVersion = PactSpecVersion.V3)
@DisplayName("Pact consumer: library-web")
class LibraryWebConsumerPactTest {
  static final String ISBN = "9780140449136";
  static final String MISSING = "9790000000001";
  static final Map<String, String> JSON = Map.of("Content-Type", "application/json; charset=utf-8");

  @Pact(consumer = "library-web")
  RequestResponsePact shelf(PactDslWithProvider p) {
    return p.given("book 9780140449136 has 2 copies on the shelf")
        .uponReceiving("a request for a book on the shelf")
        .path("/books/" + ISBN)
        .method("GET")
        .willRespondWith()
        .status(200)
        .matchHeader("Content-Type", "application/json.*", "application/json; charset=utf-8")
        .body(
            new PactDslJsonBody()
                .stringValue("isbn", ISBN)
                .stringType("title", "War and Peace")
                .integerType("available", 2))
        .toPact();
  }

  @Pact(consumer = "library-web")
  RequestResponsePact missing(PactDslWithProvider p) {
    return p.given("no book 9790000000001")
        .uponReceiving("a request for a book the library does not have")
        .path("/books/" + MISSING)
        .method("GET")
        .willRespondWith()
        .status(404)
        .body(new PactDslJsonBody().stringValue("code", "BOOK_NOT_FOUND").stringType("message", "no book"))
        .toPact();
  }

  @Pact(consumer = "library-web")
  RequestResponsePact borrow(PactDslWithProvider p) {
    return p.given("member 1 exists and book 9780140449136 has a free copy")
        .uponReceiving("member 1 borrows a free book")
        .path("/loans")
        .method("POST")
        .headers(Map.of("Content-Type", "application/json"))
        .body(new PactDslJsonBody().integerType("memberId", 1).stringValue("isbn", ISBN))
        .willRespondWith()
        .status(201)
        .body(
            new PactDslJsonBody()
                .integerType("id", 1)
                .stringMatcher("due", "\\d{4}-\\d{2}-\\d{2}", "2026-11-05"))
        .toPact();
  }

  @Pact(consumer = "library-web")
  RequestResponsePact limit(PactDslWithProvider p) {
    return p.given("member 1 already has 3 books on loan")
        .uponReceiving("member 1 tries to borrow a fourth book")
        .path("/loans")
        .method("POST")
        .headers(Map.of("Content-Type", "application/json"))
        .body(new PactDslJsonBody().integerType("memberId", 1).stringValue("isbn", ISBN))
        .willRespondWith()
        .status(409)
        .body(new PactDslJsonBody().stringValue("code", "LOAN_LIMIT"))
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "shelf")
  void readsTheShelf(MockServer server) throws Exception {
    LibraryWebClient.Shelf s = new LibraryWebClient(server.getUrl()).shelf(ISBN);
    assertEquals(ISBN, s.isbn());
    assertEquals(2, s.available());
  }

  @Test
  @PactTestFor(pactMethod = "missing")
  void showsAMissingBook(MockServer server) {
    LibraryWebClient.Refused r =
        assertThrows(LibraryWebClient.Refused.class, () -> new LibraryWebClient(server.getUrl()).shelf(MISSING));
    assertEquals("BOOK_NOT_FOUND", r.code);
  }

  @Test
  @PactTestFor(pactMethod = "borrow")
  void borrows(MockServer server) throws Exception {
    LibraryWebClient.Loan loan = new LibraryWebClient(server.getUrl()).borrow(1, ISBN);
    assertTrue(loan.id() > 0);
    assertTrue(loan.due().matches("\\d{4}-\\d{2}-\\d{2}"));
  }

  @Test
  @PactTestFor(pactMethod = "limit")
  void showsTheLimit(MockServer server) {
    LibraryWebClient.Refused r =
        assertThrows(LibraryWebClient.Refused.class, () -> new LibraryWebClient(server.getUrl()).borrow(1, ISBN));
    assertEquals("LOAN_LIMIT", r.code);
  }
}
