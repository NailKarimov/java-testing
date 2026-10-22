package dev.nailkarimov.library;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The lending rules. All state is in memory and every public method is synchronized: one library, one lock.
 *
 * <ul>
 *   <li>A loan lasts {@value #LOAN_DAYS} days; a member holds at most {@value #LOAN_LIMIT} books at once and never two
 *       copies of the same title.
 *   <li>Late returns cost 0.25 per day, at most 10.00 per loan.
 *   <li>A member who owes more than 5.00 cannot borrow until they pay.
 * </ul>
 */
public final class Library {
  public static final int LOAN_DAYS = 14;
  public static final int LOAN_LIMIT = 3;
  public static final BigDecimal FINE_PER_DAY = new BigDecimal("0.25");
  public static final BigDecimal FINE_CAP = new BigDecimal("10.00");
  public static final BigDecimal BLOCK_ABOVE = new BigDecimal("5.00");
  private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+");

  private record Member(long id, String name, String email) {}

  private static final class Book {
    final String isbn;
    final String title;
    final List<String> authors;
    int copies;

    Book(String isbn, String title, List<String> authors, int copies) {
      this.isbn = isbn;
      this.title = title;
      this.authors = authors;
      this.copies = copies;
    }
  }

  private static final class Loan {
    final long id;
    final long memberId;
    final String isbn;
    final LocalDate borrowed;
    final LocalDate due;
    LocalDate returned;
    BigDecimal fine = BigDecimal.ZERO.setScale(2);

    Loan(long id, long memberId, String isbn, LocalDate borrowed) {
      this.id = id;
      this.memberId = memberId;
      this.isbn = isbn;
      this.borrowed = borrowed;
      this.due = borrowed.plusDays(LOAN_DAYS);
    }
  }

  private final Supplier<LocalDate> today;
  private final CatalogueClient catalogue;
  private final Map<Long, Member> members = new LinkedHashMap<>();
  private final Map<Long, BigDecimal> owed = new LinkedHashMap<>();
  private final Map<String, Book> books = new LinkedHashMap<>();
  private final Map<Long, Loan> loans = new LinkedHashMap<>();
  private long nextMember = 1;
  private long nextLoan = 1;

  public Library(Supplier<LocalDate> today, CatalogueClient catalogue) {
    this.today = today;
    this.catalogue = catalogue;
  }

  // ---- members

  public synchronized Map<String, Object> addMember(Map<String, Object> in) {
    String name = text(in, "name", 1, 80);
    String email = text(in, "email", 3, 254).toLowerCase(Locale.ROOT);
    if (!EMAIL.matcher(email).matches()) {
      throw new ApiException(400, "VALIDATION", "email is not valid");
    }
    for (Member m : members.values()) {
      if (m.email.equals(email)) {
        throw new ApiException(409, "EMAIL_TAKEN", "a member with this email exists");
      }
    }
    Member m = new Member(nextMember++, name.strip(), email);
    members.put(m.id, m);
    owed.put(m.id, BigDecimal.ZERO.setScale(2));
    return member(m.id);
  }

  public synchronized Map<String, Object> member(long id) {
    Member m = members.get(id);
    if (m == null) {
      throw new ApiException(404, "MEMBER_NOT_FOUND", "no member " + id);
    }
    List<Object> active = new ArrayList<>();
    for (Loan l : loans.values()) {
      if (l.memberId == id && l.returned == null) {
        active.add(loanView(l));
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", m.id);
    out.put("name", m.name);
    out.put("email", m.email);
    out.put("loans", active);
    out.put("fines", owed.get(id));
    return out;
  }

  public synchronized Map<String, Object> pay(long memberId, Map<String, Object> in) {
    member(memberId);
    BigDecimal amount = money(in.get("amount"));
    BigDecimal due = owed.get(memberId);
    if (amount.compareTo(due) > 0) {
      throw new ApiException(400, "VALIDATION", "amount is more than the " + due + " owed");
    }
    owed.put(memberId, due.subtract(amount));
    return member(memberId);
  }

  // ---- books

  /** Adds copies of a title. A new ISBN is looked up in the catalogue first (outside the lock would be nicer). */
  public synchronized Map<String, Object> addBook(Map<String, Object> in) {
    Object rawIsbn = in.get("isbn");
    String isbn = Isbn.normalize(rawIsbn instanceof String s ? s : null);
    int copies = integer(in, "copies", 1, 20);
    Book b = books.get(isbn);
    if (b != null) {
      if (b.copies + copies > 50) {
        throw new ApiException(400, "VALIDATION", "a title has at most 50 copies");
      }
      b.copies += copies;
    } else {
      CatalogueClient.Entry e = catalogue.lookup(isbn);
      b = new Book(isbn, e.title(), e.authors(), copies);
      books.put(isbn, b);
    }
    return book(isbn);
  }

  public synchronized Map<String, Object> book(String rawIsbn) {
    String isbn = Isbn.normalize(rawIsbn);
    Book b = books.get(isbn);
    if (b == null) {
      throw new ApiException(404, "BOOK_NOT_FOUND", "no book " + isbn);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("isbn", b.isbn);
    out.put("title", b.title);
    out.put("authors", b.authors);
    out.put("copies", b.copies);
    out.put("available", b.copies - onLoan(isbn));
    return out;
  }

  private int onLoan(String isbn) {
    int n = 0;
    for (Loan l : loans.values()) {
      if (l.isbn.equals(isbn) && l.returned == null) {
        n++;
      }
    }
    return n;
  }

  // ---- loans

  public synchronized Map<String, Object> borrow(Map<String, Object> in) {
    long memberId = integer(in, "memberId", 1, Integer.MAX_VALUE);
    Object rawIsbn = in.get("isbn");
    String isbn = Isbn.normalize(rawIsbn instanceof String s ? s : null);
    member(memberId);
    Book b = books.get(isbn);
    if (b == null) {
      throw new ApiException(404, "BOOK_NOT_FOUND", "no book " + isbn);
    }
    if (owed.get(memberId).compareTo(BLOCK_ABOVE) > 0) {
      throw new ApiException(409, "FINES_UNPAID", "member owes " + owed.get(memberId));
    }
    int active = 0;
    for (Loan l : loans.values()) {
      if (l.memberId == memberId && l.returned == null) {
        active++;
        if (l.isbn.equals(isbn)) {
          throw new ApiException(409, "ALREADY_BORROWED", "member already has this title");
        }
      }
    }
    if (active >= LOAN_LIMIT) {
      throw new ApiException(409, "LOAN_LIMIT", "member already has " + LOAN_LIMIT + " books");
    }
    if (b.copies - onLoan(isbn) <= 0) {
      throw new ApiException(409, "NO_COPIES", "no copies available");
    }
    Loan l = new Loan(nextLoan++, memberId, isbn, today.get());
    loans.put(l.id, l);
    return loanView(l);
  }

  public synchronized Map<String, Object> giveBack(long loanId) {
    Loan l = loans.get(loanId);
    if (l == null) {
      throw new ApiException(404, "LOAN_NOT_FOUND", "no loan " + loanId);
    }
    if (l.returned != null) {
      throw new ApiException(409, "ALREADY_RETURNED", "loan was returned on " + l.returned);
    }
    l.returned = today.get();
    l.fine = fineFor(l.due, l.returned);
    owed.merge(l.memberId, l.fine, BigDecimal::add);
    return loanView(l);
  }

  public synchronized Map<String, Object> loan(long loanId) {
    Loan l = loans.get(loanId);
    if (l == null) {
      throw new ApiException(404, "LOAN_NOT_FOUND", "no loan " + loanId);
    }
    return loanView(l);
  }

  /** 0.25 per day after the due date, capped at 10.00. */
  public static BigDecimal fineFor(LocalDate due, LocalDate returned) {
    long late = Math.max(0, ChronoUnit.DAYS.between(due, returned));
    BigDecimal fine = FINE_PER_DAY.multiply(BigDecimal.valueOf(late));
    return fine.min(FINE_CAP).setScale(2, RoundingMode.UNNECESSARY);
  }

  private Map<String, Object> loanView(Loan l) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", l.id);
    out.put("memberId", l.memberId);
    out.put("isbn", l.isbn);
    out.put("borrowed", l.borrowed.toString());
    out.put("due", l.due.toString());
    out.put("returned", l.returned == null ? null : l.returned.toString());
    out.put("fine", l.fine);
    return out;
  }

  // ---- input helpers

  private static String text(Map<String, Object> in, String field, int min, int max) {
    if (!(in.get(field) instanceof String s) || s.isBlank() || s.length() < min || s.length() > max) {
      throw new ApiException(400, "VALIDATION", field + " must be a string of " + min + "-" + max + " characters");
    }
    return s;
  }

  private static int integer(Map<String, Object> in, String field, int min, int max) {
    if (in.get(field) instanceof BigDecimal d) {
      try {
        int v = d.intValueExact();
        if (v >= min && v <= max) {
          return v;
        }
      } catch (ArithmeticException e) {
        // fall through to the error below
      }
    }
    throw new ApiException(400, "VALIDATION", field + " must be a whole number " + min + "-" + max);
  }

  private static BigDecimal money(Object v) {
    if (v instanceof BigDecimal d && d.signum() > 0 && d.scale() <= 2 && d.compareTo(new BigDecimal("1000")) <= 0) {
      return d.setScale(2);
    }
    throw new ApiException(400, "VALIDATION", "amount must be 0.01-1000.00 with at most two decimals");
  }
}
