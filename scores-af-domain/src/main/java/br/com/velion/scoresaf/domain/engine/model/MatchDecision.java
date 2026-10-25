package br.com.velion.scoresaf.domain.engine.model;

import java.util.UUID;

/** Entrada do manager (ou da aplicação, quando o prazo vence) para o motor. */
public sealed interface MatchDecision permits MatchDecision.PlayCall, MatchDecision.Auto, MatchDecision.Timeout {

  /** Escolha de uma das opções da decisão aberta. */
  record PlayCall(String decisionId, String optionCode) implements MatchDecision {}

  /** Prazo vencido: a IA decide ({@code auto = true} no log). */
  record Auto(String decisionId) implements MatchDecision {}

  /** Tempo técnico pedido pela franquia (com a bola morta; máx. por tempo conforme as regras). */
  record Timeout(UUID clubId) implements MatchDecision {}
}
