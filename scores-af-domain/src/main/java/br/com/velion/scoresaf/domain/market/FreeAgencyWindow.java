package br.com.velion.scoresaf.domain.market;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.season.Season;
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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Janela de free agency de uma temporada da carreira (RF65, D-10, D-136): abre no avanço da temporada (dia 0, a temporada nova na fase
 * FREE_AGENCY), anda um dia por vez (a IA contrata, os waivers se resolvem, os pedidos caem) e fecha no último dia, quando a temporada
 * passa à temporada regular. Uma por temporada (chave única).
 */
@Entity
@Table(name = "free_agency", indexes = @Index(name = "ix_free_agency_career", columnList = "career_save_id"),
    uniqueConstraints = @UniqueConstraint(name = "ux_free_agency_season", columnNames = "season_id"))
@Getter
@Setter
@NoArgsConstructor
public class FreeAgencyWindow extends AuditableEntity {

  public enum Status { OPEN, CLOSED }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "season_id", nullable = false)
  private Season season;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(16)")
  private Status status = Status.OPEN;

  /** Dia atual (0 = abertura; DAY é palavra reservada no H2). */
  @Column(name = "current_day", nullable = false)
  private int day;

  /** Último dia (no avanço dele, a janela fecha). */
  @Column(nullable = false)
  private int days = MarketRules.FREE_AGENCY_DAYS;

  private Instant closedAt;

  public boolean isOpen() { return status == Status.OPEN; }
}
