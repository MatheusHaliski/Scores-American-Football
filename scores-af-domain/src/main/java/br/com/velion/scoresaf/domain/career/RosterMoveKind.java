package br.com.velion.scoresaf.domain.career;

/**
 * Tipo de movimento do elenco registrado na carreira (RF4): troca de situação (53, practice squad, IR), dispensa (waivers) e, desde
 * a Q-23 (D-78), a saída de waivers sem reivindicação no avanço da semana (WAIVER_CLEARED: o jogador entra no practice squad).
 */
public enum RosterMoveKind { STATUS_CHANGE, RELEASE, WAIVER_CLEARED }
