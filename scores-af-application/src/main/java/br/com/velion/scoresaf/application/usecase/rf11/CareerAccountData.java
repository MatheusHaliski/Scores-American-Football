package br.com.velion.scoresaf.application.usecase.rf11;

import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
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
 * de jogo e o playbook gravados); a eliminação apaga cada carreira inteira, com o mundo SAVE ({@link CareerDeletion}).
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

  public CareerAccountData(CareerSaveRepository saves, RosterMoveRepository moves, CareerDeletion deletion, MatchSessionRepository sessions,
                           GamePlanSetupRepository plans, PlaybookSetupRepository playbooks, GamePlanService planService) {
    this.saves = saves; this.moves = moves; this.deletion = deletion; this.sessions = sessions; this.plans = plans; this.playbooks = playbooks;
    this.planService = planService;
  }

  @Override
  @Transactional(readOnly = true)
  public void contribute(UUID userId, Map<String, Object> export) {
    List<Map<String, Object>> careers = new ArrayList<>();
    List<Map<String, Object>> rosterMoves = new ArrayList<>();
    List<Map<String, Object>> matches = new ArrayList<>();
    List<Map<String, Object>> gamePlans = new ArrayList<>();
    for (CareerSave s : saves.findByUserIdOrderByCreatedAtAsc(userId)) {
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
  }

  @Override
  @Transactional
  public void erase(UUID userId) {
    for (CareerSave s : saves.findByUserIdOrderByCreatedAtAsc(userId)) deletion.delete(s);
  }
}
