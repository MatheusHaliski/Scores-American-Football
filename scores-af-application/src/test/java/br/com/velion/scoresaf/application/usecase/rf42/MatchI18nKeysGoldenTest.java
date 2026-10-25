package br.com.velion.scoresaf.application.usecase.rf42;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.engine.model.EventKeys;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.RulesSnapshot;
import br.com.velion.scoresaf.domain.engine.sim.DefaultMatchSimulator;
import br.com.velion.scoresaf.domain.shared.PenaltyCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Um exemplo real (chave + parâmetros) de cada texto do play-by-play, mais os códigos de opção de chamada e de falta, gravados de
 * jogos do motor entre franquias do mundo semente (PRO e COLLEGE). O teste do front ({@code tests/match/event-keys.test.mts}) formata
 * cada exemplo nos três idiomas do catálogo {@code match}: chave sem tradução ou parâmetro que o motor não manda quebra o build.
 * Regenerar com {@code -Dgolden.update=true}.
 */
class MatchI18nKeysGoldenTest {
  static final Path GOLDEN = Path.of("..", "docs", "validation", "golden", "match-i18n-keys.json");

  record Example(String textKey, List<String> required, Map<String, String> params) {}

  record Sample(List<Example> events, List<String> options, List<String> penalties) {}

  @Test
  void cada_chave_de_evento_tem_um_exemplo_real_e_bate_com_a_amostra_dourada() throws Exception {
    DefaultMatchSimulator sim = new DefaultMatchSimulator();
    Map<String, Example> first = new LinkedHashMap<>();
    List<Club> ranked = SeedMatches.byStrength();
    long seed = 0;
    outer:
    for (RulesSnapshot rules : List.of(RulesSnapshot.pro(), RulesSnapshot.college())) {
      for (int round = 0; round < 12; round++) {
        for (int i = 0; i + 1 < ranked.size(); i += 2) {
          MatchSetup s = SeedMatches.setup(ranked.get(i), ranked.get(i + 1), 4_000 + seed++).withRules(rules);
          MatchResult r = sim.simulate(s);
          for (PlayEvent e : r.events()) first.putIfAbsent(e.textKey(), new Example(e.textKey(), EventKeys.REQUIRED_PARAMS.get(e.textKey()), e.params()));
          if (first.size() == EventKeys.ALL.size()) break outer;
        }
      }
    }
    List<String> missing = EventKeys.ALL.stream().filter(k -> !first.containsKey(k)).toList();
    assertThat(missing).as("chaves sem exemplo nos jogos da amostra").isEmpty();
    List<Example> ordered = new ArrayList<>();
    for (String k : EventKeys.ALL) ordered.add(first.get(k));
    Sample sample = new Sample(ordered, EventKeys.OPTION_CODES, Arrays.stream(PenaltyCode.values()).map(Enum::name).toList());
    JsonNode now = Jsons.MAPPER.readTree(Jsons.MAPPER.writeValueAsString(sample));
    if (Boolean.getBoolean("golden.update") || "1".equals(System.getenv("GOLDEN_UPDATE"))) {
      Files.writeString(GOLDEN, Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(now) + "\n");
    }
    assertThat(GOLDEN).exists();
    if (!Jsons.MAPPER.readTree(Files.readString(GOLDEN)).equals(now)) {
      fail("docs/validation/golden/match-i18n-keys.json diverge do motor: regenere com -Dgolden.update=true e rode npm run test:unit");
    }
  }
}
