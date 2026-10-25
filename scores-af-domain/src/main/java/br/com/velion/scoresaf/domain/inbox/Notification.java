package br.com.velion.scoresaf.domain.inbox;

import br.com.velion.scoresaf.domain.account.NotificationKind;
import br.com.velion.scoresaf.domain.account.UserAccount;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Notificação do usuário (RF24). Fonte da verdade no MySQL; projeção na Cassandra com TTL de 90 dias quando a timeline chegar.
 * Na plataforma (bloco 3) ainda não há carreira: o vínculo com a carreira ({@code save_id}) entra com a migration do bloco 6.
 */
@Entity
@Table(name = "notification", indexes = @Index(name = "ix_notification_user_read", columnList = "user_id, is_read, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class Notification extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private UserAccount user;

  @Enumerated(EnumType.STRING) @Column(nullable = false, columnDefinition = "VARCHAR(32)") private NotificationKind kind = NotificationKind.SYSTEM;
  @Column(nullable = false, length = 200) private String title;
  @Column(columnDefinition = "TEXT") private String body;
  @Column(name = "is_read", nullable = false) private boolean read;
}
