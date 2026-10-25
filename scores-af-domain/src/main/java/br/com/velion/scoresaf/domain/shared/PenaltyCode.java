package br.com.velion.scoresaf.domain.shared;

/**
 * Catálogo inicial de faltas (docs/01 §3.1): jardas, lado que comete, primeira descida automática, perda de descida e falta no
 * local (pass interference defensiva e intentional grounding marcam no ponto da falta; {@code yards} é o mínimo).
 */
public enum PenaltyCode {
  FALSE_START(5, Side.OFFENSE, false, false, false),
  OFFSIDE(5, Side.DEFENSE, false, false, false),
  ENCROACHMENT(5, Side.DEFENSE, false, false, false),
  HOLDING_OFFENSE(10, Side.OFFENSE, false, false, false),
  HOLDING_DEFENSE(5, Side.DEFENSE, true, false, false),
  PASS_INTERFERENCE_OFFENSE(10, Side.OFFENSE, false, false, false),
  PASS_INTERFERENCE_DEFENSE(1, Side.DEFENSE, true, false, true),
  ROUGHING_THE_PASSER(15, Side.DEFENSE, true, false, false),
  UNNECESSARY_ROUGHNESS(15, Side.EITHER, true, false, false),
  DELAY_OF_GAME(5, Side.OFFENSE, false, false, false),
  ILLEGAL_FORMATION(5, Side.OFFENSE, false, false, false),
  FACEMASK(15, Side.EITHER, true, false, false),
  INTENTIONAL_GROUNDING(10, Side.OFFENSE, false, true, true);

  /** Quem pode cometer a falta. A primeira descida automática só vale quando a defesa comete. */
  public enum Side { OFFENSE, DEFENSE, EITHER }

  private final int yards;
  private final Side side;
  private final boolean automaticFirstDown;
  private final boolean lossOfDown;
  private final boolean spotFoul;

  PenaltyCode(int yards, Side side, boolean automaticFirstDown, boolean lossOfDown, boolean spotFoul) {
    this.yards = yards; this.side = side; this.automaticFirstDown = automaticFirstDown; this.lossOfDown = lossOfDown; this.spotFoul = spotFoul;
  }

  public int yards() { return yards; }
  public Side side() { return side; }
  public boolean automaticFirstDown() { return automaticFirstDown; }
  public boolean lossOfDown() { return lossOfDown; }
  public boolean spotFoul() { return spotFoul; }
}
