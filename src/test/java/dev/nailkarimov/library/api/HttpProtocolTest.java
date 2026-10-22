package dev.nailkarimov.library.api;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

import dev.nailkarimov.library.support.TestLibrary;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("HTTP rules for every endpoint")
class HttpProtocolTest {
  static TestLibrary lib;

  @BeforeAll
  static void start() {
    lib = TestLibrary.start();
  }

  @AfterAll
  static void stop() {
    lib.close();
  }

  @ParameterizedTest(name = "body {0} → 400 INVALID_JSON")
  @ValueSource(strings = {"{", "{\"name\":}", "[]", "\"text\"", "{\"a\":1,\"a\":2}", "{} {}", "{\"name\":\"\\x\"}"})
  void invalidJson(String body) {
    given(lib.spec())
        .body(body)
        .post("/members")
        .then()
        .statusCode(400)
        .body("code", equalTo("INVALID_JSON"))
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));
  }

  @Test
  @DisplayName("JSON nested 40 levels deep is refused, not a stack overflow")
  void deepNesting() {
    String body = "{\"x\":" + "[".repeat(40) + "]".repeat(40) + "}";
    given(lib.spec()).body(body).post("/members").then().statusCode(400).body("code", equalTo("INVALID_JSON"));
  }

  @Test
  @DisplayName("text/plain → 415; the media type is matched case-insensitively")
  void mediaType() {
    given().baseUri(lib.server.baseUrl()).contentType(ContentType.TEXT).body("{}").post("/members").then().statusCode(415);
    given()
        .baseUri(lib.server.baseUrl())
        .contentType("Application/JSON")
        .body("{\"name\":\"Case\",\"email\":\"case-" + System.nanoTime() + "@example.com\"}")
        .post("/members")
        .then()
        .statusCode(201);
  }

  @Test
  @DisplayName("a body over 16 KB → 413")
  void bodyLimit() {
    String body = "{\"name\":\"" + "a".repeat(17 * 1024) + "\"}";
    given(lib.spec()).body(body).post("/members").then().statusCode(413).body("code", equalTo("BODY_TOO_LARGE"));
  }

  @Test
  @DisplayName("wrong method → 405, unknown path → 404, both as JSON errors")
  void routing() {
    given(lib.spec()).delete("/members/1").then().statusCode(405).body("code", equalTo("METHOD_NOT_ALLOWED"));
    given(lib.spec()).get("/members").then().statusCode(405);
    given(lib.spec()).get("/admin").then().statusCode(404).body(matchesJsonSchemaInClasspath("schemas/error.json"));
  }

  @Test
  @DisplayName("every answer is JSON with nosniff and no-store, and no server internals")
  void headers() {
    given(lib.spec())
        .get("/health")
        .then()
        .statusCode(200)
        .header("Content-Type", startsWith("application/json"))
        .header("X-Content-Type-Options", "nosniff")
        .header("Cache-Control", "no-store");
    given(lib.spec())
        .body("{")
        .post("/members")
        .then()
        .body(not(containsString("Exception")))
        .body(not(containsString("dev.nailkarimov")));
  }
}
