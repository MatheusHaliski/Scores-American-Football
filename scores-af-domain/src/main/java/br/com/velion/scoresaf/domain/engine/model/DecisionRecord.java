package br.com.velion.scoresaf.domain.engine.model;

import java.util.UUID;

/**
 * Entrada do log de decisões ({@code MatchSession.decisionsLog}). Reaplicar o log na mesma semente reproduz o jogo. Para
 * {@code TIMEOUT}, {@code decisionId} é {@code null} e {@code atSeq} é o seq do lance anterior.
 */
public record DecisionRecord(String decisionId, DecisionSituation situation, UUID clubId, String optionCode, boolean auto, int atSeq) {
  public static final String TIMEOUT = "TIMEOUT";
}
