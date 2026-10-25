package br.com.velion.scoresaf.domain.account;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro imutável (append-only) de concessão/revogação de consentimento (RF46 CA03, LGPD Art. 8º §2º — o controlador
 * prova o consentimento). O estado atual de uma finalidade é o registro mais recente; o histórico é exibido ao titular.
 * IP e user-agent ficam só como hash/resumo (minimização, Art. 6º, III).
 */
@Entity
@Table(name = "consent_record", indexes = {
    @Index(name = "ix_consent_record_user_purpose", columnList = "user_id, purpose, recorded_at")})
@Getter
@Setter
@NoArgsConstructor
public class ConsentRecord extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private ConsentPurpose purpose;

  @Column(nullable = false)
  private boolean granted;

  /** Versão do texto apresentado ao titular no momento da decisão. */
  @Column(nullable = false, length = 32)
  private String textVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private ConsentSource source = ConsentSource.SETTINGS;

  /** SHA-256 do IP (nunca o IP em claro). */
  @Column(length = 64)
  private String ipHash;

  @Column(length = 120)
  private String userAgent;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt = Instant.now();

  /**
   * Autoriza o tratamento agora? Concedido e, para finalidade sensível (Art. 11), na versão vigente do texto: quando o texto
   * muda, o titular precisa aceitar de novo antes de qualquer tratamento novo (Q-81). Finalidade comum segue valendo.
   */
  public boolean authorizes() {
    return granted && (purpose == null || !purpose.sensitive() || purpose.currentTextVersion().equals(textVersion));
  }
}
