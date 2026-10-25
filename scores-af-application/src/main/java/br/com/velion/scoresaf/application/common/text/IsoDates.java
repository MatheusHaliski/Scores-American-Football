package br.com.velion.scoresaf.application.common.text;

import java.time.LocalDate;

/** Data ISO-8601 para os DTOs; sem data, {@code null} (nunca a string "null", que o front leria como data). */
public final class IsoDates {
  private IsoDates() {}

  public static String of(LocalDate date) { return date == null ? null : date.toString(); }
}
