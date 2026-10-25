package br.com.velion.scoresaf.application.security;

import br.com.velion.scoresaf.application.port.out.cache.RateLimiterPort;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Limitador atômico em memória para testes da camada de aplicação (sem janela de tempo). */
public class TestRateLimiter implements RateLimiterPort {
  private final Map<String, Integer> counts = new ConcurrentHashMap<>();

  @Override public boolean tryAcquire(String key, int maxPerWindow, Duration window) { return counts.merge(key, 1, Integer::sum) <= maxPerWindow; }
  @Override public void release(String key) { counts.computeIfPresent(key, (k, v) -> v <= 1 ? null : v - 1); }
  @Override public void reset(String key) { counts.remove(key); }
  public int count(String key) { return counts.getOrDefault(key, 0); }
}
