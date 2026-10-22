package dev.nailkarimov.library.support;

import static io.restassured.RestAssured.given;

import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;

/** Small steps shared by the API tests and the Cucumber steps. */
public final class Api {
  private final TestLibrary lib;

  public Api(TestLibrary lib) {
    this.lib = lib;
  }

  public Response post(String path, Object body) {
    return given().spec(lib.spec()).body(body).when().post(path);
  }

  public Response get(String path) {
    return given().spec(lib.spec()).when().get(path);
  }

  public long newMember() {
    String email = "m-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    return post("/members", Map.of("name", "Test Member", "email", email))
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("id");
  }

  /** A title the catalogue knows, with the given number of copies on the shelf. */
  public String newBook(int copies) {
    String isbn = Isbns.next();
    lib.knowsBook(isbn, "Title " + isbn);
    post("/books", Map.of("isbn", isbn, "copies", copies)).then().statusCode(201);
    return isbn;
  }

  public Response borrow(long memberId, String isbn) {
    return post("/loans", Map.of("memberId", memberId, "isbn", isbn));
  }

  public long borrowed(long memberId, String isbn) {
    return borrow(memberId, isbn).then().statusCode(201).extract().jsonPath().getLong("id");
  }

  public Response giveBack(long loanId) {
    return given().spec(lib.spec()).when().post("/loans/" + loanId + "/return");
  }
}
