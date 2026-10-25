package br.com.velion.scoresaf.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.velion.scoresaf.application.common.exception.AuthBusyException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordHasherTest {
  @Test
  void permits_seguem_nucleos_e_metade_do_pool() {
    assertThat(PasswordHasher.defaultPermits(8, 10)).isEqualTo(5);
    assertThat(PasswordHasher.defaultPermits(1, 10)).isEqualTo(2);
    assertThat(PasswordHasher.defaultPermits(16, 2)).isEqualTo(1);
    assertThat(PasswordHasher.defaultPermits(4, 40)).isEqualTo(4);
  }

  @Test
  void acima_do_teto_responde_ocupado_em_vez_de_esperar_para_sempre() throws Exception {
    CountDownLatch inside = new CountDownLatch(1), release = new CountDownLatch(1);
    PasswordEncoder blocking = new PasswordEncoder() {
      @Override public String encode(CharSequence raw) { return "h"; }
      @Override public boolean matches(CharSequence raw, String encoded) {
        inside.countDown();
        try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return true;
      }
    };
    PasswordHasher hasher = new PasswordHasher(blocking, 1, 10, 50);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    pool.submit(() -> hasher.matches("a", "h"));
    assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
    assertThatThrownBy(() -> hasher.matches("b", "h")).isInstanceOf(AuthBusyException.class);
    release.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    assertThat(hasher.matches("c", "h")).isTrue();
  }
}
