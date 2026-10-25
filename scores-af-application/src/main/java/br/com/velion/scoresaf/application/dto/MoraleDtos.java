package br.com.velion.scoresaf.application.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs da moral (RF41, D-102), lidos pelo front pela amostra dourada {@code morale.json}: a moral do elenco e a de cada jogador, com os
 * fatores da última semana (em pontos de moral).
 */
public final class MoraleDtos {
  private MoraleDtos() {}

  /**
   * Moral do jogador: valor (0–100), faixa (HIGH, GOOD, NEUTRAL, LOW, CRITICAL), tendência (UP, DOWN, STABLE) e variação da última
   * semana, papel no depth chart (STARTER, ROTATION, BACKUP, RESERVE, SPECIALIST), fatia de snaps esperada, a do último jogo e a da
   * temporada (nulas para especialistas e para quem está fora do depth chart), contrato, pedido de troca, modificador de desempenho
   * (calculado, fora do motor neste bloco) e os fatores da última semana.
   */
  public record PlayerMoraleDto(UUID playerId, String name, String position, int jerseyNumber, String rosterStatus, int morale, String label, String trend,
                                double lastDelta, String role, Double expectedSnapShare, Double lastSnapShare, Double seasonSnapShare, boolean contractFinalYear,
                                int contractYearsLeft, boolean tradeRequest, double performanceModifier, Map<String, Double> factors) {}

  /**
   * Moral do elenco (os 53): média, faixa, tendência e variação média da última semana, distribuição por faixa, pedidos de troca,
   * sequência e campanha do time, até a semana {@code throughWeek} (a última encerrada).
   */
  public record SquadMoraleDto(UUID seasonId, int throughWeek, int average, String label, String trend, double lastDelta, Map<String, Integer> distribution,
                               int tradeRequests, String teamStreak, CareerDtos.RecordDto record, List<PlayerMoraleDto> players) {}

  /** Resumo para a central (RF40). */
  public record MoraleSummaryDto(int average, String label, String trend, int low, int tradeRequests) {}
}
