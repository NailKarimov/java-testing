package dev.nailkarimov.library.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.nailkarimov.library.CatalogueClient;
import dev.nailkarimov.library.Library;
import dev.nailkarimov.library.LibraryServer;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDate;

/**
 * One running library for a test class: the real HTTP server on a free port, a WireMock stand-in for the external
 * catalogue, and a clock the test can move.
 */
public final class TestLibrary implements AutoCloseable {
  public static final LocalDate START = LocalDate.of(2026, 10, 1);
  public static final Duration CATALOGUE_TIMEOUT = Duration.ofMillis(300);

  public final WireMockServer catalogue;
  public final MutableClock clock = new MutableClock(START);
  public final LibraryServer server;

  private TestLibrary() {
    catalogue = new WireMockServer(options().dynamicPort().bindAddress("127.0.0.1"));
    catalogue.start();
    try {
      server =
          new LibraryServer(
                  new Library(clock, new CatalogueClient(catalogue.baseUrl(), CATALOGUE_TIMEOUT)), 0)
              .start();
    } catch (IOException e) {
      catalogue.stop();
      throw new UncheckedIOException(e);
    }
  }

  public static TestLibrary start() {
    return new TestLibrary();
  }

  /** Teaches the catalogue one book. */
  public void knowsBook(String isbn, String title) {
    catalogue.stubFor(
        get(urlEqualTo("/isbn/" + isbn))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"title\":\"" + title + "\",\"authors\":[\"Test Author\"]}")));
  }

  /** REST Assured defaults: base URI, JSON, and every request and response attached to the Allure report. */
  public RequestSpecification spec() {
    return new RequestSpecBuilder()
        .setBaseUri(server.baseUrl())
        .setContentType(ContentType.JSON)
        .addFilter(new AllureRestAssured())
        .build();
  }

  @Override
  public void close() {
    server.close();
    catalogue.stop();
  }
}
