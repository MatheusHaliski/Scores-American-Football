package br.com.velion.scoresaf.application.usecase.rf02;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.security.PasswordAttemptGuard;
import br.com.velion.scoresaf.application.security.PasswordHasher;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserSession;
import br.com.velion.scoresaf.domain.common.Hashing;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RF2 / HU-RF2 — login sem oráculo (bloco 3 de segurança). Conta inexistente, senha errada, conta bloqueada (mesmo com a
 * senha certa) e conta indisponível recebem o mesmo 401 genérico. Ordem: reserva atômica no balde do e-mail
 * ({@link PasswordAttemptGuard}) → hash ({@link PasswordHasher}, com teto de simultâneos) → senha certa zera o balde.
 * 40 pedidos paralelos com senha errada calculam no máximo 5 hashes. Refresh token opaco rotativo em {@code user_session}.
 *
 * <p>Sem {@code @Transactional} no método: o hash roda <b>fora</b> de transação. Antes, cada login segurava uma conexão do
 * banco durante o hash e a auditoria ({@code REQUIRES_NEW}) pedia uma segunda; 60 logins paralelos esgotavam o pool e viravam
 * HTTP 500. Agora: leitura curta → hash sem conexão → escrita curta da sessão.
 */
@Service
public class LoginUseCase {
  public record Command(String email, String password, String ip, String userAgent) {}
  public record Result(UUID userId, String displayName, List<String> roles, String refreshToken, Instant refreshExpiresAt, UUID sessionFamily) {}

  private final UserAccountRepository users; private final UserSessionRepository sessions; private final SessionCachePort sessionCache;
  private final PasswordHasher hasher; private final PasswordAttemptGuard attempts; private final AuthPolicy policy; private final AuditService audit;
  private final TransactionTemplate readTx; private final TransactionTemplate writeTx;

  public LoginUseCase(UserAccountRepository users, UserSessionRepository sessions, SessionCachePort sessionCache, PasswordHasher hasher,
      PasswordAttemptGuard attempts, AuthPolicy policy, AuditService audit, PlatformTransactionManager txManager) {
    this.users = users; this.sessions = sessions; this.sessionCache = sessionCache; this.hasher = hasher; this.attempts = attempts;
    this.policy = policy; this.audit = audit;
    this.readTx = new TransactionTemplate(txManager);
    this.readTx.setReadOnly(true);
    this.writeTx = new TransactionTemplate(txManager);
  }

  public Result execute(Command c) {
    String email = Hashing.normalizeEmail(c.email());
    String emailHash = Hashing.sha256Hex(email == null ? "" : email);
    if (!attempts.reserveLogin(emailHash)) {
      audit.failed(AuditActions.LOGIN_LOCKED, "UserAccount", "email:" + emailHash.substring(0, 12), "bloqueio por tentativas");
      throw new AuthenticationFailedException();
    }
    Optional<UserAccount> found = email == null ? Optional.empty() : readTx.execute(st -> users.findByEmailHash(emailHash));
    if (found.isEmpty()) {
      hasher.spendDummy(c.password());
      audit.failed(AuditActions.LOGIN_FAILED, "UserAccount", "unknown", "e-mail desconhecido");
      throw new AuthenticationFailedException();
    }
    UserAccount u = found.get();
    if (c.password() == null || !hasher.matches(c.password(), u.getPasswordHash())) {
      audit.failed(AuditActions.LOGIN_FAILED, "UserAccount", u.getId(), "senha incorreta");
      throw new AuthenticationFailedException();
    }
    attempts.clearLogin(emailHash);
    if (!u.isUsable()) {
      audit.failed(AuditActions.LOGIN_FAILED, "UserAccount", u.getId(), "status " + u.getStatus());
      throw new AuthenticationFailedException();
    }
    String rehash = hasher.needsRehash(u.getPasswordHash()) ? hasher.encode(c.password()) : null;
    Result r = writeTx.execute(st -> openSession(u.getId(), rehash, c));
    // auditoria depois da transação: dentro dela, o REQUIRES_NEW pediria uma segunda conexão com a primeira presa
    audit.ok(AuditActions.LOGIN_OK, "UserAccount", r.userId(), "sessão " + r.sessionFamily());
    return r;
  }

  private Result openSession(UUID userId, String rehash, Command c) {
    Instant now = Instant.now();
    UserAccount u = users.findById(userId).orElseThrow(AuthenticationFailedException::new);
    if (rehash != null) u.setPasswordHash(rehash);
    u.setFailedLoginAttempts(0); u.setLockedUntil(null); u.setLastLoginAt(now); users.save(u);
    String token = TokenHashing.newToken();
    UserSession s = new UserSession(); s.setUser(u); s.setRefreshTokenHash(TokenHashing.sha256Hex(token)); s.setIp(c.ip()); s.setUserAgent(c.userAgent());
    s.setFamilyId(s.getId()); s.setExpiresAt(now.plus(Duration.ofDays(policy.refreshTtlDays()))); sessions.save(s);
    sessionCache.put(s.getRefreshTokenHash(), u.getId(), Duration.ofDays(policy.refreshTtlDays()));
    return new Result(u.getId(), u.getDisplayName(), List.of(u.getRole().name()), token, s.getExpiresAt(), s.family());
  }
}
