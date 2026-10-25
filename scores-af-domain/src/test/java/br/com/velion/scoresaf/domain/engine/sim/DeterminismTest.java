package br.com.velion.scoresaf.domain.engine.sim;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.engine.model.AdvanceMode;
import br.com.velion.scoresaf.domain.engine.model.DecisionRecord;
import br.com.velion.scoresaf.domain.engine.model.MatchDecision;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.PendingDecision;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.RulesSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** RF42: mesma semente + mesmas decisões ⇒ mesmo jogo, parando ou não no meio. */
class DeterminismTest {
  private final DefaultMatchSimulator sim = new DefaultMatchSimulator();

  @Test
  void mesma_semente_gera_o_mesmo_jogo() {
    MatchSetup setup = TestTeams.pro(42);
    MatchResult a = sim.simulate(setup);
    MatchResult b = sim.simulate(setup);
    assertThat(b).isEqualTo(a);
    assertThat(b.events()).hasSizeGreaterThan(100).isEqualTo(a.events());
    assertThat(b.homePlayers()).isEqualTo(a.homePlayers());
  }

  @Test
  void semente_diferente_gera_outro_jogo() {
    assertThat(sim.simulate(TestTeams.pro(1)).events()).isNotEqualTo(sim.simulate(TestTeams.pro(2)).events());
  }

  @Test
  void jogada_a_jogada_com_snapshot_restaurado_a_cada_passo_reproduz_o_jogo_direto() {
    MatchSetup setup = TestTeams.pro(4242);
    MatchResult full = sim.simulate(setup);
    MatchSnapshot s = sim.start(setup);
    long lastCursor = -1;
    int steps = 0;
    while (!s.finished()) {
      s = sim.advance(s, AdvanceMode.PLAY, List.of());
      assertThat(s.rngCursor()).isGreaterThan(lastCursor);
      lastCursor = s.rngCursor();
      steps++;
    }
    assertThat(steps).isGreaterThan(120);
    assertThat(sim.finish(s)).isEqualTo(full);
  }

  @Test
  void por_quarto_e_por_drive_reproduz_o_jogo_direto() {
    for (AdvanceMode mode : List.of(AdvanceMode.QUARTER, AdvanceMode.DRIVE)) {
      MatchSetup setup = TestTeams.pro(777);
      MatchResult full = sim.simulate(setup);
      MatchSnapshot s = sim.start(setup);
      int stops = 0;
      while (!s.finished()) { s = sim.advance(s, mode, List.of()); stops++; }
      assertThat(stops).as(mode.name()).isGreaterThanOrEqualTo(mode == AdvanceMode.QUARTER ? 4 : 15);
      assertThat(sim.finish(s)).as(mode.name()).isEqualTo(full);
    }
  }

  @Test
  void college_por_jogada_reproduz_o_jogo_direto() {
    MatchSetup setup = TestTeams.setup(TestTeams.team("Leoes", 66, 11), TestTeams.team("Falcoes", 66, 12), RulesSnapshot.college(), 99);
    MatchResult full = sim.simulate(setup);
    MatchSnapshot s = sim.start(setup);
    while (!s.finished()) s = sim.advance(s, AdvanceMode.PLAY, List.of());
    assertThat(sim.finish(s)).isEqualTo(full);
  }

  /** Política determinística de "manager": escolhe a opção pelo hash do id da decisão. */
  private static MatchDecision choose(PendingDecision p) {
    if (p.id().hashCode() % 5 == 0) return new MatchDecision.Auto(p.id());
    return new MatchDecision.PlayCall(p.id(), p.options().get(Math.floorMod(p.id().hashCode(), p.options().size())).code());
  }

  @Test
  void mesmas_decisoes_reproduzem_o_jogo_e_o_log_reaplicado_tambem() {
    MatchSetup base = TestTeams.pro(2026);
    MatchSetup setup = base.withUser(base.home().clubId(), PlayCallingLevel.KEY_MOMENTS);
    MatchResult a = sim.simulate(setup, DeterminismTest::choose);
    assertThat(a.decisionsLog()).hasSizeGreaterThan(5);
    assertThat(a.decisionsLog()).anyMatch(DecisionRecord::auto).anyMatch(d -> !d.auto());
    assertThat(sim.simulate(setup, DeterminismTest::choose)).isEqualTo(a);

    Map<String, DecisionRecord> log = new HashMap<>();
    for (DecisionRecord d : a.decisionsLog()) log.put(d.decisionId(), d);
    Function<PendingDecision, MatchDecision> replay = p -> {
      DecisionRecord d = log.get(p.id());
      return d.auto() ? new MatchDecision.Auto(p.id()) : new MatchDecision.PlayCall(p.id(), d.optionCode());
    };
    assertThat(sim.simulate(setup, replay)).isEqualTo(a);
  }

  @Test
  void snapshot_parado_numa_decisao_e_continuado_da_o_mesmo_resultado() {
    MatchSetup base = TestTeams.pro(31337);
    MatchSetup setup = base.withUser(base.away().clubId(), PlayCallingLevel.KEY_MOMENTS);
    MatchResult direct = sim.simulate(setup, DeterminismTest::choose);
    MatchSnapshot s = sim.advance(sim.start(setup), AdvanceMode.FULL, List.of());
    for (int i = 0; i < 3; i++) {
      assertThat(s.awaitingDecision()).isTrue();
      s = sim.advance(s, AdvanceMode.TO_DECISION, List.of(choose(s.pendingDecision())));
    }
    MatchSnapshot paused = s;
    assertThat(paused.awaitingDecision()).isTrue();
    // retomada a partir do snapshot parado (o motor relê o snapshot a cada avanço)
    while (!s.finished()) {
      List<MatchDecision> d = s.awaitingDecision() ? List.of(choose(s.pendingDecision())) : List.of();
      s = sim.advance(s, AdvanceMode.PLAY, d);
    }
    assertThat(sim.finish(s)).isEqualTo(direct);
  }

  @Test
  void decisao_automatica_em_todo_momento_decisivo_e_o_mesmo_jogo_da_ia_sozinha() {
    MatchSetup ai = TestTeams.pro(5150);
    MatchSetup user = ai.withUser(ai.home().clubId(), PlayCallingLevel.KEY_MOMENTS);
    MatchResult a = sim.simulate(ai);
    MatchResult b = sim.simulate(user);
    assertThat(b.decisionsLog()).isNotEmpty().allMatch(DecisionRecord::auto);
    assertThat(b.homeScore()).isEqualTo(a.homeScore());
    assertThat(b.awayScore()).isEqualTo(a.awayScore());
    assertThat(b.homePlayers()).isEqualTo(a.homePlayers());
    assertThat(b.drives()).isEqualTo(a.drives());
    assertThat(b.events().stream().map(DeterminismTest::bare).toList()).isEqualTo(a.events().stream().map(DeterminismTest::bare).toList());
  }

  private static String bare(PlayEvent e) {
    return e.seq() + ":" + e.type() + ":" + e.result() + ":" + e.yards() + ":" + e.homeScore() + "-" + e.awayScore() + ":" + e.clockSeconds();
  }
}
