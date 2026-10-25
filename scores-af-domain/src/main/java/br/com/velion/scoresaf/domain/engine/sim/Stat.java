package br.com.velion.scoresaf.domain.engine.sim;

/** Contadores de estatística do jogador (ordem gravada no snapshot: só acrescentar no fim e subir {@code ENGINE_VERSION}). */
enum Stat {
  SNAPS,
  PASS_ATT, PASS_CMP, PASS_YDS, PASS_TD, PASS_INT, SACKED, SACK_YDS, PASS_LONG,
  RUSH_ATT, RUSH_YDS, RUSH_TD, RUSH_LONG, FUMBLES, FUMBLES_LOST,
  TARGETS, REC, REC_YDS, YAC, REC_TD, DROPS, REC_LONG,
  TACKLES, TFL, SACKS, DEF_INT, PASSES_DEFENDED, FORCED_FUMBLES, FUMBLE_RECOVERIES, DEF_TD,
  FG_MADE_0_39, FG_ATT_0_39, FG_MADE_40_49, FG_ATT_40_49, FG_MADE_50, FG_ATT_50, FG_LONG, PAT_MADE, PAT_ATT,
  PUNTS, PUNT_YDS, PUNT_IN20, PUNT_TB, PUNT_LONG,
  KR, KR_YDS, PR, PR_YDS, RET_TD, RET_LONG,
  TWO_POINT;

  static final Stat[] ALL = values();

  /** Contadores de "mais longo": guardam o máximo, não a soma. */
  boolean isMax() { return this == PASS_LONG || this == RUSH_LONG || this == REC_LONG || this == FG_LONG || this == PUNT_LONG || this == RET_LONG; }
}
