package br.com.velion.scoresaf.domain.account;

import java.util.Locale;

/**
 * Idioma da conta (RF46 — tradução completa do jogo). Tags BCP 47 suportadas: pt-BR (padrão), en-US, es-ES.
 * {@link #fromTag(String)} aceita variações ("pt", "pt_br", "EN", "es-419") e cai no padrão quando desconhecido.
 */
public enum UserLocale {
  PT_BR("pt-BR"), EN_US("en-US"), ES_ES("es-ES");

  private final String tag;
  UserLocale(String tag) { this.tag = tag; }
  public String tag() { return tag; }
  public Locale toLocale() { return Locale.forLanguageTag(tag); }

  /** Converte uma tag/enum textual; {@code null} ou desconhecida ⇒ {@link #PT_BR}. */
  public static UserLocale fromTag(String value) {
    UserLocale l = parseOrNull(value);
    return l == null ? PT_BR : l;
  }

  /** Como {@link #fromTag(String)}, mas devolve {@code null} para valores não suportados (validação estrita). */
  public static UserLocale parseOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    String v = value.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    for (UserLocale l : values()) if (l.tag.toLowerCase(Locale.ROOT).equals(v) || l.name().replace('_', '-').toLowerCase(Locale.ROOT).equals(v)) return l;
    String lang = v.contains("-") ? v.substring(0, v.indexOf('-')) : v;
    return switch (lang) { case "pt" -> PT_BR; case "en" -> EN_US; case "es" -> ES_ES; default -> null; };
  }
}
