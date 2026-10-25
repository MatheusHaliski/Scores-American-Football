package br.com.velion.scoresaf.domain.match;

/**
 * Situação da partida ao vivo do usuário (RF27/RF29/RF62), derivada do snapshot do motor: antes do kickoff, rolando, parada entre
 * quartos ({@code BREAK}: fim do 1º e do 3º quarto, antes de cada prorrogação), no intervalo ({@code HALFTIME}), esperando a chamada
 * de jogada do manager ({@code AWAITING_DECISION}) e encerrada. Ajustes do playbook (RF29) só em NOT_STARTED, BREAK e HALFTIME.
 */
public enum MatchSessionStatus {
  NOT_STARTED, LIVE, BREAK, HALFTIME, AWAITING_DECISION, FINISHED;

  /** Bola parada para o playbook do intervalo (RF29). */
  public boolean adjustable() { return this == NOT_STARTED || this == BREAK || this == HALFTIME; }
}
