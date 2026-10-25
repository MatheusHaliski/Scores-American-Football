package br.com.velion.scoresaf.application.usecase.rf46;

import br.com.velion.scoresaf.application.security.PasswordAttemptGuard;
import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.common.i18n.LocalizedMessages;
import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.port.out.notify.EmailPort;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserSession;
import br.com.velion.scoresaf.domain.account.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF46 CA07 — exclusão da conta (LGPD Art. 18, VI) com <b>dupla confirmação</b> (senha + digitar a palavra de confirmação no idioma
 * do usuário: EXCLUIR / DELETE / ELIMINAR) e <b>prazo de arrependimento de 7 dias</b>: a conta vai para PENDING_DELETION, todas
 * as sessões são encerradas e o titular pode entrar só para cancelar. Vencido o prazo, a eliminação é irreversível: dados pessoais
 * anonimizados e mídia/artefatos apagados pelos {@link AccountErasureHandler}s (ponto de extensão dos módulos do jogo: carreiras no
 * bloco 6, "Meu jogador" no bloco 9; a plataforma registra o das exportações). Os registros de consentimento e auditoria ficam
 * vinculados ao identificador anonimizado (cumprimento de obrigação legal, Art. 16, I).
 */
@Service
public class AccountDeletionUseCases {
  public static final Duration GRACE = Duration.ofDays(7);
  static final Set<String> CONFIRMATION_WORDS = Set.of("EXCLUIR", "DELETE", "ELIMINAR");

  public record DeletionStatus(String status, Instant requestedAt, Instant scheduledFor) {}

  private final UserAccountRepository users; private final UserSessionRepository sessions; private final SessionCachePort cache;
  private final PasswordAttemptGuard attempts; private final EmailPort email; private final LocalizedMessages messages; private final AuditService audit;
  private final List<AccountErasureHandler> erasers;

  public AccountDeletionUseCases(UserAccountRepository users, UserSessionRepository sessions, SessionCachePort cache, PasswordAttemptGuard attempts,
      EmailPort email, LocalizedMessages messages, AuditService audit, List<AccountErasureHandler> erasers) {
    this.users = users; this.sessions = sessions; this.cache = cache; this.attempts = attempts; this.email = email; this.messages = messages;
    this.audit = audit; this.erasers = erasers == null ? List.of() : List.copyOf(erasers);
  }

  @Transactional
  public DeletionStatus request(UUID userId, String currentPassword, String confirmation) {
    UserAccount u = users.findById(userId).filter(UserAccount::isUsable).orElseThrow(() -> new NotFoundException("Conta", userId));
    try { attempts.verifyStepUp(u, currentPassword); }
    catch (AuthenticationFailedException e) { audit.failed(AuditActions.ACCOUNT_DELETION_REQUESTED, "UserAccount", userId, "senha incorreta"); throw e; }
    if (confirmation == null || !CONFIRMATION_WORDS.contains(confirmation.trim().toUpperCase(Locale.ROOT)))
      throw new BusinessRuleException("DELETION_CONFIRMATION_MISMATCH", "Digite a palavra de confirmação exatamente como indicado");
    Instant now = Instant.now();
    if (u.getStatus() != UserStatus.PENDING_DELETION) { u.setStatus(UserStatus.PENDING_DELETION); u.setDeletionRequestedAt(now); users.save(u); }
    revokeAllSessions(userId, now);
    Instant scheduled = u.getDeletionRequestedAt().plus(GRACE);
    email.send(u.getEmail(), messages.forUser(u, "mail.deletion.requested.subject"),
        messages.forUser(u, "mail.deletion.requested.body", u.getDisplayName(), java.util.Date.from(scheduled)));
    audit.ok(AuditActions.ACCOUNT_DELETION_REQUESTED, "UserAccount", userId, "eliminação em " + scheduled);
    return new DeletionStatus(u.getStatus().name(), u.getDeletionRequestedAt(), scheduled);
  }

  @Transactional
  public DeletionStatus cancel(UUID userId) {
    UserAccount u = users.findById(userId).orElseThrow(() -> new NotFoundException("Conta", userId));
    if (u.getStatus() != UserStatus.PENDING_DELETION) throw new BusinessRuleException("NO_PENDING_DELETION", "Não há exclusão pendente");
    u.setStatus(UserStatus.ACTIVE); u.setDeletionRequestedAt(null); users.save(u);
    email.send(u.getEmail(), messages.forUser(u, "mail.deletion.cancelled.subject"), messages.forUser(u, "mail.deletion.cancelled.body", u.getDisplayName()));
    audit.ok(AuditActions.ACCOUNT_DELETION_CANCELLED, "UserAccount", userId);
    return new DeletionStatus(u.getStatus().name(), null, null);
  }

  @Transactional(readOnly = true)
  public DeletionStatus status(UUID userId) {
    UserAccount u = users.findById(userId).orElseThrow(() -> new NotFoundException("Conta", userId));
    return new DeletionStatus(u.getStatus().name(), u.getDeletionRequestedAt(), u.getDeletionRequestedAt() == null ? null : u.getDeletionRequestedAt().plus(GRACE));
  }

  /** Eliminação definitiva das contas com prazo vencido (de hora em hora). */
  @Scheduled(cron = "${scores.privacy.deletion-cron:0 15 * * * *}")
  @Transactional
  public int eraseDue() {
    int n = 0;
    for (UserAccount u : users.findByStatusAndDeletionRequestedAtBefore(UserStatus.PENDING_DELETION, Instant.now().minus(GRACE))) { erase(u); n++; }
    return n;
  }

  /** Anonimiza imediatamente (usado pelo agendador e por administradores em ordem judicial/ANPD). */
  @Transactional
  public void erase(UserAccount u) {
    UUID userId = u.getId();
    Instant now = Instant.now();
    revokeAllSessions(userId, now);
    for (AccountErasureHandler h : erasers) h.erase(userId);
    u.setEmail("deleted+" + userId + "@scoresaf.invalid"); u.setDisplayName("—"); u.setAvatarUrl(null); u.setPendingEmail(null); u.setBirthDate(null);
    u.setPasswordHash("!deleted"); u.setStatus(UserStatus.DELETED); u.setMarketingConsent(false);
    if (u.getDeletionRequestedAt() == null) u.setDeletionRequestedAt(now);
    users.save(u);
    audit.ok(AuditActions.ACCOUNT_DELETED, "UserAccount", userId, "anonimizado");
  }

  private void revokeAllSessions(UUID userId, Instant now) {
    for (UserSession s : sessions.findByUserIdAndRevokedAtIsNull(userId)) { s.setRevokedAt(now); sessions.save(s); cache.evict(s.getRefreshTokenHash()); }
  }
}
