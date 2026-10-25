package br.com.velion.scoresaf.application.security;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Nomes de exibição reservados (bloco 3), comparados depois da normalização (minúsculas, sem acento, só letras e dígitos):
 * "Admin", "ádmin" e "a.d.m.i.n" são o mesmo nome. Os prefixos {@code demo_} e {@code e2e_} ficam para contas de teste.
 */
public final class ReservedNames {
  static final Set<String> RESERVED = Set.of("admin", "administrador", "administrator", "root", "suporte", "support", "moderador",
      "moderator", "scores", "velion", "veliontech", "veliontechco", "sistema", "system", "staff", "oficial", "official");
  static final List<String> PREFIXES = List.of("demo_", "e2e_");

  private ReservedNames() {}

  public static boolean isReserved(String name) {
    if (name == null) return false;
    String lower = Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    for (String prefix : PREFIXES) if (lower.startsWith(prefix)) return true;
    String compact = lower.replaceAll("[^a-z0-9]", "");
    return RESERVED.contains(compact);
  }
}
