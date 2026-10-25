package br.com.velion.scoresaf.application.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Mensagens de erro da API (RF46 CA05, RNF13): os 3 idiomas têm as mesmas chaves, inclusive os códigos da plataforma (bloco 3) e os
 * do editor de mundos (bloco 4).
 * No basquete a lista conferida era a do Digital Double (RF44), que entra aqui no bloco 9.
 */
class ApiErrorMessagesTest {
  private static final List<String> BUNDLES = List.of("i18n/messages.properties", "i18n/messages_en.properties", "i18n/messages_es.properties");
  private static final List<String> PLATFORM = List.of("AUTH_FAILED", "AUTH_BUSY", "TOO_MANY_ATTEMPTS", "RATE_LIMITED", "WEAK_PASSWORD", "INVALID_TOKEN",
      "CONSENT_REQUIRED", "DELETION_CONFIRMATION_MISMATCH", "EXPORT_EXPIRED", "EMAIL_IN_USE", "INVALID_QUARTER_DURATION", "CONCURRENT_UPDATE");
  /** Bloco 4 — editor de mundos (RF5): validações do esporte e acesso ao mundo. */
  private static final List<String> WORLD_EDITOR = List.of("WORLD_READ_ONLY", "WORLD_NOT_TEMPLATE", "WORLD_LIMIT", "LEAGUE_FULL", "LEAGUE_TEAM_COUNT",
      "LEAGUE_HAS_CLUBS", "STRUCTURE_CONFERENCES", "STRUCTURE_DIVISIONS", "STRUCTURE_CLUBS", "ROSTER_FULL", "PRACTICE_SQUAD_FULL", "ROSTER_ABOVE_LIMIT",
      "JERSEY_TAKEN", "PLAYER_WITHOUT_CLUB", "INVALID_POSITION", "INVALID_HEIGHT", "INVALID_WEIGHT", "INVALID_ATTRIBUTE", "INVALID_ROSTER_STATUS",
      "INVALID_RULES", "INVALID_RULESET", "INVALID_JERSEY_NUMBER", "INVALID_STADIUM_CAPACITY", "INVALID_SECTOR", "INVALID_CONTRACT", "INVALID_COLOR",
      "INVALID_SHORT_NAME", "INVALID_NAME", "INVALID_VALUE", "INVALID_BIRTH_DATE");

  private static Set<String> keys(String path) throws Exception {
    Properties p = new Properties();
    try (var in = ApiErrorMessagesTest.class.getClassLoader().getResourceAsStream(path)) {
      assertThat(in).as(path).isNotNull();
      p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return new TreeSet<>(p.stringPropertyNames());
  }

  @Test
  void os_tres_idiomas_tem_as_mesmas_chaves_e_os_codigos_da_plataforma() throws Exception {
    Set<String> pt = keys(BUNDLES.get(0));
    for (String b : BUNDLES.subList(1, BUNDLES.size())) assertThat(keys(b)).as(b).isEqualTo(pt);
    for (String code : PLATFORM) assertThat(pt).as("código " + code).contains("error." + code);
    for (String code : WORLD_EDITOR) assertThat(pt).as("código " + code).contains("error." + code);
  }
}
