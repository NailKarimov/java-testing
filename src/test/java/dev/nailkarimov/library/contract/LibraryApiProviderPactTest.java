package dev.nailkarimov.library.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import dev.nailkarimov.library.support.Api;
import dev.nailkarimov.library.support.TestLibrary;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Provider side: every interaction in the consumer's pact is replayed against the real server. Each interaction gets
 * a fresh library, so "member 1" really is the first member.
 */
@Provider("library-api")
@PactFolder("target/pacts")
@DisplayName("Pact provider: library-api")
class LibraryApiProviderPactTest {
  static final String ISBN = LibraryWebConsumerPactTest.ISBN;
  TestLibrary lib;
  Api api;

  @BeforeEach
  void fresh(PactVerificationContext context) {
    lib = TestLibrary.start();
    api = new Api(lib);
    lib.catalogue.stubFor(get(urlPathMatching("/isbn/.*")).willReturn(okJson("{\"title\":\"War and Peace\",\"authors\":[\"Leo Tolstoy\"]}")));
    context.setTarget(new HttpTestTarget("127.0.0.1", lib.server.port()));
  }

  @AfterEach
  void close() {
    lib.close();
  }

  @TestTemplate
  @ExtendWith(PactVerificationInvocationContextProvider.class)
  void verify(PactVerificationContext context) {
    context.verifyInteraction();
  }

  @State("book 9780140449136 has 2 copies on the shelf")
  void twoCopies() {
    api.post("/books", Map.of("isbn", ISBN, "copies", 2)).then().statusCode(201);
  }

  @State("no book 9790000000001")
  void noBook() {
    // A fresh library has no books.
  }

  @State("member 1 exists and book 9780140449136 has a free copy")
  void memberAndCopy() {
    api.newMember();
    api.post("/books", Map.of("isbn", ISBN, "copies", 1)).then().statusCode(201);
  }

  @State("member 1 already has 3 books on loan")
  void atLimit() {
    long member = api.newMember();
    api.post("/books", Map.of("isbn", ISBN, "copies", 1)).then().statusCode(201);
    for (int k = 0; k < 3; k++) {
      api.borrowed(member, api.newBook(1));
    }
  }
}
