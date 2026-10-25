package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.shared.Position;

/**
 * Vagas do depth chart (RF60, bloco 6): uma por posição, mais os papéis de times especiais que não são posição (retornador de
 * kickoff, retornador de punt e holder). {@link #position()} é {@code null} nos papéis.
 */
public enum DepthSlot {
  QB(Position.QB), RB(Position.RB), FB(Position.FB), WR(Position.WR), TE(Position.TE),
  LT(Position.LT), LG(Position.LG), C(Position.C), RG(Position.RG), RT(Position.RT),
  DE(Position.DE), DT(Position.DT), OLB(Position.OLB), MLB(Position.MLB), CB(Position.CB), FS(Position.FS), SS(Position.SS),
  K(Position.K), P(Position.P), LS(Position.LS),
  /** Retornador de kickoff. */
  KR(null),
  /** Retornador de punt. */
  PR(null),
  /** Holder do field goal e do ponto extra. */
  H(null);

  private final Position position;

  DepthSlot(Position position) { this.position = position; }

  public Position position() { return position; }

  public static DepthSlot of(Position p) {
    for (DepthSlot s : values()) if (s.position == p) return s;
    throw new IllegalArgumentException("posição sem vaga: " + p);
  }
}
