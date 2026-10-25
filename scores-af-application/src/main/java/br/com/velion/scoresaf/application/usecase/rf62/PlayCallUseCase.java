package br.com.velion.scoresaf.application.usecase.rf62;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.common.exception.ScoresException;
import br.com.velion.scoresaf.application.dto.MatchDtos;
import br.com.velion.scoresaf.application.service.MatchService;
import br.com.velion.scoresaf.application.usecase.rf42.MatchSupport;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.engine.model.AdvanceMode;
import br.com.velion.scoresaf.domain.engine.model.DecisionRecord;
import br.com.velion.scoresaf.domain.engine.model.MatchDecision;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.PendingDecision;
import br.com.velion.scoresaf.domain.match.MatchSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF62 — chamada de jogada num momento decisivo: o manager escolhe uma das opções ({@code PlayOption.code}) da decisão aberta e a
 * jogada é resolvida na hora (avanço {@code PLAY}). Só abre decisão o que o nível de chamada da conta pede (RF10: KEY_MOMENTS, ALL_PLAYS
 * ou NONE, congelado no kickoff). {@code version} obrigatório (409 MATCH_VERSION_CONFLICT). Decisão que não existe: 404
 * DECISION_NOT_FOUND; já resolvida (pelo manager ou pela IA): 409 DECISION_CLOSED; opção fora da lista: 422 PLAY_NOT_OFFERED. Prazo
 * vencido: a IA chama a jogada ({@code CallSource.AUTO}), o resultado é gravado e a resposta é 409 DECISION_CLOSED
 * ({@link DecisionClosedException}, que não desfaz a transação).
 */
@Service
public class PlayCallUseCase {
  public record Command(String optionCode, Long version) {}

  /** Decisão fechada (409): não desfaz a transação, para a decisão automática do prazo vencido ficar gravada. */
  public static class DecisionClosedException extends ConflictException {
    public DecisionClosedException(String message, Object... args) { super("DECISION_CLOSED", message, args); }
  }

  private final CareerSupport support; private final MatchSupport matches; private final MatchService matchService; private final AuditService audit;

  public PlayCallUseCase(CareerSupport support, MatchSupport matches, MatchService matchService, AuditService audit) {
    this.support = support; this.matches = matches; this.matchService = matchService; this.audit = audit;
  }

  @Transactional(noRollbackFor = DecisionClosedException.class)
  public MatchDtos.MatchStateDto execute(UUID matchId, UUID userId, String decisionId, Command c) {
    MatchSession session = matches.lockedSession(matchId, userId);
    support.writable(session.getCareer().getId(), userId);
    MatchSnapshot snap = MatchSupport.read(session);
    PendingDecision open = snap.pendingDecision();
    if (open == null || !open.id().equals(decisionId)) {
      DecisionRecord past = snap.decisionsLog().stream().filter(d -> decisionId.equals(d.decisionId())).findFirst().orElse(null);
      if (past == null) throw new NotFoundException("DECISION_NOT_FOUND", "Decisão não encontrada: " + decisionId, decisionId);
      throw closed(decisionId, past);
    }
    MatchSupport.checkVersion(session, c.version(), true);
    if (MatchSupport.expired(session, Instant.now())) {
      MatchSnapshot next = matchService.simulator().advance(snap, AdvanceMode.PLAY, List.of(new MatchDecision.Auto(open.id())));
      matches.store(session, next);
      audit.ok(AuditActions.PLAY_CALLED, "MatchSession", session.getId(), "decisão " + open.id() + " vencida: IA chamou " + open.autoOptionCode());
      throw closed(decisionId, next.decisionsLog().stream().filter(d -> decisionId.equals(d.decisionId())).findFirst().orElse(null));
    }
    if (c.optionCode() == null || !open.offers(c.optionCode())) {
      ScoresException e = new BusinessRuleException("PLAY_NOT_OFFERED", "Jogada fora das opções desta decisão: " + c.optionCode(), String.valueOf(c.optionCode()));
      throw e.withDetail("options", open.options().stream().map(o -> o.code()).toList());
    }
    MatchSnapshot next = matchService.simulator().advance(snap, AdvanceMode.PLAY, List.of(new MatchDecision.PlayCall(open.id(), c.optionCode())));
    matches.store(session, next);
    audit.ok(AuditActions.PLAY_CALLED, "MatchSession", session.getId(), "decisão " + open.id() + " (" + open.situation() + "): " + c.optionCode());
    return MatchSupport.state(session, next);
  }

  private static ScoresException closed(String decisionId, DecisionRecord past) {
    ScoresException e = new DecisionClosedException("A decisão " + decisionId + " já foi resolvida", decisionId);
    e.withDetail("decisionId", decisionId);
    if (past != null) e.withDetail("resolvedOption", past.optionCode()).withDetail("auto", past.auto());
    return e;
  }
}
