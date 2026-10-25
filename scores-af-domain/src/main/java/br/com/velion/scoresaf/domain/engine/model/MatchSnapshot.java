package br.com.velion.scoresaf.domain.engine.model;

import java.util.List;
import java.util.UUID;

/**
 * Estado vivo e serializável da partida (RF42: {@code MatchSession.snapshotJson}). O motor é uma função pura
 * {@code advance(snapshot, modo, decisões) → snapshot'}: mesma semente + mesmo log de decisões ⇒ mesmos eventos, placar e
 * estatísticas, parando ou não no meio. {@code rngCursor} conta as jogadas resolvidas — cada uma tem o seu gerador derivado da
 * semente e do cursor, então não há estado de gerador para guardar.
 *
 * @param version sobe a cada avanço que muda o estado (lock otimista da sessão)
 * @param ballOnAbsYd jarda absoluta 0–100 (0 = linha do gol do mandante)
 * @param possessionClubId quem tem a bola; num chute, quem chuta
 */
public record MatchSnapshot(
    MatchSetup setup,
    int engineVersion,
    long version,
    MatchStatus status,
    int quarter,
    int clockSeconds,
    int playClockSeconds,
    UUID possessionClubId,
    int down,
    int distance,
    int ballOnAbsYd,
    int homeScore,
    int awayScore,
    List<Integer> homeQuarters,
    List<Integer> awayQuarters,
    int homeTimeouts,
    int awayTimeouts,
    PendingDecision pendingDecision,
    List<PlayEvent> events,
    List<Drive> drives,
    List<DecisionRecord> decisionsLog,
    long rngCursor,
    EngineState engine) {

  public MatchSnapshot {
    homeQuarters = List.copyOf(homeQuarters);
    awayQuarters = List.copyOf(awayQuarters);
    events = List.copyOf(events);
    drives = List.copyOf(drives);
    decisionsLog = List.copyOf(decisionsLog);
  }

  public boolean finished() { return status == MatchStatus.FINISHED; }

  public boolean awaitingDecision() { return status == MatchStatus.AWAITING_DECISION; }

  /** Últimos {@code n} lances (mais antigo primeiro). */
  public List<PlayEvent> lastEvents(int n) { return events.subList(Math.max(0, events.size() - n), events.size()); }
}
