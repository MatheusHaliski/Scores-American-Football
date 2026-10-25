package br.com.velion.scoresaf.domain.engine.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.engine.model.AdvanceMode;
import br.com.velion.scoresaf.domain.engine.model.CallSource;
import br.com.velion.scoresaf.domain.engine.model.DecisionRecord;
import br.com.velion.scoresaf.domain.engine.model.DecisionSide;
import br.com.velion.scoresaf.domain.engine.model.DecisionSituation;
import br.com.velion.scoresaf.domain.engine.model.MatchDecision;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.MatchStatus;
import br.com.velion.scoresaf.domain.engine.model.PendingDecision;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.PlayOption;
import br.com.velion.scoresaf.domain.engine.model.PlayType;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Momentos decisivos (RF62): a partida para com a decisão pendente; vencido o prazo, a IA decide. */
class DecisionsTest {
  private final DefaultMatchSimulator sim = new DefaultMatchSimulator();

  private MatchSetup keyMoments(long seed) {
    MatchSetup s = TestTeams.pro(seed);
    return s.withUser(s.home().clubId(), PlayCallingLevel.KEY_MOMENTS);
  }

  /** Avança respondendo "auto" até aparecer uma decisão que satisfaça {@code want}. */
  private MatchSnapshot until(MatchSnapshot s, java.util.function.Predicate<PendingDecision> want) {
    s = sim.advance(s, AdvanceMode.TO_DECISION, List.of());
    while (!s.finished() && !want.test(s.pendingDecision())) s = sim.advance(s, AdvanceMode.TO_DECISION, List.of(new MatchDecision.Auto(s.pendingDecision().id())));
    return s;
  }

  @Test
  void momentos_decisivos_param_a_partida_com_opcoes_e_a_escolha_da_ia_para_o_prazo() {
    MatchSetup setup = keyMoments(808);
    MatchSnapshot s = sim.advance(sim.start(setup), AdvanceMode.FULL, List.of());
    Set<DecisionSituation> seen = EnumSet.noneOf(DecisionSituation.class);
    int count = 0;
    while (!s.finished()) {
      assertThat(s.status()).isEqualTo(MatchStatus.AWAITING_DECISION);
      PendingDecision p = s.pendingDecision();
      seen.add(p.situation());
      assertThat(p.options()).hasSizeBetween(2, 5);
      assertThat(p.offers(p.autoOptionCode())).isTrue();
      assertThat(p.clubId()).isEqualTo(setup.home().clubId());
      boolean userHasBall = p.side() == DecisionSide.OFFENSE;
      assertThat(p.options()).allMatch(o -> o.side() == p.side());
      assertThat(p.options()).allMatch(o -> br.com.velion.scoresaf.domain.engine.model.EventKeys.OPTION_CODES.contains(o.code()));
      switch (p.situation()) {
        case FOURTH_DOWN -> assertThat(p.down()).isEqualTo(4);
        case TWO_MINUTE -> { assertThat(p.quarter()).isIn(2, 4); assertThat(p.clockSeconds()).isLessThanOrEqualTo(120); }
        case CONVERSION -> { assertThat(p.quarter()).isGreaterThanOrEqualTo(4); assertThat(userHasBall || p.side() == DecisionSide.DEFENSE).isTrue(); }
        case OVERTIME -> assertThat(p.quarter()).isGreaterThanOrEqualTo(5);
        case ALL_PLAYS -> throw new AssertionError("KEY_MOMENTS não abre ALL_PLAYS");
      }
      s = sim.advance(s, AdvanceMode.FULL, List.of(new MatchDecision.Auto(p.id())));
      count++;
    }
    assertThat(seen).contains(DecisionSituation.FOURTH_DOWN, DecisionSituation.TWO_MINUTE);
    assertThat(count).isGreaterThan(8);
  }

  @Test
  void quarta_descida_oferece_punt_e_a_escolha_do_manager_vira_a_jogada() {
    MatchSnapshot s = until(sim.start(keyMoments(909)), p -> p.situation() == DecisionSituation.FOURTH_DOWN && p.side() == DecisionSide.OFFENSE && p.offers("PUNT"));
    assertThat(s.awaitingDecision()).isTrue();
    PendingDecision p = s.pendingDecision();
    assertThat(p.options().stream().map(PlayOption::code)).contains("PUNT", "SHORT_PASS");
    int before = s.events().size();
    MatchSnapshot after = sim.advance(s, AdvanceMode.PLAY, List.of(new MatchDecision.PlayCall(p.id(), "PUNT")));
    PlayEvent ev = after.events().subList(before, after.events().size()).stream().filter(e -> e.type().isPlay() || e.type() == PlayType.NO_PLAY).findFirst().orElseThrow();
    if (ev.type() != PlayType.NO_PLAY) {
      assertThat(ev.type()).isEqualTo(PlayType.PUNT);
      assertThat(ev.callSource()).isEqualTo(CallSource.USER);
    }
    DecisionRecord rec = after.decisionsLog().get(after.decisionsLog().size() - 1);
    assertThat(rec.decisionId()).isEqualTo(p.id());
    assertThat(rec.optionCode()).isEqualTo("PUNT");
    assertThat(rec.auto()).isFalse();
  }

  @Test
  void decisao_vencida_e_decidida_pela_ia_e_fica_marcada_como_automatica() {
    MatchSnapshot s = until(sim.start(keyMoments(1010)), p -> true);
    PendingDecision p = s.pendingDecision();
    int before = s.events().size();
    MatchSnapshot after = sim.advance(s, AdvanceMode.PLAY, List.of(new MatchDecision.Auto(p.id())));
    DecisionRecord rec = after.decisionsLog().get(after.decisionsLog().size() - 1);
    assertThat(rec.auto()).isTrue();
    assertThat(rec.optionCode()).isEqualTo(p.autoOptionCode());
    PlayEvent ev = after.events().subList(before, after.events().size()).stream().filter(e -> e.type().isPlay() || e.type() == PlayType.NO_PLAY).findFirst().orElseThrow();
    assertThat(ev.callSource()).isEqualTo(CallSource.AUTO);
  }

  @Test
  void manager_na_defesa_chama_a_defesa() {
    MatchSnapshot s = until(sim.start(keyMoments(1111)), p -> p.side() == DecisionSide.DEFENSE && p.situation() != DecisionSituation.CONVERSION
        && p.offers("DEF_DIME_COVER_4"));
    PendingDecision p = s.pendingDecision();
    assertThat(p.options()).allMatch(o -> o.side() == DecisionSide.DEFENSE && o.defensePackage() != null && o.coverage() != null);
    int before = s.events().size();
    MatchSnapshot after = sim.advance(s, AdvanceMode.PLAY, List.of(new MatchDecision.PlayCall(p.id(), "DEF_DIME_COVER_4")));
    PlayEvent ev = after.events().subList(before, after.events().size()).stream().filter(e -> e.type() == PlayType.RUN || e.type() == PlayType.PASS).findFirst().orElse(null);
    if (ev != null) assertThat(ev.defensePackage()).isEqualTo(DefensePackage.DIME);
  }

  @Test
  void nivel_none_e_jogo_sem_humano_nunca_abrem_decisao() {
    MatchSetup s = TestTeams.pro(1212);
    MatchResult none = sim.simulate(s.withUser(s.home().clubId(), PlayCallingLevel.NONE), p -> { throw new AssertionError("NONE abriu decisão"); });
    assertThat(none.decisionsLog()).isEmpty();
    assertThat(none.events()).allMatch(e -> e.callSource() == null || e.callSource() == CallSource.AI);
    MatchSnapshot ai = sim.advance(sim.start(s), AdvanceMode.FULL, List.of());
    assertThat(ai.finished()).isTrue();
  }

  @Test
  void todas_as_jogadas_abre_decisao_em_cada_snap() {
    MatchSetup s = TestTeams.pro(1313);
    Set<String> offered = new java.util.TreeSet<>();
    MatchResult r = sim.simulate(s.withUser(s.away().clubId(), PlayCallingLevel.ALL_PLAYS), p -> {
      p.options().forEach(o -> offered.add(o.code()));
      return new MatchDecision.Auto(p.id());
    });
    assertThat(br.com.velion.scoresaf.domain.engine.model.EventKeys.OPTION_CODES).containsAll(offered);
    assertThat(offered).contains("INSIDE_RUN", "SHORT_PASS", "PUNT", "EXTRA_POINT", "DEF_BASE_COVER_3", "DEF_BLITZ_COVER_1");
    assertThat(r.decisionsLog()).hasSizeGreaterThan(100).allMatch(d -> d.situation() == DecisionSituation.ALL_PLAYS);
  }

  @Test
  void decisao_invalida_e_recusada() {
    MatchSnapshot s = until(sim.start(keyMoments(1414)), p -> true);
    PendingDecision p = s.pendingDecision();
    assertThatThrownBy(() -> sim.advance(s, AdvanceMode.PLAY, List.of(new MatchDecision.PlayCall("dec-x", p.autoOptionCode())))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> sim.advance(s, AdvanceMode.PLAY, List.of(new MatchDecision.PlayCall(p.id(), "NAO_EXISTE")))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> sim.advance(s, AdvanceMode.PLAY, List.of(new MatchDecision.Timeout(java.util.UUID.randomUUID())))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tempo_tecnico_do_manager_para_o_relogio_e_acaba_em_3_por_tempo() {
    MatchSetup setup = keyMoments(1515);
    MatchSnapshot s = sim.start(setup);
    while (!(s.quarter() == 1 && s.clockSeconds() < 600 && s.engine().phase() == br.com.velion.scoresaf.domain.engine.model.EngineState.Phase.SCRIMMAGE && !s.awaitingDecision())) {
      s = sim.advance(s, AdvanceMode.PLAY, s.awaitingDecision() ? List.of(new MatchDecision.Auto(s.pendingDecision().id())) : List.of());
    }
    for (int i = 3; i >= 1; i--) {
      s = sim.advance(s, AdvanceMode.PLAY, List.of(new MatchDecision.Timeout(setup.home().clubId())));
      assertThat(s.homeTimeouts()).isEqualTo(i - 1);
      while (s.engine().phase() != br.com.velion.scoresaf.domain.engine.model.EngineState.Phase.SCRIMMAGE || s.awaitingDecision()) {
        s = sim.advance(s, AdvanceMode.PLAY, s.awaitingDecision() ? List.of(new MatchDecision.Auto(s.pendingDecision().id())) : List.of());
      }
    }
    MatchSnapshot last = s;
    assertThat(last.events()).filteredOn(e -> e.type() == PlayType.TIMEOUT).hasSizeGreaterThanOrEqualTo(3);
    assertThatThrownBy(() -> sim.advance(last, AdvanceMode.PLAY, List.of(new MatchDecision.Timeout(setup.home().clubId())))).isInstanceOf(IllegalStateException.class);
    assertThat(last.decisionsLog()).filteredOn(d -> DecisionRecord.TIMEOUT.equals(d.optionCode())).hasSize(3);
  }
}
