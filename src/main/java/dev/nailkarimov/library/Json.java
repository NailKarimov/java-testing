package dev.nailkarimov.library;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately small JSON reader and writer, so the service runs on the JDK alone.
 *
 * <p>Objects become {@code LinkedHashMap<String, Object>}, arrays {@code List<Object>}, numbers {@link BigDecimal},
 * plus {@code String}, {@code Boolean} and {@code null}. Duplicate keys, trailing data and nesting deeper than 32 are
 * rejected.
 */
public final class Json {
  private static final int MAX_DEPTH = 32;
  private final String s;
  private int i;

  private Json(String s) {
    this.s = s;
  }

  /** Thrown for any input that is not exactly one JSON value. */
  public static final class ParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    ParseException(String message, int at) {
      super(message + " at " + at);
    }
  }

  public static Object parse(String text) {
    Json p = new Json(text);
    p.ws();
    Object v = p.value(0);
    p.ws();
    if (p.i != text.length()) {
      throw new ParseException("trailing data", p.i);
    }
    return v;
  }

  private void ws() {
    while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) {
      i++;
    }
  }

  private char peek() {
    if (i >= s.length()) {
      throw new ParseException("unexpected end", i);
    }
    return s.charAt(i);
  }

  private void expect(String word) {
    if (!s.startsWith(word, i)) {
      throw new ParseException("expected " + word, i);
    }
    i += word.length();
  }

  private Object value(int depth) {
    if (depth > MAX_DEPTH) {
      throw new ParseException("nesting too deep", i);
    }
    char c = peek();
    switch (c) {
      case '{':
        return object(depth);
      case '[':
        return array(depth);
      case '"':
        return string();
      case 't':
        expect("true");
        return Boolean.TRUE;
      case 'f':
        expect("false");
        return Boolean.FALSE;
      case 'n':
        expect("null");
        return null;
      default:
        if (c == '-' || (c >= '0' && c <= '9')) {
          return number();
        }
        throw new ParseException("unexpected character '" + c + "'", i);
    }
  }

  private Map<String, Object> object(int depth) {
    Map<String, Object> m = new LinkedHashMap<>();
    i++;
    ws();
    if (peek() == '}') {
      i++;
      return m;
    }
    while (true) {
      ws();
      if (peek() != '"') {
        throw new ParseException("expected a key", i);
      }
      String k = string();
      ws();
      expect(":");
      ws();
      if (m.containsKey(k)) {
        throw new ParseException("duplicate key " + k, i);
      }
      m.put(k, value(depth + 1));
      ws();
      char c = peek();
      i++;
      if (c == '}') {
        return m;
      }
      if (c != ',') {
        throw new ParseException("expected , or }", i - 1);
      }
    }
  }

  private List<Object> array(int depth) {
    List<Object> l = new ArrayList<>();
    i++;
    ws();
    if (peek() == ']') {
      i++;
      return l;
    }
    while (true) {
      ws();
      l.add(value(depth + 1));
      ws();
      char c = peek();
      i++;
      if (c == ']') {
        return l;
      }
      if (c != ',') {
        throw new ParseException("expected , or ]", i - 1);
      }
    }
  }

  private String string() {
    StringBuilder b = new StringBuilder();
    i++;
    while (true) {
      char c = peek();
      i++;
      if (c == '"') {
        return b.toString();
      }
      if (c < 0x20) {
        throw new ParseException("control character in string", i - 1);
      }
      if (c != '\\') {
        b.append(c);
        continue;
      }
      char e = peek();
      i++;
      switch (e) {
        case '"', '\\', '/' -> b.append(e);
        case 'b' -> b.append('\b');
        case 'f' -> b.append('\f');
        case 'n' -> b.append('\n');
        case 'r' -> b.append('\r');
        case 't' -> b.append('\t');
        case 'u' -> {
          if (i + 4 > s.length()) {
            throw new ParseException("bad unicode escape", i);
          }
          try {
            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
          } catch (NumberFormatException ex) {
            throw new ParseException("bad unicode escape", i);
          }
          i += 4;
        }
        default -> throw new ParseException("bad escape", i - 1);
      }
    }
  }

  private BigDecimal number() {
    int start = i;
    if (peek() == '-') {
      i++;
    }
    while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) {
      i++;
    }
    String n = s.substring(start, i);
    if (!n.matches("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?")) {
      throw new ParseException("bad number " + n, start);
    }
    return new BigDecimal(n);
  }

  /** Serialises maps, iterables, strings, numbers, booleans and null. */
  public static String write(Object v) {
    StringBuilder b = new StringBuilder();
    write(v, b);
    return b.toString();
  }

  private static void write(Object v, StringBuilder b) {
    if (v == null) {
      b.append("null");
    } else if (v instanceof String str) {
      quote(str, b);
    } else if (v instanceof BigDecimal d) {
      b.append(d.toPlainString());
    } else if (v instanceof Number || v instanceof Boolean) {
      b.append(v);
    } else if (v instanceof Map<?, ?> m) {
      b.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> e : m.entrySet()) {
        if (!first) {
          b.append(',');
        }
        first = false;
        quote(String.valueOf(e.getKey()), b);
        b.append(':');
        write(e.getValue(), b);
      }
      b.append('}');
    } else if (v instanceof Iterable<?> it) {
      b.append('[');
      boolean first = true;
      for (Object o : it) {
        if (!first) {
          b.append(',');
        }
        first = false;
        write(o, b);
      }
      b.append(']');
    } else {
      quote(v.toString(), b);
    }
  }

  private static void quote(String str, StringBuilder b) {
    b.append('"');
    for (char c : str.toCharArray()) {
      switch (c) {
        case '"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        default -> {
          if (c < 0x20) {
            b.append(String.format("\\u%04x", (int) c));
          } else {
            b.append(c);
          }
        }
      }
    }
    b.append('"');
  }
}
