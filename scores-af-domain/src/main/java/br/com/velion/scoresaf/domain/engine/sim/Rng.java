package br.com.velion.scoresaf.domain.engine.sim;

import java.util.SplittableRandom;

/**
 * Geradores determinísticos do motor: cada jogada ({@code cursor}) tem fluxos independentes por finalidade ({@code stream}) —
 * pré-snap, chamada da IA e resolução — derivados com o finalizador SplitMix64. Assim a escolha do manager não desloca os números
 * da resolução, e retomar de qualquer snapshot (que guarda só o cursor) reproduz a partida bit a bit.
 */
final class Rng {
  static final int PRE_SNAP = 1;
  static final int CALL = 2;
  static final int PLAY = 3;
  static final int COIN = 4;
  static final int CALL_DEF = 5;

  private Rng() {}

  static long mix(long seed, long index) {
    long z = seed ^ (index * 0x9E3779B97F4A7C15L);
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }

  static SplittableRandom of(long seed, long cursor, int stream) {
    return new SplittableRandom(mix(mix(seed, cursor), stream));
  }

  static double gaussian(SplittableRandom r, double mean, double sd, double min, double max) {
    return Math.max(min, Math.min(max, mean + sd * r.nextGaussian()));
  }

  /** Exponencial de média {@code mean}. */
  static double exponential(SplittableRandom r, double mean) {
    return -mean * Math.log(1.0 - r.nextDouble());
  }

  static boolean chance(SplittableRandom r, double p) { return r.nextDouble() < p; }

  /** Sorteio proporcional aos pesos (negativos contam 0; soma 0 devolve o primeiro). */
  static int pick(SplittableRandom r, double... weights) {
    double sum = 0;
    for (double w : weights) sum += Math.max(0, w);
    if (sum <= 0) return 0;
    double x = r.nextDouble() * sum;
    for (int i = 0; i < weights.length; i++) {
      x -= Math.max(0, weights[i]);
      if (x < 0) return i;
    }
    return weights.length - 1;
  }

  static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
