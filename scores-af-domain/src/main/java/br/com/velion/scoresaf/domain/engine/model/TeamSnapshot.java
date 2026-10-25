package br.com.velion.scoresaf.domain.engine.model;

import java.util.List;
import java.util.UUID;

/**
 * Franquia congelada para a partida: jogadores ativos no dia de jogo, depth chart (explícito do RF60 ou {@code null} para derivar
 * do elenco) e plano de jogo.
 */
public record TeamSnapshot(UUID clubId, String name, String shortName, List<PlayerSnapshot> players, DepthChart depthChart, GamePlan gamePlan) {

  public TeamSnapshot {
    players = List.copyOf(players);
    if (gamePlan == null) gamePlan = GamePlan.defaults();
  }

  /** Depth chart efetivo: o explícito completado pela derivação automática. */
  public DepthChart resolvedDepthChart() { return DepthChart.resolve(depthChart, players); }

  public TeamSnapshot withDepthChart(DepthChart chart) { return new TeamSnapshot(clubId, name, shortName, players, chart, gamePlan); }

  public TeamSnapshot withGamePlan(GamePlan plan) { return new TeamSnapshot(clubId, name, shortName, players, depthChart, plan); }

  public TeamSnapshot withPlayers(List<PlayerSnapshot> roster) { return new TeamSnapshot(clubId, name, shortName, roster, depthChart, gamePlan); }
}
