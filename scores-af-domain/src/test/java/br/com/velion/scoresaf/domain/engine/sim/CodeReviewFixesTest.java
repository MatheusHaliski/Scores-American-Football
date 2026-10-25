package br.com.velion.scoresaf.domain.engine.sim;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.engine.model.AdvanceMode;
import br.com.velion.scoresaf.domain.engine.model.DecisionSide;
import br.com.velion.scoresaf.domain.engine.model.DecisionSituation;
import br.com.velion.scoresaf.domain.engine.model.MatchDecision;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.PlayType;
import br.com.velion.scoresaf.domain.engine.model.RulesSnapshot;
import br.com.velion.scoresaf.domain.shared.PenaltyCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Casos apontados na revisão do PR #5: falta pessoal na 4ª descida curta e relógio de jogada configurado. */
class CodeReviewFixesTest {
  private final DefaultMatchSimulator sim = new DefaultMatchSimulator();
  private static final Set<PenaltyCode> PERSONAL = Set.of(PenaltyCode.ROUGHING_THE_PASSER, PenaltyCode.UNNECESSARY_ROUGHNESS, PenaltyCode.FACEMASK);

  /** O mesmo snapshot com outro cursor do gerador: mesma situação, outra jogada sorteada. */
  private static MatchSnapshot withCursor(MatchSnapshot s, long cursor) {
    return new MatchSnapshot(s.setup(), s.engineVersion(), s.version(), s.status(), s.quarter(), s.clockSeconds(), s.playClockSeconds(), s.possessionClubId(),
        s.down(), s.distance(), s.ballOnAbsYd(), s.homeScore(), s.awayScore(), s.homeQuarters(), s.awayQuarters(), s.homeTimeouts(), s.awayTimeouts(),
        s.pendingDecision(), s.events(), s.drives(), s.decisionsLog(), cursor, s.engine());
  }

  @Test
  void falta_pessoal_da_defesa_numa_4a_descida_que_ficou_curta_da_primeira_descida_ao_ataque() {
    MatchSetup base = TestTeams.pro(6060);
    MatchSetup setup = base.withUser(base.home().clubId(), PlayCallingLevel.KEY_MOMENTS);
    MatchSnapshot s = sim.advance(sim.start(setup), AdvanceMode.TO_DECISION, List.of());
    while (!(s.pendingDecision().situation() == DecisionSituation.FOURTH_DOWN && s.pendingDecision().side() == DecisionSide.OFFENSE
        && s.distance() >= 4 && s.pendingDecision().offers("INSIDE_RUN"))) {
      s = sim.advance(s, AdvanceMode.TO_DECISION, List.of(new MatchDecision.Auto(s.pendingDecision().id())));
    }
    MatchSnapshot fourth = s;
    int found = 0;
    for (long c = 0; c < 8000 && found < 3; c++) {
      MatchSnapshot before = withCursor(fourth, 1_000_000 + c);
      MatchSnapshot after = sim.advance(before, AdvanceMode.PLAY, List.of(new MatchDecision.PlayCall(fourth.pendingDecision().id(), "INSIDE_RUN")));
      PlayEvent e = after.events().get(fourth.events().size());
      if (e.type() != PlayType.RUN || !e.params().containsKey("yards")) continue;
      int runYards = Integer.parseInt(e.params().get("yards"));
      if (runYards >= fourth.distance()) continue; // só a corrida que ficou curta
      int penaltiesBefore = BoxScores.team(before, false).penalties();
      int penaltiesAfter = BoxScores.team(after, false).penalties();
      boolean shortRunWithPersonalFoul = e.penalty() != null && PERSONAL.contains(e.penalty().code());
      if (!shortRunWithPersonalFoul) continue;
      found++;
      assertThat(e.penalty().accepted()).isTrue();
      assertThat(e.turnover()).as("4ª descida curta + falta pessoal: não há turnover on downs").isFalse();
      assertThat(e.driveEnded()).isNull();
      assertThat(e.firstDown()).isTrue();
      assertThat(after.possessionClubId()).isEqualTo(setup.home().clubId());
      assertThat(after.down()).isEqualTo(1);
      assertThat(penaltiesAfter).isEqualTo(penaltiesBefore + 1);
    }
    assertThat(found).as("corridas curtas na 4ª com falta pessoal da defesa encontradas").isGreaterThan(0);
  }

  private static RulesSnapshot playClock(RulesSnapshot r, int playClock, int afterStoppage) {
    return new RulesSnapshot(r.version(), r.ruleset(), r.quarterMinutes(), playClock, afterStoppage, r.twoMinuteWarning(), r.timeoutsPerHalf(),
        r.clockStopsOnFirstDown(), r.overtimeFormat(), r.overtimeMinutes(), r.overtimeStartYardLine(), r.regularSeasonTiesAllowed(), r.downs(),
        r.yardsToGain(), r.pointsTouchdown(), r.pointsExtraPoint(), r.pointsTwoPoint(), r.pointsFieldGoal(), r.pointsSafety(), r.patYardLine(),
        r.twoPointYardLine(), r.kickoffYardLine(), r.touchbackYardLine(), r.oneFootInbounds());
  }

  @Test
  void tempo_ate_o_snap_respeita_o_relogio_de_jogada_configurado() {
    RulesSnapshot fast = playClock(RulesSnapshot.pro(), 20, 10);
    assertThat(br.com.velion.scoresaf.domain.catalog.LeagueRules.pro().toBuilder().playClockSeconds(20).playClockAfterStoppageSeconds(10).build().violations())
        .as("liga válida").isEmpty();
    int pairs = 0;
    long fastPlays = 0, normalPlays = 0;
    for (MatchResult r : SampleGames.run(fast, 30, 31_000, x -> x)) {
      fastPlays += r.playCount();
      List<PlayEvent> ev = r.events();
      for (int i = 0; i + 1 < ev.size(); i++) {
        PlayEvent a = ev.get(i), b = ev.get(i + 1);
        boolean scrimmage = (a.type() == PlayType.RUN || a.type() == PlayType.PASS) && (b.type() == PlayType.RUN || b.type() == PlayType.PASS);
        if (!scrimmage || a.quarter() != b.quarter() || a.clockStopped()) continue;
        // relógio correndo: duração da jogada (até 14 s) + tempo até o snap (no máximo os 20 s do relógio de jogada)
        assertThat(a.clockSeconds() - b.clockSeconds()).as("%s → %s", a, b).isLessThanOrEqualTo(14 + 20);
        pairs++;
      }
    }
    for (MatchResult r : SampleGames.run(RulesSnapshot.pro(), 30, 31_000, x -> x)) normalPlays += r.playCount();
    assertThat(pairs).isGreaterThan(1000);
    assertThat(fastPlays).as("relógio de jogada curto ⇒ mais jogadas").isGreaterThan(normalPlays + 30 * 20);
    for (long seed = 0; seed < 2000; seed++) {
      java.util.SplittableRandom r = new java.util.SplittableRandom(seed);
      for (br.com.velion.scoresaf.domain.engine.model.GamePlan.Tempo t : br.com.velion.scoresaf.domain.engine.model.GamePlan.Tempo.values()) {
        assertThat(ClockModel.runoff(br.com.velion.scoresaf.domain.engine.model.EngineState.ClockState.RUNNING, t, fast, r)).isBetween(0, 20);
        assertThat(ClockModel.runoff(br.com.velion.scoresaf.domain.engine.model.EngineState.ClockState.RESTART_ON_READY, t, fast, r)).isBetween(0, 10);
        assertThat(ClockModel.runoff(br.com.velion.scoresaf.domain.engine.model.EngineState.ClockState.RUNNING, t, RulesSnapshot.pro(), r)).isBetween(0, 40);
        assertThat(ClockModel.runoff(br.com.velion.scoresaf.domain.engine.model.EngineState.ClockState.RESTART_ON_READY, t, RulesSnapshot.pro(), r)).isBetween(0, 25);
      }
    }
  }
}
