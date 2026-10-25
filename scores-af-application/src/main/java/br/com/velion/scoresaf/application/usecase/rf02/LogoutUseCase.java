package br.com.velion.scoresaf.application.usecase.rf02;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF2 / HU-RF3 — logout invalida o refresh token no servidor (o access expira em 15 min). */
@Service
public class LogoutUseCase {
  private final UserSessionRepository sessions; private final SessionCachePort cache; private final AuditService audit;
  public LogoutUseCase(UserSessionRepository sessions, SessionCachePort cache, AuditService audit) { this.sessions = sessions; this.cache = cache; this.audit = audit; }
  @Transactional
  public void execute(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) return;
    String hash = TokenHashing.sha256Hex(refreshToken);
    sessions.findByRefreshTokenHash(hash).ifPresent(s -> {
      if (s.getRevokedAt() == null) { s.setRevokedAt(Instant.now()); sessions.save(s); }
      cache.evict(hash);
      audit.ok(AuditActions.LOGOUT, "UserAccount", s.getUser().getId(), "sessão " + s.getId());
    });
  }
}
