package br.com.velion.scoresaf.domain.engine.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.engine.model.AdvanceMode;
import br.com.velion.scoresaf.domain.engine.model.CallSource;
import br.com.velion.scoresaf.domain.engine.model.DecisionRecord;
import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.engine.model.MatchDecision;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.PlayType;
import br.com.velion.scoresaf.domain.engine.model.Playbook;
import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RF61 / RF29 (bloco 6b): o playbook pesa a chamada da IA (conceito desativado não aparece; tudo em 100 é o jogo de antes) e o
 * ajuste do plano no meio da partida vale do próximo snap em diante, de forma determinística.
 */
class PlaybookTest {
  private final DefaultMatchSimulator sim = new DefaultMatchSimulator();

  private static Playbook without(PlayConcept... off) {
    Map<Playbook.Situation, Map<PlayConcept, Integer>> c = new EnumMap<>(Playbook.Situation.class);
    for (Playbook.Situation s : Playbook.Situation.values()) {
      Map<PlayConcept, Integer> w = new EnumMap<>(PlayConcept.class);
      for (PlayConcept p : off) w.put(p, 0);
      c.put(s, w);
    }
    return new Playbook(c, null);
  }

  private static long aiCalls(MatchResult r, UUID club, PlayConcept concept) {
    return r.events().stream().filter(e -> club.equals(e.possessionClubId()) && e.callSource() == CallSource.AI && e.concept() == concept).count();
  }

  @Test
  void playbook_padrao_explicito_da_o_mesmo_jogo_que_o_plano_sem_playbook() {
    MatchSetup base = TestTeams.pro(31);
    UUID home = base.home().clubId();
    MatchSetup explicit = base.withTeamPlan(home, base.home().gamePlan().withPlaybook(new Playbook(Map.of(), Map.of())));
    assertThat(sim.simulate(explicit)).isEqualTo(sim.simulate(base));
  }

  @Test
  void conceito_desativado_nao_e_chamado_pela_ia() {
    for (long seed = 1; seed <= 6; seed++) {
      MatchSetup base = TestTeams.pro(seed);
      UUID home = base.home().clubId();
      assertThat(aiCalls(sim.simulate(base), home, PlayConcept.DEEP_PASS) + aiCalls(sim.simulate(base), home, PlayConcept.OUTSIDE_RUN))
          .as("sem playbook a IA chama passe fundo e corrida por fora").isPositive();
      MatchSetup restricted = base.withTeamPlan(home, base.home().gamePlan().withPlaybook(without(PlayConcept.DEEP_PASS, PlayConcept.OUTSIDE_RUN)));
      MatchResult r = sim.simulate(restricted);
      assertThat(aiCalls(r, home, PlayConcept.DEEP_PASS)).as("semente " + seed).isZero();
      assertThat(aiCalls(r, home, PlayConcept.OUTSIDE_RUN)).as("semente " + seed).isZero();
      assertThat(aiCalls(r, home, PlayConcept.INSIDE_RUN)).isPositive();
    }
  }

  @Test
  void cobertura_desativada_some_das_chamadas_sem_blitz() {
    MatchSetup base = TestTeams.pro(8);
    UUID away = base.away().clubId();
    Map<Coverage, Integer> cov = new EnumMap<>(Coverage.class);
    cov.put(Coverage.COVER_3, 0);
    cov.put(Coverage.COVER_4, 0);
    MatchResult r = sim.simulate(base.withTeamPlan(away, base.away().gamePlan().withPlaybook(new Playbook(null, cov))));
    List<PlayEvent> defense = r.events().stream().filter(e -> base.home().clubId().equals(e.possessionClubId()) && !e.blitz()
        && (e.type() == PlayType.RUN || e.type() == PlayType.PASS)).toList();
    assertThat(defense).isNotEmpty().noneMatch(e -> e.coverage() == Coverage.COVER_3 || e.coverage() == Coverage.COVER_4);
  }

  @Test
  void ajuste_no_intervalo_vale_do_proximo_snap_em_diante_e_e_deterministico() {
    MatchSetup setup = TestTeams.pro(4040);
    UUID home = setup.home().clubId();
    MatchSnapshot s = sim.start(setup);
    while (s.quarter() < 3) s = sim.advance(s, AdvanceMode.QUARTER, List.of());
    int halftimeSeq = s.events().get(s.events().size() - 1).seq();
    GamePlan runOnly = new GamePlan(20, GamePlan.Aggression.AGGRESSIVE, GamePlan.Tempo.NORMAL, GamePlan.DefensiveFocus.BALANCED, GamePlan.Emphasis.LOW,
        null, without(PlayConcept.DEEP_PASS, PlayConcept.MEDIUM_PASS, PlayConcept.PLAY_ACTION));
    MatchSnapshot adjusted = sim.adjust(s, home, runOnly);
    assertThat(adjusted.version()).isEqualTo(s.version() + 1);
    assertThat(adjusted.setup().planOf(home)).isEqualTo(runOnly);
    assertThat(adjusted.decisionsLog()).last().satisfies(d -> {
      assertThat(d.optionCode()).isEqualTo(DecisionRecord.PLAN_CHANGE);
      assertThat(d.clubId()).isEqualTo(home);
    });
    MatchResult a = sim.finish(sim.advance(adjusted, AdvanceMode.FULL, List.of()));
    MatchSnapshot resumed = adjusted;
    while (!resumed.finished()) resumed = sim.advance(resumed, AdvanceMode.DRIVE, List.of());
    assertThat(sim.finish(resumed)).isEqualTo(a);
    // o 1º tempo é o mesmo do jogo sem ajuste; do 2º tempo em diante a IA da casa não chama mais passe médio ou fundo
    MatchResult untouched = sim.simulate(setup);
    assertThat(a.events().subList(0, halftimeSeq)).isEqualTo(untouched.events().subList(0, halftimeSeq));
    assertThat(a.events().stream().filter(e -> e.seq() > halftimeSeq && home.equals(e.possessionClubId()) && e.callSource() == CallSource.AI)
        .map(PlayEvent::concept)).doesNotContain(PlayConcept.DEEP_PASS, PlayConcept.MEDIUM_PASS, PlayConcept.PLAY_ACTION);
  }

  @Test
  void ajuste_e_recusado_com_decisao_aberta_ou_partida_encerrada() {
    MatchSetup setup = TestTeams.pro(55).withUser(TestTeams.pro(55).home().clubId(), PlayCallingLevel.KEY_MOMENTS);
    MatchSnapshot s = sim.advance(sim.start(setup), AdvanceMode.TO_DECISION, List.of());
    assertThat(s.awaitingDecision()).isTrue();
    MatchSnapshot pending = s;
    assertThatThrownBy(() -> sim.adjust(pending, setup.home().clubId(), GamePlan.defaults())).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sim.adjust(sim.advance(sim.start(TestTeams.pro(55)), AdvanceMode.FULL, List.of()), setup.home().clubId(), GamePlan.defaults()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sim.adjust(sim.start(TestTeams.pro(55)), UUID.randomUUID(), GamePlan.defaults())).isInstanceOf(IllegalArgumentException.class);
    MatchSnapshot next = sim.advance(pending, AdvanceMode.PLAY, List.of(new MatchDecision.Auto(pending.pendingDecision().id())));
    assertThat(next.awaitingDecision()).isFalse();
  }
}
