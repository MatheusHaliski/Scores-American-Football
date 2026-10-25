package br.com.velion.scoresaf.domain.catalog;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Conferência da liga (1 ou 2, docs/01 §3.2). Os campeões das conferências fazem a final. */
@Entity
@Table(name = "conference", indexes = @Index(name = "ix_conference_league", columnList = "league_id"))
@Getter
@Setter
@NoArgsConstructor
public class Conference extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "league_id", nullable = false)
  private League league;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false, length = 8)
  private String shortName;

  @Column(length = 120)
  private String logoKey;

  /** Ordem de exibição (0, 1). */
  @Column(nullable = false)
  private int sortOrder;
}
