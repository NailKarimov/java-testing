package dev.nailkarimov.library.api;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

import dev.nailkarimov.library.support.Api;
import dev.nailkarimov.library.support.TestLibrary;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Members")
class MembersApiTest {
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

  static String email() {
    return "u-" + UUID.randomUUID().toString().substring(0, 8) + "@Example.COM";
  }

  @Test
  @DisplayName("a new member gets an id, no loans, no fines; the e-mail is stored in lower case")
  void create() {
    String email = email();
    api.post("/members", Map.of("name", "  Ann Lee ", "email", email))
        .then()
        .statusCode(201)
        .body(matchesJsonSchemaInClasspath("schemas/member.json"))
        .body("id", greaterThan(0))
        .body("name", equalTo("Ann Lee"))
        .body("email", equalTo(email.toLowerCase()))
        .body("loans", empty())
        .body("fines", equalTo(0.00f));
  }

  @Test
  @DisplayName("the same e-mail in another case is a duplicate: 409")
  void duplicateEmail() {
    String email = email();
    api.post("/members", Map.of("name", "A", "email", email)).then().statusCode(201);
    api.post("/members", Map.of("name", "B", "email", email.toUpperCase()))
        .then()
        .statusCode(409)
        .body("code", equalTo("EMAIL_TAKEN"));
  }

  @ParameterizedTest(name = "{0} → 400")
  @CsvSource(
      delimiter = '|',
      value = {
        "name missing            | ~       | ok",
        "name blank              | '   '   | ok",
        "name of 81 characters   | LONG81  | ok",
        "e-mail without @        | Ann     | ann.example.com",
        "e-mail without a domain | Ann     | ann@",
        "e-mail with a space     | Ann     | 'ann lee@example.com'",
        "e-mail non-ASCII        | Ann     | анна@example.com",
      })
  void invalid(String caseName, String name, String emailOrOk) {
    Map<String, Object> body = new HashMap<>();
    if (!"~".equals(name)) {
      body.put("name", "LONG81".equals(name) ? "x".repeat(81) : name);
    }
    body.put("email", "ok".equals(emailOrOk) ? email() : emailOrOk);
    api.post("/members", body).then().statusCode(400).body("code", equalTo("VALIDATION"));
  }

  @Test
  @DisplayName("a name of exactly 80 characters is accepted (upper boundary)")
  void nameBoundary() {
    api.post("/members", Map.of("name", "x".repeat(80), "email", email())).then().statusCode(201);
  }

  @Test
  @DisplayName("an unknown member is 404, an id that is not a number is no route at all")
  void unknown() {
    api.get("/members/999999").then().statusCode(404).body("code", equalTo("MEMBER_NOT_FOUND"));
    api.get("/members/abc").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
  }
}
