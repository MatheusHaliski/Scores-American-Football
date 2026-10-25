package br.com.velion.scoresaf.domain.shared;

/**
 * Formato da prorrogação (docs/01 §3.1). PRO: período cronometrado em que as duas equipes têm a posse ao menos uma vez, salvo TD
 * na primeira posse; depois, quem pontuar primeiro vence. COLLEGE: posses alternadas a partir da linha de 25, sem relógio.
 */
public enum OvertimeFormat { BOTH_POSSESS_THEN_SUDDEN_DEATH, ALTERNATING_POSSESSIONS }
