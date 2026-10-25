package br.com.velion.scoresaf.domain.account;

/**
 * Quanto o manager chama as jogadas durante a partida (RF10, RF62; docs/01 §3.5, Q-2). Substitui o {@code MatchSpeed} do basquete
 * (D-10). O motor decide sozinho quando o manager não chama, com tempo limite e decisão automática se o tempo acabar.
 */
public enum PlayCallingLevel {
  /** Padrão proposto (Q-2): 4ª descida, últimos 2 minutos de cada metade e prorrogação. */
  KEY_MOMENTS,
  /** Modo opcional, mais longo: toda jogada passa pelo manager. */
  ALL_PLAYS,
  /** Só o plano de jogo e os ajustes entre quartos; nenhuma jogada é chamada. */
  NONE
}
