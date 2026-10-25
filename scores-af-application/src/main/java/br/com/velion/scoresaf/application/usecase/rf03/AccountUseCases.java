package br.com.velion.scoresaf.application.usecase.rf03;

import br.com.velion.scoresaf.application.security.PasswordHasher;
import br.com.velion.scoresaf.application.security.PasswordAttemptGuard;
import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.dto.AccountDto;
import br.com.velion.scoresaf.application.dto.SessionDto;
import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.port.out.persistence.NotificationPreferenceRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSettingsRepository;
import br.com.velion.scoresaf.application.usecase.rf02.TokenHashing;
import br.com.velion.scoresaf.application.usecase.rf46.AccountDataContributor;
import br.com.velion.scoresaf.domain.account.NotificationKind;
import br.com.velion.scoresaf.domain.account.NotificationPreference;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserLocale;
import br.com.velion.scoresaf.domain.account.UserSession;
import br.com.velion.scoresaf.domain.account.UserStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF3 / HU-RF3 — gerenciar a conta: dados pessoais (CA01), senha (CA11, encerra as demais sessões CA33),
 * sessões ativas (CA29–CA32), consentimentos com simetria de revogação (CA16–CA21), exportação (CA04) e
 * exclusão (CA05) — direitos do titular (LGPD, RNF8). Cada operação sensível gera auditoria (RNF7).
 */
@Service
public class AccountUseCases {
  public static final int MIN_AGE_YEARS = 13;
  public record UpdateProfileCommand(String displayName, String avatarUrl, String locale, java.time.LocalDate birthDate) {
    public UpdateProfileCommand(String displayName, String avatarUrl, String locale) { this(displayName, avatarUrl, locale, null); }
  }
  public record ChangePasswordCommand(String currentPassword, String newPassword, String currentRefreshToken) {}
  public record ConsentsCommand(Boolean marketingConsent, Map<String, Boolean> notifications) {}

  private final UserAccountRepository users; private final UserSessionRepository sessions; private final UserSettingsRepository settings;
  private final NotificationPreferenceRepository prefs; private final SessionCachePort cache;
  private final PasswordHasher hasher; private final PasswordAttemptGuard attempts; private final AuditService audit;
  private final List<AccountDataContributor> contributors;

  public AccountUseCases(UserAccountRepository users, UserSessionRepository sessions, UserSettingsRepository settings, NotificationPreferenceRepository prefs,
      SessionCachePort cache, PasswordHasher hasher, PasswordAttemptGuard attempts, AuditService audit, List<AccountDataContributor> contributors) {
    this.users = users; this.sessions = sessions; this.settings = settings; this.prefs = prefs; this.cache = cache; this.hasher = hasher; this.attempts = attempts; this.audit = audit;
    this.contributors = contributors == null ? List.of() : List.copyOf(contributors);
  }

  private UserAccount load(UUID userId) { return users.findById(userId).filter(UserAccount::isUsable).orElseThrow(() -> new NotFoundException("Conta", userId)); }

  @Transactional(readOnly = true)
  public AccountDto get(UUID userId) { return AccountDto.of(load(userId)); }

  @Transactional
  public AccountDto updateProfile(UUID userId, UpdateProfileCommand c) {
    UserAccount u = load(userId);
    if (c.displayName() != null && !c.displayName().isBlank()) u.setDisplayName(c.displayName().trim());
    if (c.avatarUrl() != null) u.setAvatarUrl(c.avatarUrl().isBlank() ? null : c.avatarUrl());
    if (c.locale() != null) { UserLocale l = UserLocale.parseOrNull(c.locale()); if (l == null) throw new BusinessRuleException("INVALID_LOCALE", "Idioma inválido: " + c.locale(), c.locale()); u.setLocale(l); }
    if (c.birthDate() != null) {
      // RF46 CA02: data de nascimento só para verificar idade mínima (13 anos) — Art. 6º, III e Art. 14
      if (c.birthDate().isAfter(java.time.LocalDate.now().minusYears(MIN_AGE_YEARS))) throw new BusinessRuleException("MIN_AGE", "Idade mínima: " + MIN_AGE_YEARS + " anos", MIN_AGE_YEARS);
      if (c.birthDate().isBefore(java.time.LocalDate.of(1900, 1, 1))) throw new BusinessRuleException("INVALID_BIRTH_DATE", "Data de nascimento inválida");
      u.setBirthDate(c.birthDate());
    }
    users.save(u);
    audit.ok(AuditActions.SENSITIVE_DATA_CHANGED, "UserAccount", userId, "perfil");
    return AccountDto.of(u);
  }

  @Transactional
  public void changePassword(UUID userId, ChangePasswordCommand c) {
    UserAccount u = load(userId);
    try { attempts.verifyStepUp(u, c.currentPassword()); }
    catch (AuthenticationFailedException e) { audit.failed(AuditActions.PASSWORD_CHANGED, "UserAccount", userId, "senha atual incorreta"); throw new AuthenticationFailedException("Senha atual incorreta"); }
    if (c.newPassword() == null || c.newPassword().length() < 8) throw new BusinessRuleException("WEAK_PASSWORD", "A nova senha deve ter pelo menos 8 caracteres");
    u.setPasswordHash(hasher.encode(c.newPassword())); users.save(u);
    String keepHash = c.currentRefreshToken() == null ? null : TokenHashing.sha256Hex(c.currentRefreshToken());
    Instant now = Instant.now();
    for (UserSession s : sessions.findByUserIdAndRevokedAtIsNull(userId)) {
      if (keepHash != null && keepHash.equals(s.getRefreshTokenHash())) continue;
      s.setRevokedAt(now); sessions.save(s); cache.evict(s.getRefreshTokenHash());
    }
    audit.ok(AuditActions.PASSWORD_CHANGED, "UserAccount", userId, "demais sessões encerradas");
  }

  @Transactional(readOnly = true)
  public List<SessionDto> listSessions(UUID userId, String currentRefreshToken) {
    String cur = currentRefreshToken == null ? null : TokenHashing.sha256Hex(currentRefreshToken);
    Instant now = Instant.now();
    return sessions.findByUserIdAndRevokedAtIsNull(userId).stream().filter(s -> s.isActive(now)).map(s -> SessionDto.of(s, cur != null && cur.equals(s.getRefreshTokenHash()))).toList();
  }

  @Transactional
  public void revokeSession(UUID userId, UUID sessionId) {
    UserSession s = sessions.findById(sessionId).filter(x -> x.getUser().getId().equals(userId)).orElseThrow(() -> new NotFoundException("Sessão", sessionId));
    if (s.getRevokedAt() == null) { s.setRevokedAt(Instant.now()); sessions.save(s); cache.evict(s.getRefreshTokenHash()); }
    audit.ok(AuditActions.SESSION_REVOKED, "UserSession", sessionId);
  }

  @Transactional
  public AccountDto updateConsents(UUID userId, ConsentsCommand c) {
    UserAccount u = load(userId);
    if (c.marketingConsent() != null && c.marketingConsent() != u.isMarketingConsent()) {
      u.setMarketingConsent(c.marketingConsent()); users.save(u);
      audit.ok(c.marketingConsent() ? AuditActions.CONSENT_GRANTED : AuditActions.CONSENT_REVOKED, "UserAccount", userId, "marketing");
    }
    if (c.notifications() != null) {
      c.notifications().forEach((k, enabled) -> {
        NotificationKind kind; try { kind = NotificationKind.valueOf(k.toUpperCase()); } catch (IllegalArgumentException e) { throw new BusinessRuleException("INVALID_NOTIFICATION_KIND", "Tipo de notificação inválido: " + k); }
        NotificationPreference p = prefs.findByUserIdAndType(userId, kind).orElseGet(() -> { NotificationPreference n = new NotificationPreference(); n.setUser(u); n.setType(kind); return n; });
        p.setEnabled(Boolean.TRUE.equals(enabled)); prefs.save(p);
      });
    }
    return AccountDto.of(u);
  }

  @Transactional(readOnly = true)
  public Map<String, Boolean> notificationPreferences(UUID userId) {
    Map<String, Boolean> m = new LinkedHashMap<>();
    for (NotificationKind k : NotificationKind.values()) m.put(k.name(), true);
    for (NotificationPreference p : prefs.findByUserId(userId)) m.put(p.getType().name(), p.isEnabled());
    return m;
  }

  /** Exportação dos dados do titular (LGPD art. 18) em estrutura JSON-friendly. */
  @Transactional
  public Map<String, Object> export(UUID userId) {
    UserAccount u = load(userId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("account", AccountDto.of(u));
    settings.findByUserId(userId).ifPresent(s -> out.put("settings", br.com.velion.scoresaf.application.dto.SettingsDto.of(s)));
    out.put("notificationPreferences", notificationPreferences(userId));
    // carreiras (bloco 6) e demais módulos do jogo entram pelos contribuidores (D-12)
    for (AccountDataContributor c : contributors) c.contribute(userId, out);
    out.put("sessions", listSessions(userId, null));
    out.put("exportedAt", Instant.now());
    u.setExportRequestedAt(Instant.now()); users.save(u);
    audit.ok(AuditActions.ACCOUNT_EXPORTED, "UserAccount", userId);
    return out;
  }

  /**
   * Exclusão imediata (rota antiga do RF3): anonimiza dados pessoais, encerra sessões e marca DELETED. A rota da API usa
   * {@code AccountDeletionUseCases} (prazo de arrependimento e {@code AccountErasureHandler}s dos módulos do jogo).
   */
  @Transactional
  public void delete(UUID userId, String currentPassword) {
    UserAccount u = load(userId);
    attempts.verifyStepUp(u, currentPassword);
    Instant now = Instant.now();
    for (UserSession s : sessions.findByUserIdAndRevokedAtIsNull(userId)) { s.setRevokedAt(now); sessions.save(s); cache.evict(s.getRefreshTokenHash()); }
    u.setEmail("deleted+" + userId + "@scoresaf.invalid"); u.setDisplayName("Usuário removido"); u.setAvatarUrl(null);
    u.setPasswordHash("!deleted"); u.setStatus(UserStatus.DELETED); u.setDeletionRequestedAt(now); u.setMarketingConsent(false); users.save(u);
    audit.ok(AuditActions.ACCOUNT_DELETED, "UserAccount", userId, "anonimizado");
  }
}
