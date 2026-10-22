package dev.nailkarimov.library;

/** A business or validation error that maps to an HTTP status and a stable machine-readable code. */
public final class ApiException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final int status;
  private final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }
}
