package br.com.velion.scoresaf.application.usecase.rf02;

import br.com.velion.scoresaf.application.security.PasswordAttemptGuard;
import br.com.velion.scoresaf.application.security.PasswordHasher;
import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserSession;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** RF2 / HU-RF3 CA33 — redefinir senha encerra todas as sessões. */
@Service
public class ResetPasswordUseCase {
  private final UserAccountRepository users; private final UserSessionRepository sessions; private final SessionCachePort cache; private final PasswordHasher hasher; private final PasswordAttemptGuard attempts; private final AuditService audit;
  public ResetPasswordUseCase(UserAccountRepository users, UserSessionRepository sessions, SessionCachePort cache, PasswordHasher hasher, PasswordAttemptGuard attempts, AuditService audit,
      PlatformTransactionManager txManager) { this.users = users; this.sessions = sessions; this.cache = cache; this.hasher = hasher; this.attempts = attempts; this.audit = audit; this.tx = new TransactionTemplate(txManager); }
  private final TransactionTemplate tx;

  /** O hash da senha nova roda antes da transação (bloco 3): não segura conexão do banco durante o Argon2id. */
  public void execute(String token, String newPassword) {
    if (newPassword == null || newPassword.length() < 8) throw new BusinessRuleException("WEAK_PASSWORD", "A senha deve ter pelo menos 8 caracteres");
    String key = ForgotPasswordUseCase.RESET_PREFIX + TokenHashing.sha256Hex(token == null ? "" : token);
    UUID userId = cache.get(key).orElseThrow(() -> new AuthenticationFailedException("Link inválido ou expirado"));
    String newHash = hasher.encode(newPassword);
    String emailHash = tx.execute(st -> {
      UserAccount u = users.findById(userId).orElseThrow(() -> new AuthenticationFailedException("Link inválido ou expirado"));
      u.setPasswordHash(newHash); u.setFailedLoginAttempts(0); u.setLockedUntil(null); users.save(u);
      Instant now = Instant.now();
      for (UserSession s : sessions.findByUserIdAndRevokedAtIsNull(userId)) { s.setRevokedAt(now); sessions.save(s); cache.evict(s.getRefreshTokenHash()); }
      return u.getEmailHash();
    });
    attempts.clearLogin(emailHash); // a redefinição por e-mail tira a conta do bloqueio
    cache.evict(key);
    audit.ok(AuditActions.PASSWORD_CHANGED, "UserAccount", userId, "via redefinição; sessões encerradas");
  }
}
