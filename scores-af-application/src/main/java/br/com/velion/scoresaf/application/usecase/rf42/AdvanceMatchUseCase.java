package br.com.velion.scoresaf.application.usecase.rf42;

import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.common.exception.ScoresException;
import br.com.velion.scoresaf.application.dto.MatchDtos;
import br.com.velion.scoresaf.application.service.MatchService;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.engine.model.AdvanceMode;
import br.com.velion.scoresaf.domain.engine.model.MatchDecision;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.match.MatchSession;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF27/RF42 — avança a partida ao vivo ({@code PLAY}, {@code DRIVE}, {@code QUARTER}, {@code TO_DECISION}, {@code FULL}; todo modo
 * para na próxima decisão do manager e no fim). Com decisão aberta no prazo: 409 DECISION_PENDING (chame a jogada, RF62); com o prazo
 * vencido, a IA chama a jogada ({@code CallSource.AUTO}) e o avanço segue. {@code version} opcional (409 MATCH_VERSION_CONFLICT se
 * divergir). Ao terminar, grava o resultado e fecha a semana ({@link MatchSupport#store}). Partida encerrada: 409 MATCH_FINISHED.
 */
@Service
public class AdvanceMatchUseCase {
  public record Command(String mode, Long version) {}

  private final CareerSupport support; private final MatchSupport matches; private final MatchService matchService;

  public AdvanceMatchUseCase(CareerSupport support, MatchSupport matches, MatchService matchService) {
    this.support = support; this.matches = matches; this.matchService = matchService;
  }

  @Transactional
  public MatchDtos.MatchStateDto execute(UUID matchId, UUID userId, Command c) {
    MatchSession session = matches.lockedSession(matchId, userId);
    support.writable(session.getCareer().getId(), userId);
    AdvanceMode mode = mode(c.mode());
    if (session.isFinished()) throw new ConflictException("MATCH_FINISHED", "A partida já terminou");
    MatchSupport.checkVersion(session, c.version(), false);
    MatchSnapshot snap = MatchSupport.read(session);
    List<MatchDecision> decisions = List.of();
    if (snap.awaitingDecision()) {
      if (!MatchSupport.expired(session, Instant.now())) {
        ScoresException e = new ConflictException("DECISION_PENDING", "Há uma chamada de jogada aberta: escolha a jogada ou espere o prazo", snap.pendingDecision().id());
        throw e.withDetail("decisionId", snap.pendingDecision().id()).withDetail("deadlineAt", String.valueOf(session.getDecisionDeadlineAt()));
      }
      decisions = List.of(new MatchDecision.Auto(snap.pendingDecision().id()));
    }
    MatchSnapshot next = matchService.simulator().advance(snap, mode, decisions);
    matches.store(session, next);
    return MatchSupport.state(session, next);
  }

  static AdvanceMode mode(String raw) {
    if (raw == null || raw.isBlank()) return AdvanceMode.PLAY;
    try {
      return AdvanceMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InvalidInputException("INVALID_ADVANCE_MODE", "Modo de avanço inválido: " + raw, raw);
    }
  }
}
