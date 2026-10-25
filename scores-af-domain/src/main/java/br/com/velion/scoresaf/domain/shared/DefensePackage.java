package br.com.velion.scoresaf.domain.shared;

/** Pacotes defensivos (docs/01 §3.5): base 4-3 ou 3-4, nickel (5 DBs), dime (6 DBs) e goal line. */
public enum DefensePackage {
  BASE_4_3(4), BASE_3_4(4), NICKEL(5), DIME(6), GOAL_LINE(3);

  private final int defensiveBacks;
  DefensePackage(int defensiveBacks) { this.defensiveBacks = defensiveBacks; }
  /** Defensive backs em campo (CB + S). */
  public int defensiveBacks() { return defensiveBacks; }
}
