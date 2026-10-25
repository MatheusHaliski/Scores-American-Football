package br.com.velion.scoresaf.domain.engine.model;

/**
 * Até onde o motor avança numa chamada (RF42, {@code POST /matches/{mid}/advance}). Todos param numa decisão pendente do manager e
 * no fim do jogo.
 */
public enum AdvanceMode {
  /** Uma jogada (ou um lance administrativo: tempo técnico, aviso dos 2 minutos, fim de quarto). */
  PLAY,
  /** Até o fim do drive atual (ou do próximo, se a bola estiver num chute). */
  DRIVE,
  /** Até o fim do quarto. */
  QUARTER,
  /** Até a próxima decisão do manager. */
  TO_DECISION,
  /** Até o fim. */
  FULL
}
