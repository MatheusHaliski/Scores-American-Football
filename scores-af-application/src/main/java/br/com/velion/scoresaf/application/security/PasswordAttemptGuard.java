package br.com.velion.scoresaf.application.security;

import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.common.exception.TooManyAttemptsException;
import br.com.velion.scoresaf.application.port.out.cache.RateLimiterPort;
import br.com.velion.scoresaf.application.usecase.rf02.AuthPolicy;
import br.com.velion.scoresaf.domain.account.UserAccount;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Bloqueio por conta com reserva atômica antes do hash (bloco 3). Login: 5 tentativas em 15 min por e-mail (inclusive de
 * contas que não existem); a 6ª recebe o 401 genérico sem calcular hash, mesmo com a senha certa. Reconferência de senha
 * (troca de senha, troca de e-mail, exclusão de conta): mesmo limite por pessoa, com 429 TOO_MANY_ATTEMPTS, para uma sessão
 * roubada não testar senhas sem limite.
 */
@Component
public class PasswordAttemptGuard {
  private final RateLimiterPort limiter;
  private final PasswordHasher hasher;
  private final AuthPolicy policy;

  public PasswordAttemptGuard(RateLimiterPort limiter, PasswordHasher hasher, AuthPolicy policy) {
    this.limiter = limiter; this.hasher = hasher; this.policy = policy;
  }

  public static String loginKey(String emailHash) { return "login-fail:" + emailHash; }
  public static String stepUpKey(Object userId) { return "stepup-fail:" + userId; }

  /** @return false se o balde de login do e-mail estourou (nenhum hash deve ser calculado). */
  public boolean reserveLogin(String emailHash) {
    return limiter.tryAcquire(loginKey(emailHash), policy.maxFailedAttempts(), window());
  }

  /** Login completo com a senha certa, ou redefinição de senha por e-mail: a conta sai do bloqueio. */
  public void clearLogin(String emailHash) { limiter.reset(loginKey(emailHash)); }

  /**
   * Reconferência da senha atual. Senha errada mantém a reserva; o excedente recebe 429 com Retry-After.
   * @throws TooManyAttemptsException acima do limite (sem calcular hash)
   * @throws AuthenticationFailedException senha errada
   */
  public void verifyStepUp(UserAccount u, String currentPassword) {
    String key = stepUpKey(u.getId());
    if (!limiter.tryAcquire(key, policy.maxFailedAttempts(), window())) throw new TooManyAttemptsException(window().toSeconds());
    if (currentPassword == null || !hasher.matches(currentPassword, u.getPasswordHash())) throw new AuthenticationFailedException("Senha incorreta");
    limiter.reset(key);
  }

  public Duration window() { return Duration.ofMinutes(policy.lockMinutes()); }
}
