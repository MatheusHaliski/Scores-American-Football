package br.com.velion.scoresaf.domain.account;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trilha de auditoria (RNF7): login ok/falho/bloqueio, 403, alteração de dado sensível, consentimento,
 * exportação/exclusão, compra na loja, simulação de partida (seed + resultado), demissão/contratação.
 * Sem FKs (sobrevive à exclusão do recurso); gravado em transação própria (REQUIRES_NEW) pelo AuditService.
 */
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "ix_audit_log_actor", columnList = "actor"),
    @Index(name = "ix_audit_log_occurred_at", columnList = "occurred_at"),
    @Index(name = "ix_audit_log_resource", columnList = "resource, resource_id"),
    @Index(name = "ix_audit_log_correlation", columnList = "correlation_id")})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends AuditableEntity {

  /** Identificador do ator (id do usuário, e-mail hash ou "system"). */
  @Column(nullable = false, length = 64)
  private String actor;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(nullable = false, length = 64)
  private String resource;

  @Column(name = "resource_id", length = 64)
  private String resourceId;

  /** SUCCESS | FAILURE | DENIED. */
  @Column(nullable = false, length = 16)
  private String result;

  @Column(length = 64)
  private String ip;

  @Column(length = 255)
  private String userAgent;

  @Column(name = "correlation_id", length = 64)
  private String correlationId;

  /** Momento do evento (coluna própria, distinta de createdAt, para consultas por período). */
  @Column(name = "occurred_at", nullable = false)
  private Instant timestamp = Instant.now();

  /** Detalhes livres (JSON) — ex.: seed e placar de uma simulação. */
  @Column(columnDefinition = "TEXT")
  private String details;
}
