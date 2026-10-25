package br.com.velion.scoresaf.domain.shared;

/** Conceitos de jogada do ataque (docs/01 §3.5) e as jogadas de relógio (spike, kneel). */
public enum PlayConcept {
  INSIDE_RUN, OUTSIDE_RUN, SHORT_PASS, MEDIUM_PASS, DEEP_PASS, SCREEN, PLAY_ACTION, QB_SNEAK, PUNT, FIELD_GOAL, SPIKE, KNEEL;

  public boolean isPass() { return this == SHORT_PASS || this == MEDIUM_PASS || this == DEEP_PASS || this == SCREEN || this == PLAY_ACTION || this == SPIKE; }
  public boolean isKick() { return this == PUNT || this == FIELD_GOAL; }
}
