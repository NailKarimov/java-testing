package dev.nailkarimov.library.support;

import dev.nailkarimov.library.Isbn;
import java.util.concurrent.atomic.AtomicInteger;

/** Valid, unique ISBN-13 numbers for test data, so tests never share a title by accident. */
public final class Isbns {
  private static final AtomicInteger NEXT = new AtomicInteger((int) (System.nanoTime() % 400_000));

  private Isbns() {}

  public static String next() {
    String first12 = "979" + String.format("%09d", NEXT.incrementAndGet());
    return first12 + Isbn.checkDigit(first12);
  }

  /** The same 12 digits with a wrong check digit. */
  public static String withWrongCheckDigit(String isbn) {
    int right = isbn.charAt(12) - '0';
    return isbn.substring(0, 12) + ((right + 1) % 10);
  }
}
