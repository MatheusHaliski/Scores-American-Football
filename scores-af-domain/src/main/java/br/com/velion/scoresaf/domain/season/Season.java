package br.com.velion.scoresaf.domain.season;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Temporada de uma carreira (RF16, RF21, RF35): fase, semana atual e o formato efetivo da temporada regular (semanas e jogos por
 * franquia, copiados das regras da liga na criação; ver {@link ScheduleGenerator}). Playoffs, campeão e MVP entram no bloco 6c.
 */
@Entity
@Table(name = "season", indexes = @Index(name = "ix_season_league", columnList = "league_id"),
    uniqueConstraints = @UniqueConstraint(name = "ux_season_career_number", columnNames = {"career_save_id", "number"}))
@Getter
@Setter
@NoArgsConstructor
public class Season extends AuditableEntity {

  @Version
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "league_id", nullable = false)
  private League league;

  /** 1 = primeira temporada da carreira. */
  @Column(nullable = false)
  private int number = 1;

  /** Ano da temporada (coluna {@code season_year}: YEAR é palavra reservada no H2). */
  @Column(name = "season_year", nullable = false)
  private int year;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private SeasonPhase phase = SeasonPhase.REGULAR;

  @Column(nullable = false)
  private int currentWeek = 1;

  @Column(nullable = false)
  private int regularSeasonWeeks;

  /** Jogos por franquia efetivos (o gerador reduz quando a liga tem número ímpar de franquias). */
  @Column(nullable = false)
  private int gamesPerTeam;

  /** Semente do calendário (derivada da semente da carreira). */
  @Column(nullable = false)
  private long scheduleSeed;
}
