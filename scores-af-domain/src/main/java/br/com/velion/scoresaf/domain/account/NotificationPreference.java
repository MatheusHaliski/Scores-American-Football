package br.com.velion.scoresaf.domain.account;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Preferência por tipo de notificação (RF10/RF24). Única por (usuário, tipo). */
@Entity
@Table(name = "notification_preference", uniqueConstraints =
    @UniqueConstraint(name = "ux_notification_pref_user_type", columnNames = {"user_id", "type"}))
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private NotificationKind type;

  @Column(nullable = false)
  private boolean enabled = true;
}
