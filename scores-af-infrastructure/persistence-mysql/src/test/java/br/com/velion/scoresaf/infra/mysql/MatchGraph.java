package br.com.velion.scoresaf.infra.mysql;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.match.GamePlanSetup;
import br.com.velion.scoresaf.domain.match.MatchReport;
import br.com.velion.scoresaf.domain.match.MatchSession;
import br.com.velion.scoresaf.domain.match.MatchSessionStatus;
import br.com.velion.scoresaf.domain.match.PlaybookSetup;
import br.com.velion.scoresaf.domain.match.PlaybookWeight;
import br.com.velion.scoresaf.domain.match.PlayerMatchStats;
import br.com.velion.scoresaf.infra.mysql.repository.GamePlanSetupJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.MatchReportJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.MatchSessionJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.PlaybookSetupJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.PlaybookWeightJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.PlayerMatchStatsJpaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Grava a partida mínima (bloco 6b) sobre a carreira do {@link CareerGraph}: plano de jogo, playbook com dois pesos, partida ao vivo
 * com snapshot, relatório e uma linha de box score — todas as tabelas da V4.
 */
@Component
class MatchGraph {
  final GamePlanSetupJpaRepository plans; final PlaybookSetupJpaRepository playbooks; final PlaybookWeightJpaRepository weights;
  final MatchSessionJpaRepository sessions; final MatchReportJpaRepository reports; final PlayerMatchStatsJpaRepository stats;

  MatchGraph(GamePlanSetupJpaRepository plans, PlaybookSetupJpaRepository playbooks, PlaybookWeightJpaRepository weights, MatchSessionJpaRepository sessions,
             MatchReportJpaRepository reports, PlayerMatchStatsJpaRepository stats) {
    this.plans = plans; this.playbooks = playbooks; this.weights = weights; this.sessions = sessions; this.reports = reports; this.stats = stats;
  }

  record Saved(GamePlanSetup plan, PlaybookSetup playbook, MatchSession session, MatchReport report, PlayerMatchStats line) {}

  Saved persist(CareerGraph.Saved career, Player player) {
    GamePlanSetup g = new GamePlanSetup();
    g.setCareer(career.career()); g.setClub(career.fixture().getHomeClub());
    g.apply(new GamePlan(62, GamePlan.Aggression.AGGRESSIVE, GamePlan.Tempo.HURRY_UP, GamePlan.DefensiveFocus.STOP_PASS, GamePlan.Emphasis.HIGH, null));
    plans.save(g);
    PlaybookSetup p = new PlaybookSetup();
    p.setCareer(career.career()); p.setClub(career.fixture().getHomeClub()); p.touch();
    playbooks.save(p);
    weights.saveAll(List.of(PlaybookWeight.of(p, "RED_ZONE", "DEEP_PASS", 0), PlaybookWeight.of(p, PlaybookWeight.COVERAGE, "COVER_0", 200)));
    MatchSession s = new MatchSession();
    s.setFixture(career.fixture()); s.setCareer(career.career()); s.setStatus(MatchSessionStatus.AWAITING_DECISION); s.setPlayCallingLevel(PlayCallingLevel.ALL_PLAYS);
    s.setQuarter(2); s.setClockSeconds(95); s.setPossessionClubId(career.fixture().getHomeClub().getId()); s.setHomeScore(10); s.setAwayScore(7);
    s.setSeed(99L); s.setRngCursor(81); s.setEngineVersion(1); s.setSnapshotVersion(12); s.setDecisionId("d-81");
    s.setDecisionDeadlineAt(Instant.parse("2026-10-25T12:00:30Z")); s.setSnapshotJson("{\"status\":\"AWAITING_DECISION\"}");
    sessions.save(s);
    MatchReport r = new MatchReport();
    r.setFixture(career.fixture()); r.setCareer(career.career()); r.setHomeScore(24); r.setAwayScore(17); r.setWinnerClubId(career.fixture().getHomeClub().getId());
    r.setSeed(99L); r.setEngineVersion(1); r.setRulesVersion(1); r.setPlayCount(152); r.setUserGame(true); r.setReportJson("{\"drives\":[]}");
    reports.save(r);
    PlayerMatchStats line = new PlayerMatchStats();
    line.setFixture(career.fixture()); line.setCareer(career.career()); line.setPlayer(player); line.setClub(career.fixture().getHomeClub());
    line.setSnaps(61); line.setPassAttempts(34); line.setPassCompletions(22); line.setPassYards(254);
    stats.saveAll(List.of(line));
    return new Saved(g, p, s, r, line);
  }
}
