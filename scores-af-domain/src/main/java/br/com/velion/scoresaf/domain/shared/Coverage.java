package br.com.velion.scoresaf.domain.shared;

/** Coberturas (docs/01 §3.5): Cover 0 e 1 são individuais; 2, 3, 4 e 6 são por zona. Blitz e espião são escolhas à parte. */
public enum Coverage {
  COVER_0(true, 0), COVER_1(true, 1), COVER_2(false, 2), COVER_3(false, 3), COVER_4(false, 4), COVER_6(false, 3);

  private final boolean man;
  private final int deepDefenders;
  Coverage(boolean man, int deepDefenders) { this.man = man; this.deepDefenders = deepDefenders; }
  /** Individual (homem a homem) ou zona. */
  public boolean isMan() { return man; }
  /** Defensores no fundo do campo. */
  public int deepDefenders() { return deepDefenders; }
}
