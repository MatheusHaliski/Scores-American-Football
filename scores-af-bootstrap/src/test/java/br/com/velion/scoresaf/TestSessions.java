package br.com.velion.scoresaf;

import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.domain.account.UserSession;
import br.com.velion.scoresaf.web.security.TokenService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Conta + sessão reais para testes que precisam de um access token válido (o token exige família de sessão ativa, D-25). */
final class TestSessions {
  private TestSessions() {}

  /** Conta criada e o cabeçalho Authorization dela. */
  record Session(UserAccount user, String bearer) {}

  static String bearer(UserAccountRepository users, UserSessionRepository sessions, TokenService tokens, UserRole role) {
    return open(users, sessions, tokens, role).bearer();
  }

  static Session open(UserAccountRepository users, UserSessionRepository sessions, TokenService tokens, UserRole role) {
    UserAccount u = new UserAccount();
    u.setEmail("t-" + UUID.randomUUID() + "@scoresaf.test");
    u.setDisplayName("Teste");
    u.setPasswordHash("!sem-login");
    u.setRole(role);
    users.save(u);
    UserSession s = new UserSession();
    s.setUser(u);
    s.setRefreshTokenHash(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
    s.setExpiresAt(Instant.now().plusSeconds(3600));
    s.setFamilyId(s.getId());
    sessions.save(s);
    return new Session(u, "Bearer " + tokens.issueAccessToken(u.getId(), u.getDisplayName(), List.of(role.name()), s.family()).value());
  }
}
