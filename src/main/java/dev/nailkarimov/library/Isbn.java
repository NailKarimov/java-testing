package dev.nailkarimov.library;

/** ISBN-13: 978 or 979, 13 digits, hyphens and spaces allowed in input, valid check digit. */
public final class Isbn {
  private Isbn() {}

  /** Returns the 13 digits, or throws a 400 that says what is wrong. */
  public static String normalize(String raw) {
    if (raw == null) {
      throw invalid("isbn is required");
    }
    String d = raw.replace("-", "").replace(" ", "");
    if (!d.matches("\\d{13}")) {
      throw invalid("isbn must have 13 digits");
    }
    if (!d.startsWith("978") && !d.startsWith("979")) {
      throw invalid("isbn must start with 978 or 979");
    }
    if (checkDigit(d.substring(0, 12)) != d.charAt(12) - '0') {
      throw invalid("isbn check digit is wrong");
    }
    return d;
  }

  /** Weights 1,3,1,3… over the first 12 digits. */
  public static int checkDigit(String first12) {
    int sum = 0;
    for (int k = 0; k < 12; k++) {
      sum += (first12.charAt(k) - '0') * (k % 2 == 0 ? 1 : 3);
    }
    return (10 - sum % 10) % 10;
  }

  private static ApiException invalid(String message) {
    return new ApiException(400, "INVALID_ISBN", message);
  }
}
