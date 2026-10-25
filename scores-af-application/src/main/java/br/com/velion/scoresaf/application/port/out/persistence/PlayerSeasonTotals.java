package br.com.velion.scoresaf.application.port.out.persistence;

import java.util.UUID;

/**
 * Soma das linhas de box score de um jogador por franquia nas semanas pedidas da temporada (consulta agregada, sem carregar as
 * linhas): jogos com pelo menos um snap e as colunas que o motor gera (D-35).
 */
public record PlayerSeasonTotals(UUID playerId, UUID clubId, long games, long snaps,
                                 long passAttempts, long passCompletions, long passYards, long passTouchdowns, long interceptionsThrown, long sacked,
                                 long rushAttempts, long rushYards, long rushTouchdowns, long fumblesLost,
                                 long targets, long receptions, long receivingYards, long receivingTouchdowns,
                                 long soloTackles, long tacklesForLoss, long sacks, long interceptions, long passesDefended, long forcedFumbles,
                                 long fumbleRecoveries, long fieldGoalsMade, long fieldGoalAttempts, long extraPointsMade, long extraPointAttempts,
                                 long punts, long puntYards) {}
