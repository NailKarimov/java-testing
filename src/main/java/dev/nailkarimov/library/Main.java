package dev.nailkarimov.library;

import java.time.Duration;
import java.time.LocalDate;

/** Runs the library on PORT (default 8080) against the catalogue at CATALOGUE_URL. */
public final class Main {
  private Main() {}

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    String catalogue = System.getenv().getOrDefault("CATALOGUE_URL", "http://127.0.0.1:8089");
    Library library = new Library(LocalDate::now, new CatalogueClient(catalogue, Duration.ofMillis(500)));
    LibraryServer server = new LibraryServer(library, port).start();
    System.out.println("library on " + server.baseUrl() + ", catalogue " + catalogue);
  }
}
