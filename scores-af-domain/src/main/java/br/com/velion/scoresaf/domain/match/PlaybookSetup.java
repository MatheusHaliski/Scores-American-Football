package br.com.velion.scoresaf.domain.match;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Playbook salvo da franquia do usuário (RF61, D-58): os pesos ficam em {@link PlaybookWeight} (um por situação e conceito, mais um
 * por cobertura). Nasce com tudo em 100 (o padrão da liga); o PUT troca tudo e exige {@code version} (409 em conflito).
 */
@Entity
@Table(name = "playbook", indexes = @Index(name = "ix_playbook_club", columnList = "club_id"),
    uniqueConstraints = @UniqueConstraint(name = "ux_playbook_career_club", columnNames = {"career_save_id", "club_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PlaybookSetup extends AuditableEntity {

  @Version
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "club_id", nullable = false)
  private Club club;

  @Column(nullable = false)
  private int revision;

  public void touch() { revision++; }

  public long currentVersion() { return version == null ? 0 : version; }
}
