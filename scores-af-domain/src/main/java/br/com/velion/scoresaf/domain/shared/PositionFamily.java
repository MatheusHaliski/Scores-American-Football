package br.com.velion.scoresaf.domain.shared;

import java.util.List;

/**
 * Família de posição: decide os seis macros do card (D-6, docs/01 §3.4). O long snapper fica na família da linha ofensiva (D-22):
 * os macros de chutador (potência e precisão de chute) não dizem nada sobre ele; os de bloqueio, sim.
 */
public enum PositionFamily {
  QUARTERBACK(List.of("PRE", "BRA", "LEI", "MOB", "CAL", "LID")),
  SKILL(List.of("VEL", "AGI", "MAO", "ROT", "QBT", "FOR")),
  OFFENSIVE_LINE(List.of("BLP", "BLC", "FOR", "TEC", "AGI", "CON")),
  DEFENSE(List.of("TAC", "IMP", "COB", "PRS", "VEL", "LEI")),
  KICKER(List.of("POT", "PRE", "CON"));

  private final List<String> macroCodes;

  PositionFamily(List<String> macroCodes) { this.macroCodes = macroCodes; }

  /** Códigos dos macros do card, na ordem de exibição (3 para chutadores, 6 para os demais). */
  public List<String> macroCodes() { return macroCodes; }
}
