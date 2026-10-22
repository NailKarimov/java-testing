package dev.nailkarimov.library.api;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import dev.nailkarimov.library.support.Api;
import dev.nailkarimov.library.support.Isbns;
import dev.nailkarimov.library.support.TestLibrary;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Books")
class BooksApiTest {
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

  @Test
  @DisplayName("a new ISBN takes its title and authors from the catalogue")
  void addFromCatalogue() {
    String isbn = Isbns.next();
    lib.knowsBook(isbn, "War and Peace");
    api.post("/books", Map.of("isbn", isbn, "copies", 2))
        .then()
        .statusCode(201)
        .body(matchesJsonSchemaInClasspath("schemas/book.json"))
        .body("title", equalTo("War and Peace"))
        .body("authors", contains("Test Author"))
        .body("copies", equalTo(2))
        .body("available", equalTo(2));
  }

  @Test
  @DisplayName("hyphens and spaces in the ISBN are accepted and normalised")
  void hyphens() {
    String isbn = Isbns.next();
    lib.knowsBook(isbn, "Hyphenated");
    String pretty = isbn.substring(0, 3) + "-" + isbn.substring(3, 6) + " " + isbn.substring(6);
    api.post("/books", Map.of("isbn", pretty, "copies", 1)).then().statusCode(201).body("isbn", equalTo(isbn));
    api.get("/books/" + pretty.replace(" ", "-")).then().statusCode(200).body("isbn", equalTo(isbn));
  }

  @Test
  @DisplayName("adding an ISBN that is already on the shelf adds copies")
  void addCopies() {
    String isbn = api.newBook(2);
    api.post("/books", Map.of("isbn", isbn, "copies", 3)).then().body("copies", equalTo(5)).body("available", equalTo(5));
  }

  @ParameterizedTest(name = "copies = {0} → 400")
  @ValueSource(strings = {"0", "-1", "21", "1.5", "\"2\"", "null"})
  void copiesOutOfRange(String copies) {
    String isbn = Isbns.next();
    lib.knowsBook(isbn, "X");
    api.post("/books", "{\"isbn\":\"" + isbn + "\",\"copies\":" + copies + "}")
        .then()
        .statusCode(400)
        .body("code", equalTo("VALIDATION"));
  }

  @ParameterizedTest(name = "copies = {0} is accepted (boundary)")
  @ValueSource(ints = {1, 20})
  void copiesBoundaries(int copies) {
    String isbn = Isbns.next();
    lib.knowsBook(isbn, "X");
    api.post("/books", Map.of("isbn", isbn, "copies", copies)).then().statusCode(201);
  }

  @ParameterizedTest(name = "{0} → 400")
  @CsvSource(
      delimiter = '|',
      value = {
        "12 digits           | 979000000001",
        "14 digits           | 97900000000123",
        "letters             | 97900000000X1",
        "prefix 977          | 9770000000017",
      })
  void invalidIsbn(String caseName, String isbn) {
    api.post("/books", Map.of("isbn", isbn, "copies", 1))
        .then()
        .statusCode(400)
        .body("code", equalTo("INVALID_ISBN"));
  }

  @Test
  @DisplayName("a wrong check digit is rejected before the catalogue is asked")
  void wrongCheckDigit() {
    String isbn = Isbns.withWrongCheckDigit(Isbns.next());
    api.post("/books", Map.of("isbn", isbn, "copies", 1))
        .then()
        .statusCode(400)
        .body("message", equalTo("isbn check digit is wrong"));
  }

  @Test
  @DisplayName("an unknown but valid ISBN is 404")
  void unknown() {
    api.get("/books/" + Isbns.next()).then().statusCode(404).body("code", equalTo("BOOK_NOT_FOUND"));
  }
}
