package br.com.velion.scoresaf.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Base de toda entidade: PK UUID e metadados de criação/alteração (quem/quando).
 * Isto NÃO é o log de auditoria do RNF7 — esse fica em {@code AuditLog} via AuditService.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity {
  @Id
  @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
  private UUID id = UUID.randomUUID();

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Column(length = 64, updatable = false)
  private String createdBy;

  @Column(length = 64)
  private String lastModifiedBy;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now; updatedAt = now;
    createdBy = AuditContext.actor(); lastModifiedBy = createdBy;
  }

  /**
   * Ids são atribuídos na construção (UUID v4); sem esta marca o Spring Data trataria toda entidade nova como existente e
   * faria {@code merge} (a instância passada continuaria transiente). {@code @PostPersist}/{@code @PostLoad} marcam a entidade
   * como persistida; {@link #isNew()} decide entre {@code persist} e {@code merge}. No basquete isto era o {@code Persistable}
   * do Spring Data; aqui o domínio não importa Spring (D-9) e quem lê {@link #isNew()} é o repositório base da
   * infraestrutura ({@code AssignedIdJpaRepository}).
   */
  @jakarta.persistence.Transient
  private boolean persisted;

  public boolean isNew() { return !persisted; }

  @jakarta.persistence.PostPersist
  @jakarta.persistence.PostLoad
  void markPersisted() { persisted = true; }

  @PreUpdate
  void onUpdate() { updatedAt = Instant.now(); lastModifiedBy = AuditContext.actor(); }

  @Override public boolean equals(Object o) { return this == o || (o != null && getClass() == o.getClass() && id.equals(((AuditableEntity) o).id)); }
  @Override public int hashCode() { return id.hashCode(); }
}
