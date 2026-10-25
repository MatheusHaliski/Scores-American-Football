package br.com.velion.scoresaf.domain.engine.ratings;

/** Nível do card pelo overall (docs/01 §3.4): Bronze ≤ 64, Prata 65–74, Ouro 75–84, Ícone 85–89, Elite ≥ 90. */
public enum CardTier {
  BRONZE(0), SILVER(65), GOLD(75), ICON(85), ELITE(90);

  private final int minOverall;
  CardTier(int minOverall) { this.minOverall = minOverall; }

  /** Menor overall do nível. */
  public int minOverall() { return minOverall; }

  public static CardTier of(int overall) {
    CardTier tier = BRONZE;
    for (CardTier t : values()) if (overall >= t.minOverall) tier = t;
    return tier;
  }
}
