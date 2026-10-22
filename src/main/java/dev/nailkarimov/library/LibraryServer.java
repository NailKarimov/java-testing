package dev.nailkarimov.library;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTTP front of the {@link Library}, on the JDK's built-in server. Port 0 picks a free port (used by the tests). */
public final class LibraryServer implements AutoCloseable {
  private static final int MAX_BODY = 16 * 1024;
  private static final Pattern MEMBER = Pattern.compile("/members/(\\d{1,18})");
  private static final Pattern PAYMENT = Pattern.compile("/members/(\\d{1,18})/payments");
  private static final Pattern BOOK = Pattern.compile("/books/([0-9 -]{1,20})");
  private static final Pattern LOAN = Pattern.compile("/loans/(\\d{1,18})");
  private static final Pattern RETURN = Pattern.compile("/loans/(\\d{1,18})/return");

  private final Library library;
  private final HttpServer server;
  private final ExecutorService pool = Executors.newFixedThreadPool(8);

  public LibraryServer(Library library, int port) throws IOException {
    this.library = library;
    this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    server.createContext("/", this::handle);
    server.setExecutor(pool);
  }

  public LibraryServer start() {
    server.start();
    return this;
  }

  public int port() {
    return server.getAddress().getPort();
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + port();
  }

  @Override
  public void close() {
    server.stop(0);
    pool.shutdownNow();
  }

  private void handle(HttpExchange ex) throws IOException {
    try {
      Object body = route(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex);
      int status = ex.getRequestMethod().equals("POST") && !(body instanceof Ok) ? 201 : 200;
      send(ex, status, body instanceof Ok ok ? ok.body : body);
    } catch (ApiException e) {
      send(ex, e.status(), error(e.code(), e.getMessage()));
    } catch (RuntimeException e) {
      // Never leak a stack trace or class name to the client.
      send(ex, 500, error("INTERNAL", "internal error"));
    } finally {
      ex.close();
    }
  }

  /** Marks a POST answer that is 200, not 201 (an action, not a new resource). */
  private record Ok(Object body) {}

  private Object route(String method, String path, HttpExchange ex) throws IOException {
    Matcher m;
    if (path.equals("/health")) {
      allow(method, "GET");
      return Map.of("status", "ok");
    }
    if (path.equals("/members")) {
      allow(method, "POST");
      return library.addMember(object(ex));
    }
    if ((m = PAYMENT.matcher(path)).matches()) {
      allow(method, "POST");
      return new Ok(library.pay(Long.parseLong(m.group(1)), object(ex)));
    }
    if ((m = MEMBER.matcher(path)).matches()) {
      allow(method, "GET");
      return library.member(Long.parseLong(m.group(1)));
    }
    if (path.equals("/books")) {
      allow(method, "POST");
      return library.addBook(object(ex));
    }
    if ((m = BOOK.matcher(path)).matches()) {
      allow(method, "GET");
      return library.book(m.group(1));
    }
    if (path.equals("/loans")) {
      allow(method, "POST");
      return library.borrow(object(ex));
    }
    if ((m = RETURN.matcher(path)).matches()) {
      allow(method, "POST");
      return new Ok(library.giveBack(Long.parseLong(m.group(1))));
    }
    if ((m = LOAN.matcher(path)).matches()) {
      allow(method, "GET");
      return library.loan(Long.parseLong(m.group(1)));
    }
    throw new ApiException(404, "NOT_FOUND", "no route " + path);
  }

  private static void allow(String method, String expected) {
    if (!method.equals(expected)) {
      throw new ApiException(405, "METHOD_NOT_ALLOWED", "use " + expected);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(HttpExchange ex) throws IOException {
    String type = ex.getRequestHeaders().getFirst("Content-Type");
    if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")) {
      throw new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", "send application/json");
    }
    byte[] raw;
    try (InputStream in = ex.getRequestBody()) {
      raw = in.readNBytes(MAX_BODY + 1);
    }
    if (raw.length > MAX_BODY) {
      throw new ApiException(413, "BODY_TOO_LARGE", "body over " + MAX_BODY + " bytes");
    }
    Object v;
    try {
      v = Json.parse(new String(raw, StandardCharsets.UTF_8));
    } catch (Json.ParseException e) {
      throw new ApiException(400, "INVALID_JSON", "body is not valid JSON");
    }
    if (!(v instanceof Map<?, ?>)) {
      throw new ApiException(400, "INVALID_JSON", "body must be a JSON object");
    }
    return (Map<String, Object>) v;
  }

  private static Map<String, Object> error(String code, String message) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("code", code);
    e.put("message", message);
    return e;
  }

  private static void send(HttpExchange ex, int status, Object body) throws IOException {
    byte[] out = Json.write(body).getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    ex.getResponseHeaders().set("Cache-Control", "no-store");
    ex.sendResponseHeaders(status, out.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(out);
    }
  }
}
