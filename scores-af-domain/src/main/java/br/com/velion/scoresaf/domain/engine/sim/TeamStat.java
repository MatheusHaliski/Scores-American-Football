package br.com.velion.scoresaf.domain.engine.sim;

/** Contadores de estatística da equipe (ordem gravada no snapshot: só acrescentar no fim e subir {@code ENGINE_VERSION}). */
enum TeamStat {
  FIRST_DOWNS, FD_RUSH, FD_PASS, FD_PENALTY, THIRD_ATT, THIRD_CONV, FOURTH_ATT, FOURTH_CONV, RZ_TRIPS, RZ_TD,
  PLAYS, RUSH_ATT, RUSH_YDS, PASS_ATT, PASS_CMP, PASS_YDS_GROSS, SACKS_ALLOWED, SACK_YDS, INT_THROWN, FUMBLES, FUMBLES_LOST,
  PENALTIES, PENALTY_YDS, TOP_SECONDS, DRIVES, PUNTS, FG_MADE, FG_ATT, DEF_SACKS, TIMEOUTS_USED, TIMEOUTS_LEFT, SCORE;

  static final TeamStat[] ALL = values();
}
