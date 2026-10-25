package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.shared.Position;
import java.util.List;
import java.util.UUID;

/**
 * Box score do jogador (docs/01 §3.6). Cada grupo é {@code null} quando o jogador não registrou nada nele (o front mostra a linha
 * vazia). Dentro do grupo, o que o motor não gera é {@code null} — e o front mostra "—", nunca 0 ({@link #NOT_GENERATED}).
 */
public record PlayerBoxScore(UUID playerId, UUID clubId, String name, Position position, int jerseyNumber, int snaps, PassingLine passing,
                             RushingLine rushing, ReceivingLine receiving, DefenseLine defense, KickingLine kicking, PuntingLine punting,
                             ReturnLine returns) {

  /** Campos que o motor desta versão não gera (sempre {@code null}): tackles assistidos e QB hits. */
  public static final List<String> NOT_GENERATED = List.of("defense.assistedTackles", "defense.qbHits");

  /** Passe: completos/tentados, jardas, TD, INT, sacks sofridos e jardas perdidas, rating (fórmula da liga profissional). */
  public record PassingLine(int completions, int attempts, int yards, int touchdowns, int interceptions, int sacked, int sackYards, double rating,
                            int longest) {}

  public record RushingLine(int attempts, int yards, double average, int touchdowns, int fumbles, int fumblesLost, int longest) {}

  public record ReceivingLine(int targets, int receptions, int yards, int yardsAfterCatch, int touchdowns, int drops, int longest) {}

  /** O motor credita cada tackle a um só defensor e não marca QB hit sem sack: {@code assistedTackles} e {@code qbHits} são "—". */
  public record DefenseLine(int soloTackles, Integer assistedTackles, int tacklesForLoss, int sacks, Integer qbHits, int interceptions,
                            int passesDefended, int forcedFumbles, int fumbleRecoveries, int touchdowns) {}

  /** FG por faixa de distância (0–39, 40–49, 50+), mais longo, PAT. */
  public record KickingLine(int fgMade, int fgAttempts, int fgMade0to39, int fgAttempts0to39, int fgMade40to49, int fgAttempts40to49,
                            int fgMade50plus, int fgAttempts50plus, int longest, int patMade, int patAttempts) {}

  public record PuntingLine(int punts, int yards, double average, int inside20, int touchbacks, int longest) {}

  public record ReturnLine(int kickReturns, int kickReturnYards, int puntReturns, int puntReturnYards, int touchdowns, int longest) {}
}
