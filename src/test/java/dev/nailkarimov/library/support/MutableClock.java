package dev.nailkarimov.library.support;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** "Today" for the library, moved by the tests instead of waiting 14 days. */
public final class MutableClock implements Supplier<LocalDate> {
  private final AtomicReference<LocalDate> today;

  public MutableClock(LocalDate start) {
    this.today = new AtomicReference<>(start);
  }

  @Override
  public LocalDate get() {
    return today.get();
  }

  public void set(LocalDate date) {
    today.set(date);
  }

  public void plusDays(long days) {
    today.updateAndGet(d -> d.plusDays(days));
  }
}
