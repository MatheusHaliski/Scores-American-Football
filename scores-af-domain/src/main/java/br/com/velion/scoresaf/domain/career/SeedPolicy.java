package br.com.velion.scoresaf.domain.career;

import java.nio.charset.StandardCharsets;

/**
 * Sementes derivadas da semente-raiz da carreira (RF34, RF42): calendário, partidas e sorteios usam
 * {@code derive(seed, escopo, partes...)}, de modo que a mesma carreira (mesma semente) reproduz os mesmos resultados. SplitMix64:
 * puro, sem estado, igual em qualquer JVM.
 */
public final class SeedPolicy {
  private SeedPolicy() {}

  public static long mix(long z) {
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return z ^ (z >>> 31);
  }

  public static long derive(long root, String scope, long... parts) {
    long h = mix(root + 0x9E3779B97F4A7C15L);
    for (byte b : scope.getBytes(StandardCharsets.UTF_8)) h = mix(h ^ b);
    for (long p : parts) h = mix(h ^ p);
    return h;
  }
}
