package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.shared.Position;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ordem de entrada por vaga (titular primeiro). Neste bloco o motor deriva o depth chart do elenco ({@link #derive}); o editor do
 * RF60 (bloco 6) vai mandar um explícito em {@link TeamSnapshot#depthChart()}, e {@link #resolve} completa com a derivação as vagas
 * que ele deixar vazias, de modo que o motor sempre recebe todas as vagas.
 */
public record DepthChart(Map<DepthSlot, List<UUID>> slots) {

  public DepthChart {
    Map<DepthSlot, List<UUID>> copy = new EnumMap<>(DepthSlot.class);
    if (slots != null) slots.forEach((k, v) -> { if (k != null && v != null) copy.put(k, List.copyOf(v)); });
    slots = Collections.unmodifiableMap(copy);
  }

  public List<UUID> get(DepthSlot slot) { return slots.getOrDefault(slot, List.of()); }

  /**
   * Depth chart automático: em cada vaga de posição, primeiro os jogadores daquela posição natural, depois os que a têm como
   * secundária, depois os do mesmo grupo (ex.: qualquer OL para LT) — cada bloco pelo overall na posição, do maior para o menor
   * (empate: camisa, depois id, para ser determinístico). Retornadores: RB, WR e CB pelo retorno de chute e velocidade. Holder: o
   * punter, depois os QBs reservas.
   */
  public static DepthChart derive(List<PlayerSnapshot> players) {
    Map<DepthSlot, List<UUID>> out = new EnumMap<>(DepthSlot.class);
    for (DepthSlot slot : DepthSlot.values()) {
      if (slot.position() == null) continue;
      Position pos = slot.position();
      Set<UUID> order = new LinkedHashSet<>();
      add(order, players.stream().filter(p -> p.position() == pos).toList(), pos);
      add(order, players.stream().filter(p -> p.secondaryPosition() == pos).toList(), pos);
      add(order, players.stream().filter(p -> p.position() != null && p.position().group() == pos.group()).toList(), pos);
      out.put(slot, List.copyOf(order));
    }
    Comparator<PlayerSnapshot> returner = Comparator.comparingInt((PlayerSnapshot p) ->
        -(2 * p.attr(AttributeKey.KICK_RETURN) + p.attr(AttributeKey.SPEED) + p.attr(AttributeKey.ACCELERATION) + p.attr(AttributeKey.BALL_SECURITY)))
        .thenComparingInt(PlayerSnapshot::jerseyNumber).thenComparing(PlayerSnapshot::id);
    List<UUID> ret = players.stream().filter(p -> p.position() == Position.RB || p.position() == Position.WR || p.position() == Position.CB)
        .sorted(returner).map(PlayerSnapshot::id).limit(4).toList();
    out.put(DepthSlot.KR, ret);
    out.put(DepthSlot.PR, ret);
    List<UUID> holders = new ArrayList<>(out.getOrDefault(DepthSlot.P, List.of()));
    List<UUID> qbs = out.getOrDefault(DepthSlot.QB, List.of());
    if (qbs.size() > 1) holders.addAll(qbs.subList(1, qbs.size()));
    out.put(DepthSlot.H, List.copyOf(new LinkedHashSet<>(holders)));
    return new DepthChart(out);
  }

  /** O explícito (quando houver) vale vaga a vaga; vaga ausente ou vazia vem da derivação. Ids fora do elenco são ignorados. */
  public static DepthChart resolve(DepthChart explicit, List<PlayerSnapshot> players) {
    DepthChart derived = derive(players);
    if (explicit == null) return derived;
    Set<UUID> roster = new LinkedHashSet<>();
    for (PlayerSnapshot p : players) roster.add(p.id());
    Map<DepthSlot, List<UUID>> out = new EnumMap<>(DepthSlot.class);
    for (DepthSlot slot : DepthSlot.values()) {
      List<UUID> chosen = explicit.get(slot).stream().filter(roster::contains).distinct().toList();
      out.put(slot, chosen.isEmpty() ? derived.get(slot) : chosen);
    }
    return new DepthChart(out);
  }

  private static void add(Set<UUID> order, List<PlayerSnapshot> candidates, Position pos) {
    candidates.stream()
        .sorted(Comparator.comparingInt((PlayerSnapshot p) -> -p.overallAt(pos)).thenComparingInt(PlayerSnapshot::jerseyNumber).thenComparing(PlayerSnapshot::id))
        .forEach(p -> order.add(p.id()));
  }
}
