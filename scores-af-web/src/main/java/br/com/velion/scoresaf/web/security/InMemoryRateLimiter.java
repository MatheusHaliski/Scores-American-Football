package br.com.velion.scoresaf.web.security;

import br.com.velion.scoresaf.application.port.out.cache.RateLimiterPort;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback em memória (janela fixa, atômico por chave). A implementação Redis substitui este bean quando
 * {@code scores.persistence.cache=redis}. Teto de {@value #MAX_BUCKETS} baldes, com varredura dos vencidos.
 */
public class InMemoryRateLimiter implements RateLimiterPort {
  static final int MAX_BUCKETS = 100_000;
  private record Window(long startMillis, long sizeMillis, int count) {
    boolean expired(long now) { return now - startMillis >= sizeMillis; }
  }
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  @Override
  public boolean tryAcquire(String key, int maxPerWindow, Duration window) {
    long now = System.currentTimeMillis();
    long size = window.toMillis();
    if (windows.size() >= MAX_BUCKETS) sweep(now);
    Window w = windows.compute(key, (k, old) -> old == null || old.expired(now) ? new Window(now, size, 1) : new Window(old.startMillis, old.sizeMillis, old.count + 1));
    return w.count <= maxPerWindow;
  }

  @Override
  public void release(String key) {
    windows.computeIfPresent(key, (k, w) -> w.count <= 1 ? null : new Window(w.startMillis, w.sizeMillis, w.count - 1));
  }

  @Override
  public void reset(String key) { windows.remove(key); }

  private void sweep(long now) { windows.entrySet().removeIf(e -> e.getValue().expired(now)); }

  int size() { return windows.size(); }
}
