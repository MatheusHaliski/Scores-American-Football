package br.com.velion.scoresaf.application.usecase.rf29;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.dto.MatchDtos;
import br.com.velion.scoresaf.application.service.MatchService;
import br.com.velion.scoresaf.application.usecase.rf42.MatchSupport;
import br.com.velion.scoresaf.application.usecase.rf61.GamePlanUseCases;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.engine.model.MatchDecision;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.Playbook;
import br.com.velion.scoresaf.domain.match.GamePlanPolicy;
import br.com.velion.scoresaf.domain.match.MatchSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF29 — playbook do intervalo e pausas ({@code POST /matches/{mid}/decisions}): ajustes do plano de jogo ({@code GAME_PLAN}) e do
 * playbook ({@code PLAYBOOK}) da franquia do usuário, só com a bola parada entre quartos, no intervalo ou antes do kickoff (422
 * ADJUSTMENT_NOT_ALLOWED), e tempo técnico ({@code TIMEOUT}) com a bola morta antes de um snap (422 TIMEOUT_NOT_ALLOWED). Valem a
 * partir do próximo snap; não avançam a partida (o front chama {@code /advance}). Mesma validação do RF61 (422 GAME_PLAN_INVALID,
 * PLAYBOOK_INVALID/INCOMPLETE). Só a partida muda: o plano salvo da franquia continua o mesmo. {@code version} obrigatório (409).
 */
@Service
public class MatchAdjustmentsUseCase {
  public record PlanPatch(Integer passRatio, String fourthDownAggression, String tempo, String defensiveFocus, String blitzFrequency, String baseDefense) {}
  public record PlaybookPatch(List<GamePlanPolicy.SituationInput> situations, List<GamePlanPolicy.WeightInput> coverages) {}
  public record Adjustment(String type, PlanPatch gamePlan, PlaybookPatch playbook) {}
  public record Command(Long version, List<Adjustment> adjustments) {}

  private final CareerSupport support; private final MatchSupport matches; private final MatchService matchService; private final AuditService audit;

  public MatchAdjustmentsUseCase(CareerSupport support, MatchSupport matches, MatchService matchService, AuditService audit) {
    this.support = support; this.matches = matches; this.matchService = matchService; this.audit = audit;
  }

  @Transactional
  public MatchDtos.MatchStateDto execute(UUID matchId, UUID userId, Command c) {
    MatchSession session = matches.lockedSession(matchId, userId);
    support.writable(session.getCareer().getId(), userId);
    if (session.isFinished()) throw new ConflictException("MATCH_FINISHED", "A partida já terminou");
    MatchSupport.checkVersion(session, c.version(), true);
    MatchSnapshot snap = MatchSupport.read(session);
    UUID club = snap.setup().userClubId();
    List<String> applied = new ArrayList<>();
    for (Adjustment a : c.adjustments() == null ? List.<Adjustment>of() : c.adjustments()) {
      String type = a == null || a.type() == null ? "" : a.type().trim().toUpperCase(Locale.ROOT);
      switch (type) {
        case "GAME_PLAN" -> {
          requireBreak(session);
          if (a.gamePlan() == null) throw new InvalidInputException("INVALID_ADJUSTMENT", "Ajuste GAME_PLAN sem o plano (gamePlan)", type);
          PlanPatch p = a.gamePlan();
          GamePlan plan = GamePlanUseCases.validPlan(p.passRatio(), p.fourthDownAggression(), p.tempo(), p.defensiveFocus(), p.blitzFrequency(), p.baseDefense());
          snap = matchService.simulator().adjust(snap, club, plan.withPlaybook(snap.setup().planOf(club).playbook()));
        }
        case "PLAYBOOK" -> {
          requireBreak(session);
          if (a.playbook() == null) throw new InvalidInputException("INVALID_ADJUSTMENT", "Ajuste PLAYBOOK sem o playbook (playbook)", type);
          Playbook book = GamePlanUseCases.validPlaybook(a.playbook().situations(), a.playbook().coverages());
          snap = matchService.simulator().adjust(snap, club, snap.setup().planOf(club).withPlaybook(book));
        }
        case "TIMEOUT" -> {
          try {
            snap = matchService.simulator().apply(snap, List.of(new MatchDecision.Timeout(club)));
          } catch (IllegalStateException e) {
            throw new BusinessRuleException("TIMEOUT_NOT_ALLOWED", "Tempo técnico indisponível agora: " + e.getMessage());
          }
        }
        default -> throw new InvalidInputException("INVALID_ADJUSTMENT", "Tipo de ajuste desconhecido: " + (a == null ? null : a.type()), String.valueOf(a == null ? null : a.type()));
      }
      applied.add(type);
    }
    if (applied.isEmpty()) throw new InvalidInputException("INVALID_ADJUSTMENT", "Nenhum ajuste informado", "");
    matches.store(session, snap);
    audit.ok(AuditActions.MATCH_ADJUSTED, "MatchSession", session.getId(), String.join(",", applied) + " no " + session.getStatus() + " do " + snap.quarter() + "º quarto");
    return MatchSupport.state(session, snap);
  }

  private static void requireBreak(MatchSession s) {
    if (!s.getStatus().adjustable()) {
      throw new BusinessRuleException("ADJUSTMENT_NOT_ALLOWED", "Ajustes do playbook só entre quartos, no intervalo ou antes do kickoff", s.getStatus().name());
    }
  }
}
