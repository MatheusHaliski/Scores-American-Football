package br.com.velion.scoresaf.application.usecase.rf02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.security.PasswordAttemptGuard;
import br.com.velion.scoresaf.application.security.PasswordHasher;
import br.com.velion.scoresaf.application.security.NoOpTransactionManager;
import br.com.velion.scoresaf.application.security.TestRateLimiter;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserSession;
import br.com.velion.scoresaf.domain.common.EncryptionKeyHolder;
import br.com.velion.scoresaf.domain.common.Hashing;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginUseCaseTest {
  // senhas geradas na execução: nenhum segredo fixo em teste (docs/01 §6)
  private static final String SENHA = "T" + java.util.UUID.randomUUID();
  private static final String ERRADA = "E" + java.util.UUID.randomUUID();
  private final UserAccountRepository users = mock(UserAccountRepository.class);
  private final UserSessionRepository sessions = mock(UserSessionRepository.class);
  private final SessionCachePort cache = mock(SessionCachePort.class);
  private final AuditService audit = mock(AuditService.class);
  private final PasswordEncoder encoder = new PasswordEncoder() {
    @Override public String encode(CharSequence raw) { return "h:" + raw; }
    @Override public boolean matches(CharSequence raw, String encoded) { return ("h:" + raw).equals(encoded); }
  };
  private final AuthPolicy policy = new AuthPolicy(3, 15, 7, 30);
  private final TestRateLimiter limiter = new TestRateLimiter();
  private PasswordHasher hasher;
  private LoginUseCase useCase; private UserAccount user;

  @BeforeAll static void key() { EncryptionKeyHolder.setKeyBase64(""); }

  @BeforeEach void setup() {
    hasher = new PasswordHasher(encoder, 4, 10, 2000);
    useCase = new LoginUseCase(users, sessions, cache, hasher, new PasswordAttemptGuard(limiter, hasher, policy), policy, audit, new NoOpTransactionManager());
    user = new UserAccount(); user.setEmail("Ana@Scores.app"); user.setDisplayName("Ana"); user.setPasswordHash("h:" + SENHA);
    when(users.findByEmailHash(Hashing.sha256Hex("ana@scores.app"))).thenReturn(Optional.of(user));
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(users.save(any())).thenAnswer(i -> i.getArgument(0));
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test void deve_criar_sessao_e_refresh_quando_credenciais_corretas() {
    LoginUseCase.Result r = useCase.execute(new LoginUseCase.Command("ana@scores.app", SENHA, "1.1.1.1", "ua"));
    assertThat(r.userId()).isEqualTo(user.getId());
    assertThat(r.refreshToken()).hasSizeGreaterThan(30);
    assertThat(r.refreshExpiresAt()).isAfter(Instant.now());
    verify(cache).put(eq(TokenHashing.sha256Hex(r.refreshToken())), eq(user.getId()), any());
    verify(audit).ok(eq(AuditActions.LOGIN_OK), eq("UserAccount"), eq(user.getId()), anyString());
  }
  @Test void deve_falhar_uniformemente_quando_email_desconhecido() {
    assertThatThrownBy(() -> useCase.execute(new LoginUseCase.Command("x@y.z", "qualquer", null, null))).isInstanceOf(AuthenticationFailedException.class).hasMessage("Credenciais inválidas");
  }
  @Test void deve_bloquear_depois_do_limite_sem_oraculo_nem_hash_mesmo_com_a_senha_certa() {
    for (int i = 0; i < 3; i++) assertThatThrownBy(() -> useCase.execute(new LoginUseCase.Command("ana@scores.app", ERRADA, null, null))).isInstanceOf(AuthenticationFailedException.class);
    long hashes = hasher.evaluations();
    assertThatThrownBy(() -> useCase.execute(new LoginUseCase.Command("ana@scores.app", SENHA, null, null)))
        .isInstanceOf(AuthenticationFailedException.class).hasMessage("Credenciais inválidas");
    assertThat(hasher.evaluations()).as("conta bloqueada não calcula hash").isEqualTo(hashes);
    verify(audit).failed(eq(AuditActions.LOGIN_LOCKED), eq("UserAccount"), anyString(), anyString());
  }
  @Test void senha_certa_zera_o_balde_e_a_sessao_leva_a_familia() {
    assertThatThrownBy(() -> useCase.execute(new LoginUseCase.Command("ana@scores.app", ERRADA, null, null))).isInstanceOf(AuthenticationFailedException.class);
    LoginUseCase.Result r = useCase.execute(new LoginUseCase.Command("ana@scores.app", SENHA, null, null));
    assertThat(limiter.count(PasswordAttemptGuard.loginKey(Hashing.sha256Hex("ana@scores.app")))).isZero();
    assertThat(r.sessionFamily()).isNotNull();
  }
  @Test void quarenta_pedidos_paralelos_com_senha_errada_calculam_no_maximo_o_limite_de_hashes() throws Exception {
    PasswordEncoder slow = new PasswordEncoder() {
      @Override public String encode(CharSequence raw) { return "h:" + raw; }
      @Override public boolean matches(CharSequence raw, String encoded) {
        try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return ("h:" + raw).equals(encoded);
      }
    };
    PasswordHasher slowHasher = new PasswordHasher(slow, 8, 16, 5000);
    LoginUseCase parallel = new LoginUseCase(users, sessions, cache, slowHasher, new PasswordAttemptGuard(new TestRateLimiter(), slowHasher, policy), policy, audit, new NoOpTransactionManager());
    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(40);
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
    for (int i = 0; i < 40; i++) futures.add(pool.submit(() -> {
      start.await();
      try { parallel.execute(new LoginUseCase.Command("ana@scores.app", ERRADA, null, null)); } catch (AuthenticationFailedException expected) { }
      return null;
    }));
    start.countDown();
    for (var f : futures) f.get();
    pool.shutdown();
    assertThat(slowHasher.evaluations()).isLessThanOrEqualTo(policy.maxFailedAttempts());
  }
  @Test void deve_rotacionar_refresh_e_revogar_todas_quando_reuso_detectado() {
    RefreshTokenUseCase refresh = new RefreshTokenUseCase(sessions, cache, policy, audit);
    UserSession old = new UserSession(); old.setUser(user); old.setRefreshTokenHash(TokenHashing.sha256Hex("t1")); old.setExpiresAt(Instant.now().plusSeconds(3600));
    when(sessions.findByRefreshTokenHash(TokenHashing.sha256Hex("t1"))).thenReturn(Optional.of(old));
    when(sessions.revokeIfActive(any(), any())).thenReturn(1); // o banco consumiu a sessão (UPDATE condicional)
    RefreshTokenUseCase.Result r = refresh.execute("t1", null, null);
    assertThat(old.getRevokedAt()).isNotNull(); assertThat(old.getReplacedBy()).isNotNull(); assertThat(r.refreshToken()).isNotEqualTo("t1");
    UserSession other = new UserSession(); other.setUser(user); other.setRefreshTokenHash("h2"); other.setExpiresAt(Instant.now().plusSeconds(3600));
    when(sessions.findByUserIdAndRevokedAtIsNull(user.getId())).thenReturn(List.of(other));
    assertThatThrownBy(() -> refresh.execute("t1", null, null)).isInstanceOf(AuthenticationFailedException.class);
    assertThat(other.getRevokedAt()).isNotNull();
    verify(audit).denied(eq(AuditActions.TOKEN_REUSE_DETECTED), eq("UserAccount"), eq(user.getId()), anyString());
  }

  @Test void refresh_que_perde_a_corrida_no_banco_e_tratado_como_reuso() {
    RefreshTokenUseCase refresh = new RefreshTokenUseCase(sessions, cache, policy, audit);
    UserSession s = new UserSession(); s.setUser(user); s.setRefreshTokenHash(TokenHashing.sha256Hex("t9")); s.setExpiresAt(Instant.now().plusSeconds(3600));
    when(sessions.findByRefreshTokenHash(TokenHashing.sha256Hex("t9"))).thenReturn(Optional.of(s));
    when(sessions.revokeIfActive(any(), any())).thenReturn(0); // outro refresh simultâneo consumiu a mesma sessão antes
    assertThatThrownBy(() -> refresh.execute("t9", null, null)).isInstanceOf(AuthenticationFailedException.class);
    verify(audit).denied(eq(AuditActions.TOKEN_REUSE_DETECTED), eq("UserAccount"), eq(user.getId()), anyString());
  }
}
