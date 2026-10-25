package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.PlaybackPlan;

/**
 * Ritmo de exibição da partida (RNF2, base). O motor calcula a jogada na hora; o cliente exibe no ritmo que cabe no tempo que o
 * manager escolheu por quarto simulado ({@code simQuarterMinutes}, 1–15; 0 = instantâneo). Orçamento = 4 × minutos × 60 s; ritmo =
 * orçamento ÷ jogadas, entre 400 ms e 6 s por jogada (com o teto, jogos longos acabam antes do orçamento — Q-15).
 */
public final class MatchTimeBudgetPolicy {
  public static final int DEFAULT_QUARTER_MINUTES = 3;
  public static final int PLAYS_ESTIMATE = 150;
  public static final int MIN_MS_PER_PLAY = 400;
  public static final int MAX_MS_PER_PLAY = 6_000;

  private MatchTimeBudgetPolicy() {}

  /** Orçamento em segundos para os 4 quartos ({@code 0} = instantâneo; fora de 0–15 usa o padrão de 3 minutos). */
  public static int budgetSeconds(int quarterDurationMinutes) {
    int q = quarterDurationMinutes < 0 || quarterDurationMinutes > 15 ? DEFAULT_QUARTER_MINUTES : quarterDurationMinutes;
    return 4 * q * 60;
  }

  /** Milissegundos por jogada para exibir {@code remainingPlays} no tempo que resta (as pausas de decisão já descontadas). */
  public static int msPerPlay(long remainingBudgetMs, int remainingPlays) {
    if (remainingBudgetMs <= 0) return MIN_MS_PER_PLAY;
    long ms = remainingBudgetMs / Math.max(1, remainingPlays);
    return (int) Math.max(MIN_MS_PER_PLAY, Math.min(MAX_MS_PER_PLAY, ms));
  }

  /** Plano de exibição: dado o tempo por quarto (1–15 min; 0 = instantâneo) e o número estimado de jogadas, o ritmo por jogada. */
  public static PlaybackPlan plan(int quarterDurationMinutes, int estimatedPlays) {
    int plays = estimatedPlays <= 0 ? PLAYS_ESTIMATE : estimatedPlays;
    if (quarterDurationMinutes == 0) return new PlaybackPlan(0, plays, 0, true);
    int budget = budgetSeconds(quarterDurationMinutes);
    return new PlaybackPlan(budget, plays, msPerPlay(budget * 1000L, plays), false);
  }
}
