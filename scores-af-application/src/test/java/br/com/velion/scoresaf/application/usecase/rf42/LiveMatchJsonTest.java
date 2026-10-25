package br.com.velion.scoresaf.application.usecase.rf42;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.application.dto.LiveMatchDto;
import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.engine.model.AdvanceMode;
import br.com.velion.scoresaf.domain.engine.model.DecisionSituation;
import br.com.velion.scoresaf.domain.engine.model.MatchDecision;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.sim.DefaultMatchSimulator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Estado ao vivo em JSON com as regras da API ({@link Jsons}): o snapshot do motor serializa e volta sem perda (parar, gravar,
 * restaurar e continuar = o mesmo jogo), e a amostra dourada {@code docs/validation/golden/live-match.json} — um jogo real do motor
 * entre franquias do mundo semente, parado na primeira decisão de 4ª descida do manager — é o contrato que o front lê no bloco 6.
 * Regenerar: {@code mvn -pl scores-af-application -am test -Dtest=LiveMatchJsonTest -Dsurefire.failIfNoSpecifiedTests=false -Dgolden.update=true}.
 */
class LiveMatchJsonTest {
  static final Path GOLDEN = Path.of("..", "docs", "validation", "golden", "live-match.json");
  private final DefaultMatchSimulator sim = new DefaultMatchSimulator();

  private MatchSetup setup() {
    Club home = SeedMatches.club("PAV");
    Club away = SeedMatches.byStrength().stream().filter(c -> c != home).skip(3).findFirst().orElseThrow();
    MatchSetup s = SeedMatches.setup(home, away, 20261025L);
    return s.withUser(home.getId(), PlayCallingLevel.KEY_MOMENTS);
  }

  /** Avança respondendo "prazo vencido" até a primeira decisão de 4ª descida. */
  private MatchSnapshot firstFourthDown(MatchSetup setup) {
    MatchSnapshot s = sim.advance(sim.start(setup), AdvanceMode.TO_DECISION, List.of());
    while (!s.finished() && s.pendingDecision().situation() != DecisionSituation.FOURTH_DOWN) {
      s = sim.advance(s, AdvanceMode.TO_DECISION, List.of(new MatchDecision.Auto(s.pendingDecision().id())));
    }
    assertThat(s.awaitingDecision()).isTrue();
    return s;
  }

  @Test
  void snapshot_em_json_volta_sem_perda_e_continua_o_mesmo_jogo() throws Exception {
    MatchSetup setup = setup();
    MatchResult direct = sim.simulate(setup);
    MatchSnapshot paused = firstFourthDown(setup);
    String json = Jsons.MAPPER.writeValueAsString(paused);
    MatchSnapshot restored = Jsons.MAPPER.readValue(json, MatchSnapshot.class);
    assertThat(restored).isEqualTo(paused);
    MatchSnapshot s = restored;
    while (!s.finished()) {
      List<MatchDecision> d = s.awaitingDecision() ? List.of(new MatchDecision.Auto(s.pendingDecision().id())) : List.of();
      s = sim.advance(s, AdvanceMode.DRIVE, d);
      s = Jsons.MAPPER.readValue(Jsons.MAPPER.writeValueAsString(s), MatchSnapshot.class); // grava e restaura a cada drive
    }
    assertThat(sim.finish(s)).isEqualTo(direct);
  }

  @Test
  void estado_ao_vivo_bate_com_a_amostra_dourada() throws Exception {
    MatchSnapshot s = firstFourthDown(setup());
    // pelo texto, como a API entrega (a árvore lida do texto tem os mesmos tipos numéricos que a do arquivo)
    JsonNode now = Jsons.MAPPER.readTree(Jsons.MAPPER.writeValueAsString(LiveMatchDto.of(s, 12)));
    if (Boolean.getBoolean("golden.update") || "1".equals(System.getenv("GOLDEN_UPDATE"))) {
      Files.writeString(GOLDEN, Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(now) + "\n");
    }
    assertThat(GOLDEN).as("amostra live-match.json (rodar a partir do módulo; regenerar com -Dgolden.update=true)").exists();
    if (!Jsons.MAPPER.readTree(Files.readString(GOLDEN)).equals(now)) {
      fail("docs/validation/golden/live-match.json diverge do motor: regenere com -Dgolden.update=true e confira o front no bloco 6");
    }
    // o que o front espera encontrar
    assertThat(now.get("status").asString()).isEqualTo("AWAITING_DECISION");
    assertThat(now.get("decision").get("situation").asString()).isEqualTo("FOURTH_DOWN");
    assertThat(now.get("down").asInt()).isEqualTo(4);
    assertThat(now.get("lastEvents").size()).isEqualTo(Math.min(12, s.events().size()));
    assertThat(now.get("lastEvents").get(0).get("textKey").asString()).startsWith("match:event.");
    JsonNode defense = null;
    for (JsonNode p : now.get("home").get("players")) if (!p.get("defense").isNull()) { defense = p.get("defense"); break; }
    assertThat(defense).isNotNull();
    assertThat(defense.has("assistedTackles")).isTrue();
    assertThat(defense.get("assistedTackles").isNull()).as("o que o motor não gera vai como null (o front mostra —)").isTrue();
  }
}
