package br.com.velion.scoresaf.application.usecase.rf46;

import br.com.velion.scoresaf.application.security.PasswordAttemptGuard;
import br.com.velion.scoresaf.domain.common.LogSafe;
import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.common.i18n.LocalizedMessages;
import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.port.out.notify.EmailPort;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.usecase.rf02.TokenHashing;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.common.Hashing;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF46 CA02 — correção do e-mail (Art. 18, III) com verificação de posse do novo endereço (Art. 7º, V) e reautenticação por senha.
 * O token (32 bytes aleatórios) só existe em hash no cache, com validade de 24 h; o endereço antigo é avisado nas duas etapas.
 */
@Service
public class EmailChangeUseCases {
  static final String CACHE_PREFIX = "email-verify:";
  static final Duration TOKEN_TTL = Duration.ofHours(24);
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,255}\\.[^@\\s]{2,}$");
  private final SecureRandom random = new SecureRandom();

  private final UserAccountRepository users; private final SessionCachePort cache; private final EmailPort email;
  private final LocalizedMessages messages; private final PasswordAttemptGuard attempts;
  private final AuditService audit; private final String frontendBaseUrl;

  public EmailChangeUseCases(UserAccountRepository users, SessionCachePort cache, EmailPort email, LocalizedMessages messages,
      PasswordAttemptGuard attempts, AuditService audit,
      @Value("${scores.frontend.base-url:http://localhost:3000}") String frontendBaseUrl) {
    this.users = users; this.cache = cache; this.email = email; this.messages = messages; this.attempts = attempts; this.audit = audit;
    this.frontendBaseUrl = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
  }

  /** Retorna o token apenas para testes/integração; a API nunca o expõe (vai só por e-mail). */
  @Transactional
  public String request(UUID userId, String newEmail, String currentPassword) {
    UserAccount u = users.findById(userId).filter(UserAccount::isUsable).orElseThrow(() -> new NotFoundException("Conta", userId));
    try { attempts.verifyStepUp(u, currentPassword); }
    catch (AuthenticationFailedException e) { audit.failed(AuditActions.EMAIL_CHANGE_REQUESTED, "UserAccount", userId, "reautenticação falhou"); throw e; }
    String normalized = Hashing.normalizeEmail(newEmail);
    if (normalized == null || !EMAIL.matcher(normalized).matches()) throw new BusinessRuleException("INVALID_EMAIL", "E-mail inválido");
    if (normalized.equals(u.getEmail())) throw new BusinessRuleException("EMAIL_UNCHANGED", "O novo e-mail é igual ao atual");
    if (users.existsByEmailHash(Hashing.sha256Hex(normalized))) throw new ConflictException("EMAIL_IN_USE", "E-mail já cadastrado");
    byte[] raw = new byte[32]; random.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    cache.put(CACHE_PREFIX + TokenHashing.sha256Hex(token), userId, TOKEN_TTL);
    u.setPendingEmail(normalized); users.save(u);
    String link = frontendBaseUrl + "/settings/verify-email?token=" + token;
    email.send(normalized, messages.forUser(u, "mail.emailChange.subject"), messages.forUser(u, "mail.emailChange.body", u.getDisplayName(), link));
    email.send(u.getEmail(), messages.forUser(u, "mail.emailChange.notice.subject"), messages.forUser(u, "mail.emailChange.notice.body", u.getDisplayName(), mask(normalized)));
    audit.ok(AuditActions.EMAIL_CHANGE_REQUESTED, "UserAccount", userId, "novo=" + mask(normalized));
    return token;
  }

  @Transactional
  public void verify(String token) {
    if (token == null || token.isBlank()) throw new BusinessRuleException("INVALID_TOKEN", "Link inválido ou expirado");
    String key = CACHE_PREFIX + TokenHashing.sha256Hex(token);
    UUID userId = cache.get(key).orElseThrow(() -> new BusinessRuleException("INVALID_TOKEN", "Link inválido ou expirado"));
    UserAccount u = users.findById(userId).filter(UserAccount::isUsable).orElseThrow(() -> new NotFoundException("Conta", userId));
    String pending = u.getPendingEmail();
    if (pending == null) { cache.evict(key); throw new BusinessRuleException("INVALID_TOKEN", "Link inválido ou expirado"); }
    if (users.existsByEmailHash(Hashing.sha256Hex(pending))) { cache.evict(key); throw new ConflictException("EMAIL_IN_USE", "E-mail já cadastrado"); }
    String old = u.getEmail();
    u.setEmail(pending); u.setPendingEmail(null); users.save(u);
    cache.evict(key);
    email.send(old, messages.forUser(u, "mail.emailChanged.subject"), messages.forUser(u, "mail.emailChanged.body", u.getDisplayName(), mask(pending)));
    audit.ok(AuditActions.EMAIL_CHANGED, "UserAccount", userId, "novo=" + mask(pending));
  }

  static String mask(String email) { return LogSafe.maskEmail(email); }
}
