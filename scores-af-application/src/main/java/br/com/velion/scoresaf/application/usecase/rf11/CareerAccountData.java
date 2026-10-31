package br.com.velion.scoresaf.application.usecase.rf11;

import br.com.velion.scoresaf.application.port.out.persistence.AnalysisReportRepository;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayoffSeedRepository;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonAwardRepository;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonRepository;
import br.com.velion.scoresaf.domain.analysis.AnalysisReport;
import br.com.velion.scoresaf.domain.season.PlayoffSeed;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.application.port.out.persistence.GamePlanSetupRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlaybookSetupRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.service.CareerDeletion;
import br.com.velion.scoresaf.application.service.GamePlanService;
import br.com.velion.scoresaf.application.usecase.rf42.MatchSupport;
import br.com.velion.scoresaf.application.usecase.rf46.AccountDataCollector;
import br.com.velion.scoresaf.application.usecase.rf46.AccountDataContributor;
import br.com.velion.scoresaf.application.usecase.rf46.AccountErasureHandler;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.engine.model.DecisionRecord;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.match.GamePlanSetup;
import br.com.velion.scoresaf.domain.match.MatchSession;
import br.com.velion.scoresaf.domain.match.PlaybookSetup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * As carreiras na exportação e na eliminação da conta (RF46 CA07, LGPD Art. 18, II, V e VI; D-12): a exportação ganha a seção
 * {@code careers} (uma linha por carreira: nome, slot, situação, head coach, franquia, liga, temporada, semente, datas) e a seção
 * {@code careerRosterMoves} (os movimentos do elenco que o titular fez); desde o bloco 6b, {@code careerMatches} (as partidas que ele
 * jogou: placar, semente, nível de chamada, quantas jogadas chamou e quantos ajustes fez no intervalo) e {@code careerGamePlans} (o plano
 * de jogo e o playbook gravados); desde o bloco 6c, {@code careerSeasons} (cada temporada: número, ano, fase, campeã, vice, a semente e
 * o resultado da franquia dele e os prêmios) e {@code careerAnalyses} (os relatórios do analista que ele pediu: semana, data e o
 * resumo). A eliminação apaga cada carreira inteira, com o mundo SAVE ({@link CareerDeletion}).
 */
@Component
public class CareerAccountData implements AccountDataContributor, AccountErasureHandler {
  private final CareerSaveRepository saves;
  private final RosterMoveRepository moves;
  private final CareerDeletion deletion;
  private final MatchSessionRepository sessions;
  private final GamePlanSetupRepository plans;
  private final PlaybookSetupRepository playbooks;
  private final GamePlanService planService;
  private final SeasonRepository seasons;
  private final PlayoffSeedRepository seeds;
  private final SeasonAwardRepository awards;
  private final AnalysisReportRepository analyses;

  public CareerAccountData(CareerSaveRepository saves, RosterMoveRepository moves, CareerDeletion deletion, MatchSessionRepository sessions,
                           GamePlanSetupRepository plans, PlaybookSetupRepository playbooks, GamePlanService planService, SeasonRepository seasons,
                           PlayoffSeedRepository seeds, SeasonAwardRepository awards, AnalysisReportRepository analyses) {
    this.saves = saves; this.moves = moves; this.deletion = deletion; this.sessions = sessions; this.plans = plans; this.playbooks = playbooks;
    this.planService = planService; this.seasons = seasons; this.seeds = seeds; this.awards = awards; this.analyses = analyses;
  }

  @Override
  @Transactional(readOnly = true)
  public void contribute(UUID userId, Map<String, Object> export) {
    List<Map<String, Object>> careers = new ArrayList<>();
    List<Map<String, Object>> rosterMoves = new ArrayList<>();
    List<Map<String, Object>> matches = new ArrayList<>();
    List<Map<String, Object>> gamePlans = new ArrayList<>();
    List<Map<String, Object>> seasonRows = new ArrayList<>();
    List<Map<String, Object>> analysisRows = new ArrayList<>();
    for (CareerSave s : saves.findByUserIdOrderByCreatedAtAsc(userId)) {
      java.util.UUID club = s.getClub() == null ? null : s.getClub().getId();
      for (Season se : seasons.findByCareerIdOrderByNumberAsc(s.getId())) {
        Integer seed = club == null ? null : seeds.findBySeasonIdOrderBySeedAsc(se.getId()).stream().filter(x -> x.getClub().getId().equals(club))
            .map(PlayoffSeed::getSeed).findFirst().orElse(null);
        List<String> seasonAwards = awards.findBySeasonId(se.getId()).stream().map(a -> a.getType().name() + ": " + a.getWinnerName()).sorted().toList();
        seasonRows.add(AccountDataCollector.row("careerId", String.valueOf(s.getId()), "number", se.getNumber(), "year", se.getYear(),
            "phase", se.getPhase().name(), "champion", se.getChampionClub() == null ? null : se.getChampionClub().getName(),
            "runnerUp", se.getRunnerUpClub() == null ? null : se.getRunnerUpClub().getName(), "userSeed", seed, "awards", seasonAwards,
            "completedAt", se.getCompletedAt() == null ? null : String.valueOf(se.getCompletedAt())));
      }
      for (AnalysisReport a : analyses.findByCareerIdOrderByCreatedAtAsc(s.getId())) {
        analysisRows.add(AccountDataCollector.row("careerId", String.valueOf(s.getId()), "seasonId", String.valueOf(a.getSeason().getId()), "week", a.getWeek(),
            "source", a.getSource(), "generatedAt", String.valueOf(a.getCreatedAt()), "report", a.getReportJson()));
      }
      careers.add(AccountDataCollector.row("id", String.valueOf(s.getId()), "name", s.getName(), "slot", s.getSlot(), "status", s.getStatus().name(),
          "difficulty", s.getDifficulty().name(), "coachName", s.getManager().getName(), "coachNationality", s.getManager().getNationality(),
          "coachStyle", s.getManager().getCoachStyle().name(), "club", s.getClub() == null ? null : s.getClub().getName(),
          "league", s.getLeague() == null ? null : s.getLeague().getName(), "seasonYear", s.getSeason() == null ? null : s.getSeason().getYear(),
          "currentWeek", s.getCurrentWeek(), "seed", s.getSeed(), "sourceWorld", s.getSourceWorldName(), "createdAt", String.valueOf(s.getCreatedAt()),
          "lastPlayedAt", String.valueOf(s.getLastPlayedAt())));
      for (RosterMove m : moves.findByCareerIdOrderByCreatedAtAsc(s.getId())) {
        rosterMoves.add(AccountDataCollector.row("careerId", String.valueOf(s.getId()), "player", m.getPlayer().getName(), "week", m.getWeek(),
            "kind", m.getKind().name(), "from", m.getFromStatus().name(), "to", m.getToStatus().name(), "deadMoney", m.getDeadMoney(),
            "at", String.valueOf(m.getCreatedAt())));
      }
      for (MatchSession ms : sessions.findByCareerIdOrderByCreatedAtAsc(s.getId())) {
        MatchSnapshot snap = MatchSupport.read(ms);
        long calls = snap.decisionsLog().stream().filter(d -> d.decisionId() != null && !d.auto()).count();
        long adjustments = snap.decisionsLog().stream().filter(d -> DecisionRecord.PLAN_CHANGE.equals(d.optionCode())).count();
        matches.add(AccountDataCollector.row("careerId", String.valueOf(s.getId()), "week", ms.getFixture().getWeekNumber(),
            "home", ms.getFixture().getHomeClub().getName(), "away", ms.getFixture().getAwayClub().getName(), "status", ms.getStatus().name(),
            "homeScore", ms.getHomeScore(), "awayScore", ms.getAwayScore(), "seed", ms.getSeed(), "playCallingLevel", ms.getPlayCallingLevel().name(),
            "playCalls", calls, "adjustments", adjustments, "startedAt", String.valueOf(ms.getCreatedAt()), "finishedAt", String.valueOf(ms.getFinishedAt())));
      }
      for (GamePlanSetup g : plans.findByCareerId(s.getId())) {
        gamePlans.add(AccountDataCollector.row("careerId", String.valueOf(s.getId()), "kind", "GAME_PLAN", "passRatio", g.getPassRatio(),
            "fourthDownAggression", g.getFourthDownAggression().name(), "tempo", g.getTempo().name(), "defensiveFocus", g.getDefensiveFocus().name(),
            "blitzFrequency", g.getBlitzFrequency().name(), "baseDefense", g.getBaseDefense().name(), "updatedAt", String.valueOf(g.getUpdatedAt())));
      }
      for (PlaybookSetup p : playbooks.findByCareerId(s.getId())) {
        gamePlans.add(AccountDataCollector.row("careerId", String.valueOf(s.getId()), "kind", "PLAYBOOK",
            "customWeights", GamePlanService.customWeights(planService.read(p)), "updatedAt", String.valueOf(p.getUpdatedAt())));
      }
    }
    export.put("careers", careers);
    export.put("careerRosterMoves", rosterMoves);
    export.put("careerMatches", matches);
    export.put("careerGamePlans", gamePlans);
    export.put("careerSeasons", seasonRows);
    export.put("careerAnalyses", analysisRows);
  }

  @Override
  @Transactional
  public void erase(UUID userId) {
    for (CareerSave s : saves.findByUserIdOrderByCreatedAtAsc(userId)) deletion.delete(s);
  }
}
