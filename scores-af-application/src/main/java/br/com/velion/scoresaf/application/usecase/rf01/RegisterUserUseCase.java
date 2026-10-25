package br.com.velion.scoresaf.application.usecase.rf01;

import br.com.velion.scoresaf.application.security.ReservedNames;
import br.com.velion.scoresaf.application.security.PasswordHasher;
import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSettingsRepository;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserLocale;
import br.com.velion.scoresaf.domain.account.UserSettings;
import br.com.velion.scoresaf.domain.common.Hashing;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** RF1 / HU-RF1 — cadastro com e-mail único (hash), senha Argon2id (≥ 8 caracteres) e consentimento LGPD. */
@Service
public class RegisterUserUseCase {
  public record Command(String email, String password, String displayName, String locale, boolean marketingConsent) {}
  public record Result(UUID userId) {}

  private final UserAccountRepository users; private final UserSettingsRepository settings; private final PasswordHasher hasher; private final AuditService audit;
  private final TransactionTemplate tx;
  public RegisterUserUseCase(UserAccountRepository users, UserSettingsRepository settings, PasswordHasher hasher, AuditService audit, PlatformTransactionManager txManager) {
    this.users = users; this.settings = settings; this.hasher = hasher; this.audit = audit; this.tx = new TransactionTemplate(txManager);
  }

  /** O hash roda antes da transação (bloco 3): não segura conexão do banco durante o Argon2id. */
  public Result execute(Command c) {
    String email = Hashing.normalizeEmail(c.email());
    if (email == null || !email.contains("@")) throw new BusinessRuleException("INVALID_EMAIL", "E-mail inválido");
    if (c.password() == null || c.password().length() < 8) throw new BusinessRuleException("WEAK_PASSWORD", "A senha deve ter pelo menos 8 caracteres");
    if (c.displayName() == null || c.displayName().isBlank()) throw new BusinessRuleException("INVALID_NAME", "Informe um nome");
    if (ReservedNames.isReserved(c.displayName())) throw new BusinessRuleException("NAME_RESERVED", "Este nome é reservado. Escolha outro.");
    if (users.existsByEmailHash(Hashing.sha256Hex(email))) throw new ConflictException("Já existe uma conta com este e-mail");
    String passwordHash = hasher.encode(c.password());
    UUID id = tx.execute(st -> persist(email, passwordHash, c));
    audit.ok(AuditActions.REGISTER, "UserAccount", id);
    if (c.marketingConsent()) audit.ok(AuditActions.CONSENT_GRANTED, "UserAccount", id, "marketing");
    return new Result(id);
  }

  private UUID persist(String email, String passwordHash, Command c) {
    if (users.existsByEmailHash(Hashing.sha256Hex(email))) throw new ConflictException("Já existe uma conta com este e-mail");
    UserAccount u = new UserAccount();
    u.setEmail(email);
    u.setPasswordHash(passwordHash);
    u.setDisplayName(c.displayName().trim());
    u.setLocale(parseLocale(c.locale()));
    u.setMarketingConsent(c.marketingConsent());
    u.setDataProcessingConsentAt(Instant.now());
    users.save(u);
    UserSettings s = new UserSettings(); s.setUser(u); settings.save(s);
    return u.getId();
  }
  static UserLocale parseLocale(String v) { return UserLocale.fromTag(v); }
}
