package br.com.velion.scoresaf.application.usecase.rf02;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.port.out.notify.EmailPort;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.domain.account.UserStatus;
import br.com.velion.scoresaf.domain.common.Hashing;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF2 — "esqueci minha senha": resposta uniforme; token opaco de 30 min guardado só como hash no cache. */
@Service
public class ForgotPasswordUseCase {
  public static final String RESET_PREFIX = "reset:";
  private final UserAccountRepository users; private final SessionCachePort cache; private final EmailPort email; private final AuthPolicy policy; private final AuditService audit;
  public ForgotPasswordUseCase(UserAccountRepository users, SessionCachePort cache, EmailPort email, AuthPolicy policy, AuditService audit) { this.users = users; this.cache = cache; this.email = email; this.policy = policy; this.audit = audit; }

  @Transactional(readOnly = true)
  public void execute(String rawEmail, String resetUrlBase) {
    String normalized = Hashing.normalizeEmail(rawEmail);
    if (normalized == null) return;
    users.findByEmailHash(Hashing.sha256Hex(normalized)).filter(u -> u.getStatus() == UserStatus.ACTIVE).ifPresent(u -> {
      String token = TokenHashing.newToken();
      cache.put(RESET_PREFIX + TokenHashing.sha256Hex(token), u.getId(), Duration.ofMinutes(policy.resetTokenMinutes()));
      email.send(u.getEmail(), "Scores: American Football — redefinição de senha", "Para redefinir sua senha acesse: " + resetUrlBase + "?token=" + token + " (válido por " + policy.resetTokenMinutes() + " minutos).");
      audit.ok(AuditActions.PASSWORD_RESET_REQUESTED, "UserAccount", u.getId());
    });
  }
}
