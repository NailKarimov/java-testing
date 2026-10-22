package dev.nailkarimov.library.contract;

import dev.nailkarimov.library.Json;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * The consumer: how the library's web front end calls the API. It reads only the fields it shows, which is exactly
 * what its Pact contract promises to need, no more.
 */
public final class LibraryWebClient {
  public record Shelf(String isbn, String title, int available) {}

  public record Loan(long id, String due) {}

  /** A refusal the front end shows to the member, keyed by the API's error code. */
  public static final class Refused extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public final String code;

    Refused(String code) {
      super(code);
      this.code = code;
    }
  }

  private final HttpClient http = HttpClient.newHttpClient();
  private final String base;

  public LibraryWebClient(String base) {
    this.base = base;
  }

  public Shelf shelf(String isbn) throws IOException, InterruptedException {
    HttpResponse<String> res =
        http.send(HttpRequest.newBuilder(URI.create(base + "/books/" + isbn)).GET().build(), HttpResponse.BodyHandlers.ofString());
    Map<?, ?> body = (Map<?, ?>) Json.parse(res.body());
    if (res.statusCode() != 200) {
      throw new Refused((String) body.get("code"));
    }
    return new Shelf((String) body.get("isbn"), (String) body.get("title"), ((BigDecimal) body.get("available")).intValueExact());
  }

  public Loan borrow(long memberId, String isbn) throws IOException, InterruptedException {
    String json = Json.write(Map.of("memberId", memberId, "isbn", isbn));
    HttpResponse<String> res =
        http.send(
            HttpRequest.newBuilder(URI.create(base + "/loans"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    Map<?, ?> body = (Map<?, ?>) Json.parse(res.body());
    if (res.statusCode() != 201) {
      throw new Refused((String) body.get("code"));
    }
    return new Loan(((BigDecimal) body.get("id")).longValueExact(), (String) body.get("due"));
  }
}
