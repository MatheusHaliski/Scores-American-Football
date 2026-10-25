package br.com.velion.scoresaf.domain.match;

import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.engine.model.PlayerSnapshot;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Regras do depth chart salvo (RF60), puras: validação, aviso de fora de posição, depth chart padrão (a partir do automático do
 * motor, bloco 5) e conversão para o depth chart do motor.
 *
 * <ul>
 *   <li>Disponível = jogador da franquia, ACTIVE e sem suspensão (practice squad, IR, inativo e dispensado não entram).</li>
 *   <li>Erros: jogador indisponível, o mesmo jogador duas vezes na mesma vaga, vaga com menos que {@link ChartSlot#minStarters()}
 *   jogadores disponíveis, mais jogadores distintos que os ativos do dia de jogo ({@code LeagueRules.gameDayActives}).</li>
 *   <li>Aviso (não bloqueia): fora de posição = numa vaga de posição, overall ali abaixo do overall na posição natural menos
 *   {@link #OUT_OF_POSITION_MARGIN} (posição natural e secundária nunca avisam); num papel, posição natural fora das que o fazem.</li>
 * </ul>
 */
public final class DepthChartPolicy {
  public static final int OUT_OF_POSITION_MARGIN = 5;

  public static final String UNAVAILABLE = "DEPTH_CHART_PLAYER_UNAVAILABLE";
  public static final String DUPLICATE = "DEPTH_CHART_DUPLICATE_PLAYER";
  public static final String INCOMPLETE = "DEPTH_CHART_INCOMPLETE";
  public static final String TOO_MANY_ACTIVES = "DEPTH_CHART_TOO_MANY_ACTIVES";
  public static final String OUT_OF_POSITION = "OUT_OF_POSITION";

  /** Profundidade do depth chart padrão por vaga (o motor completa o resto pelo automático). */
  private static final Map<ChartSlot, Integer> DEFAULT_DEPTH = defaultDepth();

  private DepthChartPolicy() {}

  /** Problema numa vaga ({@code slot}/{@code depth}/{@code playerId} nulos quando o problema é do depth chart inteiro). */
  public record Issue(String code, ChartSlot slot, Integer depth, UUID playerId) {}

  public record Result(List<Issue> errors, List<Issue> warnings, int uniquePlayers) {
    public boolean valid() { return errors.isEmpty(); }
  }

  public static boolean available(Player p, UUID clubId) {
    return p != null && p.getClub() != null && p.getClub().getId().equals(clubId) && p.getRosterStatus() == RosterStatus.ACTIVE
        && p.getSuspensionWeeks() == 0;
  }

  public static Result validate(Map<ChartSlot, List<UUID>> chart, Map<UUID, Player> players, UUID clubId, int gameDayActives) {
    List<Issue> errors = new ArrayList<>();
    List<Issue> warnings = new ArrayList<>();
    Set<UUID> unique = new LinkedHashSet<>();
    for (ChartSlot slot : ChartSlot.values()) {
      List<UUID> ids = chart.getOrDefault(slot, List.of());
      Set<UUID> seen = new LinkedHashSet<>();
      int availableHere = 0;
      for (int i = 0; i < ids.size(); i++) {
        UUID id = ids.get(i);
        int depth = i + 1;
        if (!seen.add(id)) { errors.add(new Issue(DUPLICATE, slot, depth, id)); continue; }
        Player p = players.get(id);
        if (!available(p, clubId)) { errors.add(new Issue(UNAVAILABLE, slot, depth, id)); continue; }
        availableHere++;
        unique.add(id);
        if (outOfPosition(p, slot)) warnings.add(new Issue(OUT_OF_POSITION, slot, depth, id));
      }
      if (availableHere < slot.minStarters()) errors.add(new Issue(INCOMPLETE, slot, null, null));
    }
    if (unique.size() > gameDayActives) errors.add(new Issue(TOO_MANY_ACTIVES, null, null, null));
    return new Result(List.copyOf(errors), List.copyOf(warnings), unique.size());
  }

  public static boolean outOfPosition(Player p, ChartSlot slot) {
    if (slot.isRole()) return !slot.naturalPositions().contains(p.getPosition());
    if (p.getPosition() == slot.position() || p.getSecondaryPosition() == slot.position()) return false;
    return overallAt(p, slot) < p.ratingAt(p.getPosition()).overall() - OUT_OF_POSITION_MARGIN;
  }

  /** Overall do jogador na vaga (nos papéis, o da posição natural). */
  public static int overallAt(Player p, ChartSlot slot) {
    return p.ratingAt(slot.isRole() ? p.getPosition() : slot.position()).overall();
  }

  /**
   * Depth chart padrão de uma franquia: o automático do motor ({@link br.com.velion.scoresaf.domain.engine.model.DepthChart#derive})
   * sobre os jogadores disponíveis, cortado na profundidade padrão de cada vaga e, se ainda passar dos ativos do dia de jogo, aparado
   * nas vagas mais fundas. O back de 3ª descida é o segundo RB.
   */
  public static Map<ChartSlot, List<UUID>> defaults(List<Player> eligible, int gameDayActives) {
    List<PlayerSnapshot> snaps = eligible.stream().map(PlayerSnapshot::of).toList();
    var derived = br.com.velion.scoresaf.domain.engine.model.DepthChart.derive(snaps);
    Map<ChartSlot, List<UUID>> out = new EnumMap<>(ChartSlot.class);
    for (ChartSlot slot : ChartSlot.values()) {
      List<UUID> src;
      if (slot == ChartSlot.THIRD_DOWN_BACK) {
        List<UUID> rbs = derived.get(ChartSlot.RB.engineSlot());
        src = rbs.size() > 1 ? List.of(rbs.get(1)) : rbs;
      } else {
        src = derived.get(slot.engineSlot());
      }
      out.put(slot, new ArrayList<>(src.subList(0, Math.min(src.size(), DEFAULT_DEPTH.get(slot)))));
    }
    while (unique(out) > gameDayActives) {
      ChartSlot deepest = null;
      int slack = 0;
      for (ChartSlot s : ChartSlot.values()) {
        int extra = out.get(s).size() - Math.max(1, s.minStarters());
        if (extra > 0 && extra >= slack) { deepest = s; slack = extra; }
      }
      if (deepest == null) break;
      List<UUID> l = out.get(deepest);
      l.remove(l.size() - 1);
    }
    Map<ChartSlot, List<UUID>> frozen = new LinkedHashMap<>();
    out.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
    return frozen;
  }

  /** Depth chart do motor a partir do salvo (o back de 3ª descida ainda não tem vaga no motor). */
  public static br.com.velion.scoresaf.domain.engine.model.DepthChart toEngine(Map<ChartSlot, List<UUID>> chart) {
    Map<br.com.velion.scoresaf.domain.engine.model.DepthSlot, List<UUID>> m = new EnumMap<>(br.com.velion.scoresaf.domain.engine.model.DepthSlot.class);
    chart.forEach((slot, ids) -> { if (slot.engineSlot() != null && ids != null && !ids.isEmpty()) m.put(slot.engineSlot(), ids); });
    return new br.com.velion.scoresaf.domain.engine.model.DepthChart(m);
  }

  private static int unique(Map<ChartSlot, List<UUID>> chart) {
    Set<UUID> u = new LinkedHashSet<>();
    chart.values().forEach(u::addAll);
    return u.size();
  }

  private static Map<ChartSlot, Integer> defaultDepth() {
    Map<ChartSlot, Integer> d = new EnumMap<>(ChartSlot.class);
    d.put(ChartSlot.QB, 3); d.put(ChartSlot.RB, 3); d.put(ChartSlot.FB, 1); d.put(ChartSlot.WR, 6); d.put(ChartSlot.TE, 3);
    for (ChartSlot s : new ChartSlot[] {ChartSlot.LT, ChartSlot.LG, ChartSlot.C, ChartSlot.RG, ChartSlot.RT}) d.put(s, 2);
    d.put(ChartSlot.DE, 4); d.put(ChartSlot.DT, 4); d.put(ChartSlot.OLB, 4); d.put(ChartSlot.MLB, 2); d.put(ChartSlot.CB, 5);
    d.put(ChartSlot.FS, 2); d.put(ChartSlot.SS, 2); d.put(ChartSlot.K, 1); d.put(ChartSlot.P, 1); d.put(ChartSlot.LS, 1);
    d.put(ChartSlot.KR, 2); d.put(ChartSlot.PR, 2); d.put(ChartSlot.H, 2); d.put(ChartSlot.THIRD_DOWN_BACK, 1);
    return d;
  }
}
