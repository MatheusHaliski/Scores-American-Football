package br.com.velion.scoresaf.domain.catalog;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.shared.ClimateZone;
import br.com.velion.scoresaf.domain.shared.RoofType;
import br.com.velion.scoresaf.domain.shared.Surface;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Collection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Estádio da franquia (RF12/RF26): cobertura, piso, zona climática, setores e política de ingressos. A capacidade é a soma dos
 * setores e fica entre {@link #MIN_CAPACITY} e {@link #MAX_CAPACITY}. {@code club} é o lado inverso ({@code Club.stadium} guarda a
 * FK, como no basquete). Dinheiro em centavos de S$.
 */
@Entity
@Table(name = "stadium", indexes = @Index(name = "ix_stadium_world", columnList = "world_id"))
@Getter
@Setter
@NoArgsConstructor
public class Stadium extends AuditableEntity {
  public static final int MIN_CAPACITY = 60_000;
  public static final int MAX_CAPACITY = 80_000;

  @Version
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "world_id", nullable = false)
  private World world;

  @OneToOne(mappedBy = "stadium", fetch = FetchType.LAZY)
  private Club club;

  @Column(nullable = false, length = 120)
  private String name;

  /** Soma dos assentos dos setores (cache). */
  @Column(nullable = false)
  private int capacity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private RoofType roof = RoofType.OPEN;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private Surface surface = Surface.GRASS;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private ClimateZone climateZone = ClimateZone.TEMPERATE;

  @Column(nullable = false) private int level = 1;
  @Column(nullable = false) private int atmosphere = 50;

  /** Valor patrimonial (centavos de S$). */
  @Column(name = "valuation", nullable = false) private long value;

  @Column(nullable = false) private long maintenanceCostPerWeek;

  @Column(length = 512) private String model3dUrl;
  @Column(length = 120) private String endZoneArtKey;

  @Embedded
  private TicketPolicy ticketPolicy = new TicketPolicy();

  public void recalculateCapacity(Collection<StadiumSector> sectors) {
    int total = 0;
    for (StadiumSector s : sectors) total += s.getSeats();
    this.capacity = total;
  }

  public static boolean acceptsCapacity(int seats) { return seats >= MIN_CAPACITY && seats <= MAX_CAPACITY; }
}
