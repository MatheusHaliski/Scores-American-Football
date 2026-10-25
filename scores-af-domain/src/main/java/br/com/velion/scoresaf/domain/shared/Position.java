package br.com.velion.scoresaf.domain.shared;

/**
 * Posições do futebol americano (docs/01 §3.3). Cada uma tem família (macros do card), unidade, grupo (agrupa as posições que se
 * substituem entre si: linha ofensiva, linha defensiva, linebackers, safeties) e a faixa de altura e peso aceita pelo editor
 * (RF5). Retornadores (KR/PR) são papel no depth chart, não posição.
 */
public enum Position {
  QB(PositionFamily.QUARTERBACK, Unit.OFFENSE, PositionGroup.QB, 180, 203, 86, 118),
  RB(PositionFamily.SKILL, Unit.OFFENSE, PositionGroup.RB, 165, 193, 77, 118),
  FB(PositionFamily.SKILL, Unit.OFFENSE, PositionGroup.FB, 175, 196, 100, 122),
  WR(PositionFamily.SKILL, Unit.OFFENSE, PositionGroup.WR, 165, 203, 73, 109),
  TE(PositionFamily.SKILL, Unit.OFFENSE, PositionGroup.TE, 185, 206, 104, 125),
  LT(PositionFamily.OFFENSIVE_LINE, Unit.OFFENSE, PositionGroup.OL, 185, 206, 125, 163),
  LG(PositionFamily.OFFENSIVE_LINE, Unit.OFFENSE, PositionGroup.OL, 185, 206, 125, 163),
  C(PositionFamily.OFFENSIVE_LINE, Unit.OFFENSE, PositionGroup.OL, 183, 203, 125, 159),
  RG(PositionFamily.OFFENSIVE_LINE, Unit.OFFENSE, PositionGroup.OL, 185, 206, 125, 163),
  RT(PositionFamily.OFFENSIVE_LINE, Unit.OFFENSE, PositionGroup.OL, 185, 206, 125, 163),
  DE(PositionFamily.DEFENSE, Unit.DEFENSE, PositionGroup.DL, 185, 206, 109, 136),
  DT(PositionFamily.DEFENSE, Unit.DEFENSE, PositionGroup.DL, 180, 203, 127, 159),
  OLB(PositionFamily.DEFENSE, Unit.DEFENSE, PositionGroup.LB, 180, 198, 100, 122),
  MLB(PositionFamily.DEFENSE, Unit.DEFENSE, PositionGroup.LB, 178, 196, 104, 122),
  CB(PositionFamily.DEFENSE, Unit.DEFENSE, PositionGroup.CB, 165, 193, 77, 100),
  FS(PositionFamily.DEFENSE, Unit.DEFENSE, PositionGroup.S, 170, 196, 84, 104),
  SS(PositionFamily.DEFENSE, Unit.DEFENSE, PositionGroup.S, 170, 196, 86, 104),
  K(PositionFamily.KICKER, Unit.SPECIAL_TEAMS, PositionGroup.K, 170, 196, 75, 104),
  P(PositionFamily.KICKER, Unit.SPECIAL_TEAMS, PositionGroup.P, 175, 198, 80, 109),
  LS(PositionFamily.OFFENSIVE_LINE, Unit.SPECIAL_TEAMS, PositionGroup.LS, 178, 198, 100, 122);

  private final PositionFamily family;
  private final Unit unit;
  private final PositionGroup group;
  private final int minHeightCm;
  private final int maxHeightCm;
  private final int minWeightKg;
  private final int maxWeightKg;

  Position(PositionFamily family, Unit unit, PositionGroup group, int minHeightCm, int maxHeightCm, int minWeightKg, int maxWeightKg) {
    this.family = family; this.unit = unit; this.group = group;
    this.minHeightCm = minHeightCm; this.maxHeightCm = maxHeightCm; this.minWeightKg = minWeightKg; this.maxWeightKg = maxWeightKg;
  }

  public PositionFamily family() { return family; }
  public Unit unit() { return unit; }
  public PositionGroup group() { return group; }
  public int minHeightCm() { return minHeightCm; }
  public int maxHeightCm() { return maxHeightCm; }
  public int minWeightKg() { return minWeightKg; }
  public int maxWeightKg() { return maxWeightKg; }

  public boolean acceptsHeight(int cm) { return cm >= minHeightCm && cm <= maxHeightCm; }
  public boolean acceptsWeight(int kg) { return kg >= minWeightKg && kg <= maxWeightKg; }

  /** Converte o texto do editor ({@code "qb"}, {@code " LT "}); desconhecido devolve {@code null}. */
  public static Position parse(String raw) {
    if (raw == null) return null;
    String key = raw.trim().toUpperCase(java.util.Locale.ROOT);
    for (Position p : values()) if (p.name().equals(key)) return p;
    return null;
  }
}
