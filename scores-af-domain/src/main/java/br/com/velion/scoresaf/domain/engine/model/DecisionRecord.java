package br.com.velion.scoresaf.domain.engine.model;

import java.util.UUID;

/**
 * Entrada do log de decisões ({@code MatchSession.decisionsLog}). Reaplicar o log na mesma semente reproduz o jogo. Para
 * {@code TIMEOUT} e {@code PLAN_CHANGE}, {@code decisionId} é {@code null} e {@code atSeq} é o seq do lance anterior; o plano
 * ajustado não cabe no log e fica no setup do snapshot (D-60).
 */
public record DecisionRecord(String decisionId, DecisionSituation situation, UUID clubId, String optionCode, boolean auto, int atSeq) {
  public static final String TIMEOUT = "TIMEOUT";
  /** Ajuste do plano de jogo ou do playbook no intervalo ou entre quartos (RF29); o plano novo fica no setup do snapshot. */
  public static final String PLAN_CHANGE = "PLAN_CHANGE";
}
