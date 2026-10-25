package br.com.velion.scoresaf.domain.engine.model;

/**
 * Ritmo de exibição da partida (RNF2): orçamento de tempo, jogadas estimadas e milissegundos por jogada. {@code instant} quando o
 * manager escolheu simulação instantânea ({@code simQuarterMinutes = 0}).
 */
public record PlaybackPlan(int timeBudgetSeconds, int playsEstimate, int msPerPlay, boolean instant) {}
