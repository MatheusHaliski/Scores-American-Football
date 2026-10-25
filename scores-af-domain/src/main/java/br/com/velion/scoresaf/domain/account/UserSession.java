package br.com.velion.scoresaf.domain.account;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sessão de refresh token rotativo (RF2 login/refresh/logout, RNF1). Guarda apenas o hash do token.
 * {@code replacedBy} aponta para a sessão que substituiu esta na rotação (detecção de reuso).
 */
@Entity
@Table(name = "user_session", indexes = {
    @Index(name = "ux_user_session_token_hash", columnList = "refresh_token_hash", unique = true),
    @Index(name = "ix_user_session_user", columnList = "user_id"),
    @Index(name = "ix_user_session_expires", columnList = "expires_at")})
@Getter
@Setter
@NoArgsConstructor
public class UserSession extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @Column(name = "refresh_token_hash", nullable = false, length = 64, unique = true)
  private String refreshTokenHash;

  @Column(length = 64)
  private String ip;

  @Column(length = 255)
  private String userAgent;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant revokedAt;

  @Column(columnDefinition = "BINARY(16)")
  private UUID replacedBy;

  /**
   * Família da sessão (D-25): o id da sessão do login, mantido em cada rotação do refresh. O access token leva esta família
   * ({@code sid}); se nenhuma sessão ativa da família sobra (logout, troca de senha, reuso detectado), o token é recusado.
   */
  @Column(name = "family_id", columnDefinition = "BINARY(16)")
  private UUID familyId;

  public UUID family() { return familyId != null ? familyId : getId(); }

  public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
}
