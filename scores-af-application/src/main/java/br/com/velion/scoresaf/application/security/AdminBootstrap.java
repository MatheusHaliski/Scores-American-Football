package br.com.velion.scoresaf.application.security;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.domain.account.AccountOrigin;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.domain.account.UserStatus;
import br.com.velion.scoresaf.domain.common.Hashing;
import br.com.velion.scoresaf.domain.common.LogSafe;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin inicial seguro (bloco 3). Cria o administrador só se ainda não existe nenhum. A senha vem de um segredo
 * ({@code SCORES_ADMIN_PASSWORD}, 12+ caracteres), nunca de um valor padrão. Nunca promove uma conta existente sem a flag
 * explícita {@code SCORES_ADMIN_PROMOTE_EXISTING=true}. Sem e-mail configurado, não faz nada.
 */
@Component
public class AdminBootstrap {
  static final int MIN_PASSWORD = 12;
  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final UserAccountRepository users;
  private final PasswordHasher hasher;
  private final AuditService audit;
  private final String email;
  private final String password;
  private final boolean promoteExisting;

  public AdminBootstrap(UserAccountRepository users, PasswordHasher hasher, AuditService audit,
      @Value("${scores.admin.bootstrap-email:}") String email,
      @Value("${scores.admin.bootstrap-password:}") String password,
      @Value("${scores.admin.promote-existing:false}") boolean promoteExisting) {
    this.users = users; this.hasher = hasher; this.audit = audit;
    this.email = email; this.password = password; this.promoteExisting = promoteExisting;
  }

  public enum Outcome { SKIPPED_NO_EMAIL, SKIPPED_ADMIN_EXISTS, SKIPPED_EXISTING_ACCOUNT, SKIPPED_WEAK_PASSWORD, PROMOTED, CREATED }

  private final AtomicBoolean done = new AtomicBoolean();

  /** Roda uma vez na subida, depois que o contexto (e o Flyway) está pronto. */
  @EventListener(ContextRefreshedEvent.class)
  @Transactional
  public void onStartup() { if (done.compareAndSet(false, true)) bootstrap(); }

  @Transactional
  public Outcome bootstrap() {
    String normalized = Hashing.normalizeEmail(email);
    if (normalized == null || normalized.isBlank()) return Outcome.SKIPPED_NO_EMAIL;
    if (users.existsByRole(UserRole.ADMIN)) return Outcome.SKIPPED_ADMIN_EXISTS;
    var existing = users.findByEmailHash(Hashing.sha256Hex(normalized));
    if (existing.isPresent()) {
      if (!promoteExisting) {
        log.warn("AdminBootstrap: {} já tem conta e SCORES_ADMIN_PROMOTE_EXISTING não é true; nada foi feito.", LogSafe.maskEmail(normalized));
        return Outcome.SKIPPED_EXISTING_ACCOUNT;
      }
      UserAccount u = existing.get();
      u.setRole(UserRole.ADMIN); users.save(u);
      audit.ok(AuditActions.ADMIN_PROMOTED, "UserAccount", u.getId(), "promoção explícita (SCORES_ADMIN_PROMOTE_EXISTING)");
      return Outcome.PROMOTED;
    }
    if (password == null || password.length() < MIN_PASSWORD) {
      log.error("AdminBootstrap: SCORES_ADMIN_PASSWORD ausente ou com menos de {} caracteres; o admin não foi criado.", MIN_PASSWORD);
      return Outcome.SKIPPED_WEAK_PASSWORD;
    }
    UserAccount u = new UserAccount();
    u.setEmail(normalized);
    u.setDisplayName("Administrador");
    u.setPasswordHash(hasher.encode(password));
    u.setRole(UserRole.ADMIN);
    u.setStatus(UserStatus.ACTIVE);
    u.setAccountOrigin(AccountOrigin.SYSTEM);
    u.setDataProcessingConsentAt(Instant.now());
    users.save(u);
    audit.ok(AuditActions.ADMIN_BOOTSTRAPPED, "UserAccount", u.getId(), "admin inicial criado");
    return Outcome.CREATED;
  }
}
