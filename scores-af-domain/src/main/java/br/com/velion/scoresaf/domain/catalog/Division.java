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

/** Divisão de uma conferência (4 franquias no padrão). Os rivais de divisão se enfrentam duas vezes por temporada (bloco 6). */
@Entity
@Table(name = "division", indexes = @Index(name = "ix_division_conference", columnList = "conference_id"))
@Getter
@Setter
@NoArgsConstructor
public class Division extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conference_id", nullable = false)
  private Conference conference;

  @Column(nullable = false, length = 80)
  private String name;

  /** Ordem de exibição dentro da conferência. */
  @Column(nullable = false)
  private int sortOrder;
}
