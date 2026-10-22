package dev.nailkarimov.library;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Looks up a title and authors by ISBN in an external catalogue service: {@code GET {base}/isbn/{isbn}}.
 *
 * <p>One attempt plus one retry for a timeout, a dropped connection or a 5xx. A 404 means "no such book". An answer
 * without a title is the catalogue's fault, not the user's, and is reported as a bad gateway.
 */
public class CatalogueClient {
  /** What the catalogue knows about a book. */
  public record Entry(String title, List<String> authors) {}

  private final HttpClient http;
  private final String base;
  private final Duration timeout;

  public CatalogueClient(String base, Duration timeout) {
    this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    this.timeout = timeout;
    this.http = HttpClient.newBuilder().connectTimeout(timeout).version(HttpClient.Version.HTTP_1_1).build();
  }

  public Entry lookup(String isbn) {
    ApiException last = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        return once(isbn);
      } catch (Retryable e) {
        last = new ApiException(503, "CATALOGUE_UNAVAILABLE", "catalogue unavailable: " + e.getMessage());
      }
    }
    throw last;
  }

  private static final class Retryable extends Exception {
    private static final long serialVersionUID = 1L;

    Retryable(String message) {
      super(message);
    }
  }

  private Entry once(String isbn) throws Retryable {
    HttpRequest req =
        HttpRequest.newBuilder(URI.create(base + "/isbn/" + isbn))
            .timeout(timeout)
            .header("Accept", "application/json")
            .GET()
            .build();
    HttpResponse<String> res;
    try {
      res = http.send(req, HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException e) {
      throw new Retryable("timeout after " + timeout.toMillis() + " ms");
    } catch (IOException e) {
      throw new Retryable(e.getClass().getSimpleName());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "CATALOGUE_UNAVAILABLE", "interrupted");
    }
    if (res.statusCode() == 404) {
      throw new ApiException(422, "UNKNOWN_ISBN", "the catalogue does not know isbn " + isbn);
    }
    if (res.statusCode() >= 500) {
      throw new Retryable("status " + res.statusCode());
    }
    if (res.statusCode() != 200) {
      throw badGateway("status " + res.statusCode());
    }
    Object body;
    try {
      body = Json.parse(res.body());
    } catch (Json.ParseException e) {
      throw badGateway("invalid JSON");
    }
    if (!(body instanceof Map<?, ?> m) || !(m.get("title") instanceof String title) || title.isBlank()) {
      throw badGateway("no title");
    }
    List<String> authors = new ArrayList<>();
    if (m.get("authors") instanceof List<?> l) {
      for (Object a : l) {
        if (a instanceof String s) {
          authors.add(s);
        }
      }
    }
    return new Entry(title, List.copyOf(authors));
  }

  private static ApiException badGateway(String why) {
    return new ApiException(502, "CATALOGUE_BAD_RESPONSE", "catalogue sent a bad answer: " + why);
  }
}
