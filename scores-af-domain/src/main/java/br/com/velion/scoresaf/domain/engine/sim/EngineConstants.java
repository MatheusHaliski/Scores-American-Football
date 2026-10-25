package br.com.velion.scoresaf.domain.engine.sim;

/** Constantes do motor do futebol americano (calibradas pelo teste de plausibilidade, docs/01 §3.5). */
final class EngineConstants {
  private EngineConstants() {}

  /** Versão do motor gravada no snapshot e no resultado: muda quando a mesma semente passa a gerar outro jogo. */
  static final int ENGINE_VERSION = 1;

  /** Energia abaixo da qual o titular de RB, linha defensiva e recebedores dá lugar ao reserva. */
  static final double ROTATION_ENERGY = 70;
  /** Recuperação por jogada fora de campo. */
  static final double REST_PER_PLAY = 2.0;
  static final double REST_QUARTER = 8;
  static final double REST_HALF = 30;

  /**
   * Pontos de nota que valem uma "unidade" de vantagem num confronto: maior = jogo mais equilibrado entre equipes desiguais.
   * Calibrado para a mais forte do mundo semente (overall médio ~83 nos 22 melhores) vencer a mais fraca (~76) em ~90 % dos jogos.
   */
  static final double EDGE_SCALE = 17.0;

  /** Vantagem de mando em pontos de atributo. */
  static final double HOME_BONUS = 1.0;

  /** Distância do snap do FG até a linha do gol: 7 jardas do snap + 10 da end zone. */
  static final int FG_EXTRA_YARDS = 17;
  /** Touchback de punt (o de kickoff vem das regras). */
  static final int PUNT_TOUCHBACK_YARD_LINE = 20;
  /** Linha do free kick depois do safety. */
  static final int SAFETY_KICK_YARD_LINE = 20;
  /** Segundos finais do 2º e do 4º quarto em que a bola fora de campo para o relógio (profissional). */
  static final int OUT_OF_BOUNDS_STOP_Q2 = 120;
  static final int OUT_OF_BOUNDS_STOP_Q4 = 300;
  static final int TWO_MINUTES = 120;
  /** Teto de lances por partida: proteção contra laço infinito (uma partida real tem ~200 lances com os administrativos). */
  static final int MAX_EVENTS = 1200;
}
