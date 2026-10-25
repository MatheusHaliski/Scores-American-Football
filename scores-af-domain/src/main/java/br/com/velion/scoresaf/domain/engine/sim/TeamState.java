package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.DepthChart;
import br.com.velion.scoresaf.domain.engine.model.DepthSlot;
import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.engine.model.PlayerSnapshot;
import br.com.velion.scoresaf.domain.engine.model.TeamSnapshot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Franquia durante a partida: elenco, depth chart efetivo, placar, tempos técnicos e contadores da equipe. */
final class TeamState {
  final TeamSnapshot snap;
  final boolean home;
  final UUID clubId;
  final GamePlan plan;
  final Map<UUID, PlayerState> byId = new LinkedHashMap<>();
  final List<PlayerState> players = new ArrayList<>();
  final Map<DepthSlot, List<PlayerState>> depth = new EnumMap<>(DepthSlot.class);
  final List<Integer> quarterPoints = new ArrayList<>();
  final int[] stats = new int[TeamStat.ALL.length];
  /** Pontos de atributo do mando de campo (0 em campo neutro). */
  double homeBonus;

  TeamState(TeamSnapshot snap, boolean home) {
    this.snap = snap;
    this.home = home;
    this.clubId = snap.clubId();
    this.plan = snap.gamePlan();
    for (PlayerSnapshot p : snap.players()) {
      PlayerState ps = new PlayerState(p, this);
      byId.put(p.id(), ps);
      players.add(ps);
    }
    DepthChart chart = snap.resolvedDepthChart();
    for (DepthSlot slot : DepthSlot.values()) {
      List<PlayerState> list = new ArrayList<>();
      for (UUID id : chart.get(slot)) { PlayerState ps = byId.get(id); if (ps != null && !list.contains(ps)) list.add(ps); }
      depth.put(slot, list);
    }
  }

  int score() { return stats[TeamStat.SCORE.ordinal()]; }
  int timeoutsLeft() { return stats[TeamStat.TIMEOUTS_LEFT.ordinal()]; }
  void setTimeouts(int n) { stats[TeamStat.TIMEOUTS_LEFT.ordinal()] = n; }
  void add(TeamStat s, int v) { stats[s.ordinal()] += v; }
  int get(TeamStat s) { return stats[s.ordinal()]; }
  String name() { return snap.name(); }

  void addPoints(int quarterIndex, int points) {
    add(TeamStat.SCORE, points);
    while (quarterPoints.size() <= quarterIndex) quarterPoints.add(0);
    quarterPoints.set(quarterIndex, quarterPoints.get(quarterIndex) + points);
  }

  /** Primeiro da vaga (titular), ignorando os já escolhidos; {@code null} se a vaga não tiver ninguém disponível. */
  PlayerState first(DepthSlot slot, List<PlayerState> used) {
    for (PlayerState p : depth.get(slot)) if (!used.contains(p)) return p;
    return null;
  }

  /**
   * Escolha com rodízio por fadiga (RB, linha defensiva, recebedores): o primeiro da ordem com energia acima do limiar; se todos
   * estiverem cansados, o mais descansado.
   */
  PlayerState rotate(DepthSlot slot, List<PlayerState> used) {
    PlayerState best = null;
    int seen = 0;
    for (PlayerState p : depth.get(slot)) {
      if (used.contains(p)) continue;
      if (p.energy >= EngineConstants.ROTATION_ENERGY) return p;
      if (best == null || p.energy > best.energy) best = p;
      if (++seen >= 3) break;
    }
    return best;
  }

  /** Jogador de times especiais (K, P, LS, H, KR, PR); cai para qualquer um do elenco se a vaga estiver vazia. */
  PlayerState special(DepthSlot slot) {
    List<PlayerState> list = depth.get(slot);
    if (!list.isEmpty()) return list.get(0);
    return players.get(0);
  }

  PlayerState specialSecond(DepthSlot slot) {
    List<PlayerState> list = depth.get(slot);
    return list.size() > 1 ? list.get(1) : special(slot);
  }
}
