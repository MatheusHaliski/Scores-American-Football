package br.com.velion.scoresaf.application.dto;

import br.com.velion.scoresaf.domain.account.UserAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Visão pública da conta (nunca expõe hash de senha). {@code locale} é a tag BCP 47 (RF46); {@code pendingEmail} mostra o endereço
 * aguardando verificação; {@code deletionScheduledFor} é preenchido durante o prazo de arrependimento da exclusão.
 */
public record AccountDto(UUID id, String email, String displayName, String avatarUrl, String locale, String role, String status,
    boolean marketingConsent, Instant dataProcessingConsentAt, Instant lastLoginAt, Instant createdAt, String profileVisibility,
    LocalDate birthDate, String pendingEmail, Instant deletionScheduledFor) {
  public static AccountDto of(UserAccount u) {
    Instant scheduled = u.getDeletionRequestedAt() == null || u.getStatus() != br.com.velion.scoresaf.domain.account.UserStatus.PENDING_DELETION
        ? null : u.getDeletionRequestedAt().plus(java.time.Duration.ofDays(7));
    return new AccountDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getAvatarUrl(), u.getLocale().tag(), u.getRole().name(), u.getStatus().name(),
        u.isMarketingConsent(), u.getDataProcessingConsentAt(), u.getLastLoginAt(), u.getCreatedAt(),
        u.getProfileVisibility() == null ? "ONLY_ME" : u.getProfileVisibility().name(), u.getBirthDate(), u.getPendingEmail(), scheduled);
  }
}
