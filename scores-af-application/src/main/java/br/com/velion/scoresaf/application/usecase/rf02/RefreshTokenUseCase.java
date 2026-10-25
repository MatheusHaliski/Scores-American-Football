package br.com.velion.scoresaf.application.usecase.rf02;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserSession;
import br.com.velion.scoresaf.domain.account.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RNF4 — refresh rotativo: cada uso revoga a sessão anterior e cria outra ({@code replacedBy}). Reuso de um
 * token já rotacionado indica roubo: todas as sessões do usuário são revogadas ({@code TOKEN_REUSE_DETECTED}).
 */
@Service
public class RefreshTokenUseCase {
  public record Result(UUID userId, String displayName, List<String> roles, String refreshToken, Instant refreshExpiresAt, UUID sessionFamily) {}
  private final UserSessionRepository sessions; private final SessionCachePort cache; private final AuthPolicy policy; private final AuditService audit;
  public RefreshTokenUseCase(UserSessionRepository sessions, SessionCachePort cache, AuthPolicy policy, AuditService audit) { this.sessions = sessions; this.cache = cache; this.policy = policy; this.audit = audit; }

  @Transactional
  public Result execute(String refreshToken, String ip, String userAgent) {
    if (refreshToken == null || refreshToken.isBlank()) throw new AuthenticationFailedException();
    String hash = TokenHashing.sha256Hex(refreshToken);
    UserSession current = sessions.findByRefreshTokenHash(hash).orElseThrow(AuthenticationFailedException::new);
    Instant now = Instant.now();
    UserAccount u = current.getUser();
    if (current.getRevokedAt() != null) {
      for (UserSession s : sessions.findByUserIdAndRevokedAtIsNull(u.getId())) { s.setRevokedAt(now); sessions.save(s); cache.evict(s.getRefreshTokenHash()); }
      audit.denied(AuditActions.TOKEN_REUSE_DETECTED, "UserAccount", u.getId(), "sessão " + current.getId());
      throw new AuthenticationFailedException("Sessão inválida. Entre novamente.");
    }
    if (!current.isActive(now) || u.getStatus() != UserStatus.ACTIVE) throw new AuthenticationFailedException();
    String token = TokenHashing.newToken();
    UserSession next = new UserSession(); next.setUser(u); next.setRefreshTokenHash(TokenHashing.sha256Hex(token)); next.setIp(ip); next.setUserAgent(userAgent);
    next.setFamilyId(current.family()); next.setExpiresAt(now.plus(Duration.ofDays(policy.refreshTtlDays()))); sessions.save(next);
    current.setRevokedAt(now); current.setReplacedBy(next.getId()); sessions.save(current);
    cache.evict(hash); cache.put(next.getRefreshTokenHash(), u.getId(), Duration.ofDays(policy.refreshTtlDays()));
    audit.ok(AuditActions.TOKEN_REFRESHED, "UserAccount", u.getId(), "sessão " + current.getId() + " → " + next.getId());
    return new Result(u.getId(), u.getDisplayName(), List.of(u.getRole().name()), token, next.getExpiresAt(), next.family());
  }
}
