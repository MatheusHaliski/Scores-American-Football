package br.com.velion.scoresaf.domain.game;

/** Conjunto de regras da liga (docs/01 §3.1, D-4). O padrão até a resposta da Q-1 é {@link #PRO}. */
public enum Ruleset {
  /** Futebol americano profissional (liga fictícia, sem marca real). */
  PRO,
  /** Variante universitária: relógio para na primeira descida, prorrogação com posses alternadas da linha de 25. */
  COLLEGE
}
