package br.com.velion.scoresaf.domain.account;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.common.Hashing;
import br.com.velion.scoresaf.domain.common.converters.AesGcmConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Conta do usuário (RF1 cadastro, RF2 login, RF3 conta/LGPD, HU-RF1..HU-RF3).
 * E-mail e nome de exibição são cifrados (AES-GCM); a busca por e-mail usa {@link #getEmailHash()} (SHA-256).
 * A senha é um hash Argon2id produzido pela camada web.
 */
@Entity
@Table(name = "user_account", indexes = {
    @Index(name = "ux_user_account_email_hash", columnList = "email_hash", unique = true),
    @Index(name = "ix_user_account_status", columnList = "status")})
@Getter
@Setter
@NoArgsConstructor
public class UserAccount extends AuditableEntity {

  /** Origem da conta (D-23): TEST_SEED e DEMO ficam fora de rankings compartilhados e métricas. Coluna `test_account` derivada no banco. */
  @Enumerated(EnumType.STRING)
  @Column(name = "account_origin", nullable = false, columnDefinition = "VARCHAR(16)")
  private AccountOrigin accountOrigin = AccountOrigin.REAL;

  /** Chave estável da fixture de teste/demo (seed idempotente por upsert). Nula em conta real. */
  @Column(name = "fixture_key", length = 80, unique = true)
  private String fixtureKey;

  /** E-mail cifrado (AES-256-GCM, Base64). Use {@link #setEmail(String)} para manter o hash coerente. */
  @Convert(converter = AesGcmConverter.class)
  @Column(nullable = false, length = 512)
  private String email;

  /** SHA-256 hex do e-mail normalizado — coluna única de busca. */
  @Column(name = "email_hash", nullable = false, length = 64, unique = true)
  private String emailHash;

  @Column(nullable = false, length = 255)
  private String passwordHash;

  @Convert(converter = AesGcmConverter.class)
  @Column(nullable = false, length = 512)
  private String displayName;

  @Column(length = 512)
  private String avatarUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private UserLocale locale = UserLocale.PT_BR;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private UserRole role = UserRole.PLAYER;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private UserStatus status = UserStatus.ACTIVE;

  @Column(nullable = false)
  private int failedLoginAttempts;

  private Instant lockedUntil;
  private Instant lastLoginAt;

  // LGPD (RF3)
  @Column(nullable = false)
  private boolean marketingConsent;
  private Instant dataProcessingConsentAt;
  private Instant exportRequestedAt;
  private Instant deletionRequestedAt;

  // RF46 — Privacidade e Conta
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private ProfileVisibility profileVisibility = ProfileVisibility.ONLY_ME;

  /** Usada só para verificar idade mínima (Art. 6º, III — minimização); nunca exibida publicamente. */
  private LocalDate birthDate;

  /** Novo e-mail aguardando verificação de posse (Art. 7º, V); cifrado como o e-mail atual. */
  @Convert(converter = AesGcmConverter.class)
  @Column(length = 512)
  private String pendingEmail;

  /** Normaliza o e-mail e atualiza o hash de busca. */
  public void setEmail(String email) {
    String normalized = Hashing.normalizeEmail(email);
    this.email = normalized;
    this.emailHash = normalized == null ? null : Hashing.sha256Hex(normalized);
  }

  public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }

  /** Conta utilizável (ativa ou em prazo de arrependimento da exclusão). */
  public boolean isUsable() { return status == UserStatus.ACTIVE || status == UserStatus.PENDING_DELETION; }

  /** Conta de teste ou demo (fora de rankings compartilhados, métricas e Scores Points oficiais). */
  public boolean isTestAccount() { return accountOrigin != null && accountOrigin.isTest(); }
}
