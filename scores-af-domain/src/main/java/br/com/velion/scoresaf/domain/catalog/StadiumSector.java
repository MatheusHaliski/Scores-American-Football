package br.com.velion.scoresaf.domain.catalog;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.shared.StadiumSectorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Setor do estádio (RF26): assentos, preço do ingresso (centavos de S$), conservação e custo de reforma. Um por tipo. */
@Entity
@Table(name = "stadium_sector", indexes = @Index(name = "ix_stadium_sector_stadium", columnList = "stadium_id"),
    uniqueConstraints = @UniqueConstraint(name = "ux_stadium_sector_type", columnNames = {"stadium_id", "type"}))
@Getter
@Setter
@NoArgsConstructor
public class StadiumSector extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "stadium_id", nullable = false)
  private Stadium stadium;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private StadiumSectorType type;

  @Column(nullable = false) private int seats;
  @Column(nullable = false) private long ticketPrice;
  @Column(name = "sector_condition", nullable = false) private int condition = 100;
  @Column(nullable = false) private long upgradeCost;
}
