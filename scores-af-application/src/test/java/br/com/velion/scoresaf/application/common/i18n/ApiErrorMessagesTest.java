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
 * do editor de mundos (bloco 4), os da carreira, do elenco e do depth chart (bloco 6a) e os da partida (bloco 6b).
 * No basquete a lista conferida era a do Digital Double (RF44), que entra aqui no bloco 9.
 */
class ApiErrorMessagesTest {
  private static final List<String> BUNDLES = List.of("i18n/messages.properties", "i18n/messages_en.properties", "i18n/messages_es.properties");
  private static final List<String> PLATFORM = List.of("AUTH_FAILED", "AUTH_BUSY", "TOO_MANY_ATTEMPTS", "RATE_LIMITED", "WEAK_PASSWORD", "INVALID_TOKEN",
      "CONSENT_REQUIRED", "DELETION_CONFIRMATION_MISMATCH", "EXPORT_EXPIRED", "EMAIL_IN_USE", "INVALID_QUARTER_DURATION", "CONCURRENT_UPDATE");
  /** Bloco 4 — editor de mundos (RF5): validações do esporte e acesso ao mundo. */
  private static final List<String> WORLD_EDITOR = List.of("WORLD_READ_ONLY", "WORLD_NOT_TEMPLATE", "WORLD_LIMIT", "LEAGUE_FULL", "LEAGUE_TEAM_COUNT",
      "LEAGUE_HAS_CLUBS", "STRUCTURE_CONFERENCES", "STRUCTURE_DIVISIONS", "STRUCTURE_CLUBS", "STRUCTURE_DUPLICATE_ID", "ROSTER_FULL", "PRACTICE_SQUAD_FULL", "ROSTER_ABOVE_LIMIT",
      "JERSEY_TAKEN", "PLAYER_WITHOUT_CLUB", "INVALID_POSITION", "INVALID_HEIGHT", "INVALID_WEIGHT", "INVALID_ATTRIBUTE", "INVALID_ROSTER_STATUS",
      "INVALID_RULES", "INVALID_RULESET", "INVALID_JERSEY_NUMBER", "INVALID_STADIUM_CAPACITY", "INVALID_SECTOR", "INVALID_CONTRACT", "INVALID_COLOR",
      "INVALID_SHORT_NAME", "INVALID_NAME", "INVALID_VALUE", "INVALID_BIRTH_DATE");
  /** Bloco 6a — carreira, calendário, elenco e depth chart. */
  private static final List<String> CAREER = List.of("CAREER_NAME_INVALID", "CAREER_NAME_TAKEN", "CAREER_SLOTS_FULL", "CAREER_SLOT_TAKEN",
      "CAREER_ARCHIVED", "CAREER_CONFIRMATION_REQUIRED", "CAREER_VERSION_CONFLICT", "CAREER_UNEMPLOYED", "INVALID_CAREER_STATUS", "INVALID_COACH_STYLE",
      "INVALID_DIFFICULTY", "COACH_NAME_INVALID", "COACH_NATIONALITY_INVALID", "LEAGUE_NOT_IN_WORLD", "CLUB_REQUIRED", "CLUB_NOT_IN_LEAGUE",
      "LEAGUE_STRUCTURE_INVALID", "NO_SEASON", "INVALID_WEEK", "PLAYER_NOT_IN_ROSTER", "PRACTICE_SQUAD_INELIGIBLE", "IR_NOT_ALLOWED",
      "IR_RETURN_TOO_EARLY", "IR_STILL_INJURED", "IR_RETURNS_EXHAUSTED", "ROSTER_MOVE_NOT_ALLOWED", "CAP_EXCEEDED", "DEPTH_CHART_VERSION_CONFLICT",
      "DEPTH_CHART_UNKNOWN_SLOT", "DEPTH_CHART_DUPLICATE_SLOT", "DEPTH_CHART_PLAYER_UNAVAILABLE", "DEPTH_CHART_DUPLICATE_PLAYER",
      "DEPTH_CHART_INCOMPLETE", "DEPTH_CHART_TOO_MANY_ACTIVES", "INVALID_DEPTH");
  /** Bloco 6b — plano de jogo, playbook, preparação, partida ao vivo, intervalo e chamada de jogada. */
  private static final List<String> MATCH = List.of("GAME_PLAN_INVALID", "GAME_PLAN_VERSION_CONFLICT", "PLAYBOOK_INVALID", "PLAYBOOK_INCOMPLETE",
      "PLAYBOOK_VERSION_CONFLICT", "NO_NEXT_GAME", "DEPTH_CHART_INVALID", "MATCH_FINISHED", "MATCH_NOT_FINISHED", "MATCH_VERSION_CONFLICT",
      "INVALID_ADVANCE_MODE", "DECISION_PENDING", "DECISION_CLOSED", "DECISION_NOT_FOUND", "PLAY_NOT_OFFERED", "ADJUSTMENT_NOT_ALLOWED",
      "TIMEOUT_NOT_ALLOWED", "INVALID_ADJUSTMENT");

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
    for (String code : CAREER) assertThat(pt).as("código " + code).contains("error." + code);
    for (String code : MATCH) assertThat(pt).as("código " + code).contains("error." + code);
  }
}
