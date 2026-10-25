package br.com.velion.scoresaf.application.usecase.rf05;

import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conferência dos valores de entrada do editor (RF5): cada recusa é um 400 com código {@code error.*} traduzido nas 3 línguas. */
final class CatalogInput {
  private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
  private static final Pattern SHORT_NAME = Pattern.compile("^[A-Z0-9]{2,8}$");

  private CatalogInput() {}

  /** Nome obrigatório (sem espaços nas pontas, até {@code max} caracteres). */
  static String name(String raw, int max) {
    if (raw == null || raw.isBlank() || raw.trim().length() > max) throw new InvalidInputException("INVALID_NAME", "Nome inválido");
    return raw.trim();
  }

  /** Sigla em maiúsculas, letras e dígitos, de 2 a {@code max} caracteres. */
  static String shortName(String raw, int max) {
    String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    if (!SHORT_NAME.matcher(s).matches() || s.length() > max) throw new InvalidInputException("INVALID_SHORT_NAME", "Sigla inválida: " + raw, raw);
    return s;
  }

  /** Cor {@code #RRGGBB}, devolvida em maiúsculas. */
  static String color(String raw) {
    if (raw == null || !COLOR.matcher(raw.trim()).matches()) throw new InvalidInputException("INVALID_COLOR", "Cor inválida: " + raw, raw);
    return raw.trim().toUpperCase(Locale.ROOT);
  }

  /** Enum pelo nome (sem diferenciar maiúsculas); desconhecido = 400 com o código dado. */
  static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String code) {
    if (raw != null) {
      String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
      for (E e : type.getEnumConstants()) if (e.name().equals(key)) return e;
    }
    throw new InvalidInputException(code, "Valor inválido: " + raw, raw);
  }

  /** Inteiro na faixa [min, max]; fora dela = 400 INVALID_VALUE com o nome do campo. */
  static int range(String field, int value, int min, int max) {
    if (value < min || value > max) throw new InvalidInputException("INVALID_VALUE", "Valor inválido: " + field, field);
    return value;
  }

  static long nonNegative(String field, long value) {
    if (value < 0) throw new InvalidInputException("INVALID_VALUE", "Valor inválido: " + field, field);
    return value;
  }
}
