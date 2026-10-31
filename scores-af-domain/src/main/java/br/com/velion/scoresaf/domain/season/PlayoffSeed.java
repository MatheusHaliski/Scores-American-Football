package br.com.velion.scoresaf.domain.season;

import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Semente de playoff (RF66), congelada no fim da temporada regular pela {@link Standings}: franquia, conferência, número (1 = melhor)
 * e se entrou como campeã de divisão. A chave das rodadas sai das sementes e dos jogos de playoff ({@link PlayoffBracket}).
 */
@Entity
@Table(name = "playoff_seed", indexes = @Index(name = "ix_playoff_seed_club", columnList = "club_id"),
    uniqueConstraints = {@UniqueConstraint(name = "ux_playoff_seed_club", columnNames = {"season_id", "club_id"}),
        @UniqueConstraint(name = "ux_playoff_seed_number", columnNames = {"season_id", "conference_id", "seed"})})
@Getter
@Setter
@NoArgsConstructor
public class PlayoffSeed extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "season_id", nullable = false)
  private Season season;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conference_id", nullable = false)
  private Conference conference;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "club_id", nullable = false)
  private Club club;

  @Column(nullable = false)
  private int seed;

  @Column(nullable = false)
  private boolean divisionChampion;

  /** Critério de desempate que definiu a posição (nulo sem empate). */
  @Column(length = 32)
  private String tiebreak;
}
