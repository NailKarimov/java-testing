package dev.nailkarimov.library.mock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.http.Fault;
import dev.nailkarimov.library.support.Api;
import dev.nailkarimov.library.support.Isbns;
import dev.nailkarimov.library.support.TestLibrary;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The library depends on an external catalogue. WireMock plays the catalogue and misbehaves on purpose; each test
 * checks the answer the library gives AND how many times it called the catalogue.
 */
@DisplayName("External catalogue faults (WireMock)")
class CatalogueFaultsTest {
  static TestLibrary lib;
  static Api api;
  String isbn;

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
  void fresh() {
    lib.catalogue.resetAll();
    isbn = Isbns.next();
  }

  private String path() {
    return "/isbn/" + isbn;
  }

  private int calls() {
    return lib.catalogue.countRequestsMatching(getRequestedFor(urlEqualTo(path())).build()).getCount();
  }

  private Response add() {
    return api.post("/books", Map.of("isbn", isbn, "copies", 1));
  }

  @Test
  @DisplayName("the catalogue does not know the ISBN → 422 UNKNOWN_ISBN, asked once")
  void unknownIsbn() {
    lib.catalogue.stubFor(get(urlEqualTo(path())).willReturn(notFound()));
    add().then().statusCode(422).body("code", equalTo("UNKNOWN_ISBN"));
    assertEquals(1, calls());
  }

  @Test
  @DisplayName("one 500, then a good answer → the retry saves the request: 201, asked twice")
  void oneServerError() {
    lib.catalogue.stubFor(
        get(urlEqualTo(path())).inScenario("flaky").whenScenarioStateIs(STARTED).willReturn(serverError()).willSetStateTo("ok"));
    lib.catalogue.stubFor(
        get(urlEqualTo(path())).inScenario("flaky").whenScenarioStateIs("ok").willReturn(okJson("{\"title\":\"Recovered\",\"authors\":[]}")));
    add().then().statusCode(201).body("title", equalTo("Recovered"));
    assertEquals(2, calls());
  }

  @Test
  @DisplayName("500 every time → 503 CATALOGUE_UNAVAILABLE after exactly two calls, nothing stored")
  void alwaysServerError() {
    lib.catalogue.stubFor(get(urlEqualTo(path())).willReturn(serverError()));
    add().then().statusCode(503).body("code", equalTo("CATALOGUE_UNAVAILABLE"));
    assertEquals(2, calls());
    api.get("/books/" + isbn).then().statusCode(404);
  }

  @Test
  @DisplayName("slower than the 300 ms timeout → 503 well within a second, not a hanging request")
  void slow() {
    lib.catalogue.stubFor(get(urlEqualTo(path())).willReturn(okJson("{\"title\":\"Late\"}").withFixedDelay(1500)));
    add().then().statusCode(503).time(lessThan(1400L));
  }

  @Test
  @DisplayName("connection reset → retried; the second answer is used")
  void reset() {
    lib.catalogue.stubFor(
        get(urlEqualTo(path())).inScenario("reset").whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)).willSetStateTo("ok"));
    lib.catalogue.stubFor(
        get(urlEqualTo(path())).inScenario("reset").whenScenarioStateIs("ok").willReturn(okJson("{\"title\":\"After reset\"}")));
    add().then().statusCode(201).body("title", equalTo("After reset"));
  }

  @Test
  @DisplayName("a broken transfer is retried and ends in 503; HTML instead of JSON is 502 and not retried")
  void garbage() {
    lib.catalogue.stubFor(get(urlEqualTo(path())).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
    add().then().statusCode(503);
    lib.catalogue.resetAll();
    lib.catalogue.stubFor(get(urlEqualTo(path())).willReturn(okJson("<html>maintenance</html>")));
    add().then().statusCode(502).body("code", equalTo("CATALOGUE_BAD_RESPONSE"));
    assertEquals(1, calls());
  }

  @Test
  @DisplayName("an answer without a title → 502: the catalogue's fault, not the user's")
  void noTitle() {
    lib.catalogue.stubFor(get(urlEqualTo(path())).willReturn(okJson("{\"title\":\"  \",\"authors\":[\"X\"]}")));
    add().then().statusCode(502);
  }

  @Test
  @DisplayName("a title already on the shelf does not call the catalogue again")
  void cached() {
    lib.catalogue.stubFor(get(urlEqualTo(path())).willReturn(okJson("{\"title\":\"Once\"}")));
    add().then().statusCode(201);
    add().then().statusCode(201).body("copies", equalTo(2));
    assertEquals(1, calls());
  }

  @Test
  @DisplayName("an invalid ISBN never reaches the catalogue")
  void invalidNeverCalls() {
    isbn = Isbns.withWrongCheckDigit(isbn);
    add().then().statusCode(400);
    assertEquals(0, calls());
  }
}
