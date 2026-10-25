package br.com.velion.scoresaf.domain.engine.model;

import java.util.List;
import java.util.UUID;

/** Resultado consolidado da partida (RF42 → Fixture, box score, drives e play-by-play). {@code winnerClubId} nulo = empate. */
public record MatchResult(
    UUID fixtureId,
    long seed,
    int engineVersion,
    int rulesVersion,
    UUID homeClubId,
    UUID awayClubId,
    int homeScore,
    int awayScore,
    List<Integer> homeQuarters,
    List<Integer> awayQuarters,
    boolean overtime,
    UUID winnerClubId,
    TeamBoxScore homeTeam,
    TeamBoxScore awayTeam,
    List<PlayerBoxScore> homePlayers,
    List<PlayerBoxScore> awayPlayers,
    List<Drive> drives,
    List<PlayEvent> events,
    List<DecisionRecord> decisionsLog) {

  /** Lances que contam como jogada (snaps e chutes). */
  public long playCount() { return events.stream().filter(e -> e.type().isPlay()).count(); }
}
