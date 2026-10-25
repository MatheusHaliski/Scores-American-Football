package br.com.velion.scoresaf.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {
  @Test
  void reserva_atomica_sob_concorrencia() throws Exception {
    InMemoryRateLimiter limiter = new InMemoryRateLimiter();
    AtomicInteger granted = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(32);
    for (int i = 0; i < 400; i++) pool.submit(() -> { if (limiter.tryAcquire("k", 50, Duration.ofMinutes(1))) granted.incrementAndGet(); });
    pool.shutdown();
    assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(granted.get()).isEqualTo(50);
  }

  @Test
  void release_nunca_fica_abaixo_de_zero_e_reset_zera() {
    InMemoryRateLimiter limiter = new InMemoryRateLimiter();
    limiter.release("vazio");
    assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isTrue();
    assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isFalse();
    limiter.release("k");
    limiter.release("k");
    limiter.release("k");
    assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isTrue();
    limiter.reset("k");
    assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isTrue();
  }
}
