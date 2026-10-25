package br.com.velion.scoresaf.domain.engine.model;

/** Tipo do lance no play-by-play (RF54). Os três últimos são administrativos e não contam como jogada. */
public enum PlayType {
  COIN_TOSS, KICKOFF, ONSIDE_KICK, FREE_KICK, RUN, PASS, PUNT, FIELD_GOAL, EXTRA_POINT, TWO_POINT, KNEEL, SPIKE,
  /** Falta antes do snap: a descida é repetida. */
  NO_PLAY,
  TIMEOUT, TWO_MINUTE_WARNING, END_OF_PERIOD;

  /** Conta como jogada (snap ou chute) nas estatísticas e na faixa de ~150 jogadas por partida. */
  public boolean isPlay() {
    return this != COIN_TOSS && this != NO_PLAY && this != TIMEOUT && this != TWO_MINUTE_WARNING && this != END_OF_PERIOD;
  }

  /** Jogada de scrimmage (snap do ataque). */
  public boolean isScrimmage() { return this == RUN || this == PASS || this == KNEEL || this == SPIKE; }
}
