package br.com.velion.scoresaf.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.common.exception.TooManyAttemptsException;
import br.com.velion.scoresaf.application.usecase.rf02.AuthPolicy;
import br.com.velion.scoresaf.domain.account.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Sessão roubada: o 6º chute da senha atual numa reconferência recebe 429, sem calcular hash. */
class PasswordAttemptGuardTest {
  // senhas geradas na execução: nenhum segredo fixo em teste (docs/01 §6)
  private static final String CERTA = "T" + java.util.UUID.randomUUID();
  private static final String CHUTE = "E" + java.util.UUID.randomUUID();
  private final PasswordEncoder encoder = new PasswordEncoder() {
    @Override public String encode(CharSequence raw) { return "h:" + raw; }
    @Override public boolean matches(CharSequence raw, String encoded) { return ("h:" + raw).equals(encoded); }
  };

  @Test
  void sexto_chute_na_reconferencia_recebe_429_sem_hash() {
    PasswordHasher hasher = new PasswordHasher(encoder, 2, 10, 2000);
    PasswordAttemptGuard guard = new PasswordAttemptGuard(new TestRateLimiter(), hasher, AuthPolicy.defaults());
    UserAccount u = new UserAccount();
    u.setPasswordHash("h:" + CERTA);
    for (int i = 0; i < 5; i++) assertThatThrownBy(() -> guard.verifyStepUp(u, CHUTE)).isInstanceOf(AuthenticationFailedException.class);
    long hashes = hasher.evaluations();
    assertThatThrownBy(() -> guard.verifyStepUp(u, CERTA)).isInstanceOf(TooManyAttemptsException.class)
        .satisfies(e -> assertThat(((TooManyAttemptsException) e).retryAfterSeconds()).isEqualTo(900));
    assertThat(hasher.evaluations()).isEqualTo(hashes);
  }

  @Test
  void senha_certa_zera_o_balde_da_reconferencia() {
    TestRateLimiter limiter = new TestRateLimiter();
    PasswordAttemptGuard guard = new PasswordAttemptGuard(limiter, new PasswordHasher(encoder, 2, 10, 2000), AuthPolicy.defaults());
    UserAccount u = new UserAccount();
    u.setPasswordHash("h:" + CERTA);
    assertThatThrownBy(() -> guard.verifyStepUp(u, CHUTE)).isInstanceOf(AuthenticationFailedException.class);
    guard.verifyStepUp(u, CERTA);
    assertThat(limiter.count(PasswordAttemptGuard.stepUpKey(u.getId()))).isZero();
  }
}
