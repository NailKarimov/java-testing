package dev.nailkarimov.library.bdd;

import io.cucumber.java.ParameterType;
import java.time.LocalDate;

/** Lets steps say {isoDate} and receive a LocalDate. */
public class ParameterTypes {
  @ParameterType("\\d{4}-\\d{2}-\\d{2}")
  public LocalDate isoDate(String text) {
    return LocalDate.parse(text);
  }
}
