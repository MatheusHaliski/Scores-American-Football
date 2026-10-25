package br.com.velion.scoresaf.domain.engine.model;

import java.util.List;
import java.util.UUID;

/**
 * Box score da equipe (docs/01 §3.6): primeiras descidas, 3ª e 4ª descidas, red zone, tempo de posse, turnovers, faltas. Jardas de
 * passe são líquidas (descontados os sacks), como na liga profissional.
 */
public record TeamBoxScore(
    UUID clubId,
    int points,
    List<Integer> pointsByQuarter,
    int firstDowns,
    int firstDownsRushing,
    int firstDownsPassing,
    int firstDownsPenalty,
    int thirdDownConversions,
    int thirdDownAttempts,
    int fourthDownConversions,
    int fourthDownAttempts,
    int redZoneTouchdowns,
    int redZoneTrips,
    int offensivePlays,
    int totalYards,
    double yardsPerPlay,
    int rushAttempts,
    int rushYards,
    int passCompletions,
    int passAttempts,
    int passYards,
    int sacksAllowed,
    int sackYardsLost,
    int interceptionsThrown,
    int fumbles,
    int fumblesLost,
    int turnovers,
    int penalties,
    int penaltyYards,
    int timeOfPossessionSeconds,
    int drives,
    int punts,
    int fieldGoalsMade,
    int fieldGoalAttempts,
    int defensiveSacks,
    int timeoutsUsed) {

  public TeamBoxScore { pointsByQuarter = List.copyOf(pointsByQuarter); }
}
