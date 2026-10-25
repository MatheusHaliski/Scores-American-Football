package br.com.velion.scoresaf.domain.shared;

/** Dificuldade da carreira: multiplica o contexto do adversário IA e a rigidez da diretoria. */
public enum Difficulty {
  EASY(0.95, 4), NORMAL(1.00, 3), HARD(1.04, 2), LEGEND(1.08, 2);
  private final double aiContextFactor; private final int dismissalPatienceRounds;
  Difficulty(double f, int r) { aiContextFactor = f; dismissalPatienceRounds = r; }
  /** Fator aplicado ao contexto ofensivo/defensivo dos times de IA contra o usuário. */
  public double aiContextFactor() { return aiContextFactor; }
  /** Rodadas consecutivas abaixo do limiar crítico antes da demissão. */
  public int dismissalPatienceRounds() { return dismissalPatienceRounds; }
}
