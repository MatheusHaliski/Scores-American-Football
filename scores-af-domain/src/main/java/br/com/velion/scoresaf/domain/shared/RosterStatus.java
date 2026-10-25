package br.com.velion.scoresaf.domain.shared;

/**
 * Situação do jogador no elenco (docs/01 §3.2). ACTIVE e INACTIVE contam no limite de {@code LeagueRules.rosterMax} (53);
 * PRACTICE_SQUAD conta em {@code practiceSquadMax} (16); IR e PUP ficam fora dos dois; FREE_AGENT e WAIVED não têm clube.
 * PRACTICE_SQUAD_WAIVERS (Q-23, D-78): saiu dos 53 rumo ao practice squad e está exposto a waivers até a semana avançar; continua com
 * a franquia e o contrato, mas não conta nos 53 nem no practice squad (a vaga do practice squad fica reservada).
 */
public enum RosterStatus {
  ACTIVE, INACTIVE, PRACTICE_SQUAD, IR, PUP, FREE_AGENT, WAIVED, PRACTICE_SQUAD_WAIVERS;

  /** Conta no elenco principal (53). */
  public boolean countsTowardRoster() { return this == ACTIVE || this == INACTIVE; }

  /** Exige clube (agente livre e dispensado não têm). */
  public boolean requiresClub() { return this != FREE_AGENT && this != WAIVED; }
}
