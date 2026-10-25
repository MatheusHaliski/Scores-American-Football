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
 * Pedido de portabilidade/acesso (RF46 CA07 — "Baixar meus dados", LGPD Art. 18, II e V). Os arquivos (JSON completo e CSV
 * resumido) ficam no armazenamento de mídia com chave não pública e são servidos só ao titular autenticado até {@code expiresAt}
 * (24 h).
 */
@Entity
@Table(name = "data_export_job", indexes = {@Index(name = "ix_data_export_job_user", columnList = "user_id, requested_at")})
@Getter
@Setter
@NoArgsConstructor
public class DataExportJob extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private ExportStatus status = ExportStatus.REQUESTED;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt = Instant.now();

  private Instant readyAt;
  private Instant expiresAt;

  @Column(length = 255)
  private String jsonKey;

  @Column(length = 255)
  private String csvKey;

  private long sizeBytes;

  @Column(length = 255)
  private String failureReason;

  public boolean isDownloadable(Instant now) { return status == ExportStatus.READY && expiresAt != null && expiresAt.isAfter(now); }
}
