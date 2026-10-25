package br.com.velion.scoresaf.domain.season;

import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.shared.FixtureStatus;
import br.com.velion.scoresaf.domain.shared.KickoffSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Jogo do calendário (RF16, RF21, RF27): semana, data e horário (quinta à noite, domingo cedo/tarde/noite, segunda à noite),
 * mandante e visitante, se é jogo de divisão ou de conferência, e o placar quando encerrado (bloco 6b). {@code seed} é a semente da
 * partida, derivada da semente da carreira.
 */
@Entity
@Table(name = "fixture", indexes = {
    @Index(name = "ix_fixture_season_week", columnList = "season_id, week_number"),
    @Index(name = "ix_fixture_week", columnList = "week_id"),
    @Index(name = "ix_fixture_home", columnList = "home_club_id"),
    @Index(name = "ix_fixture_away", columnList = "away_club_id")})
@Getter
@Setter
@NoArgsConstructor
public class Fixture extends AuditableEntity {

  @Version
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "season_id", nullable = false)
  private Season season;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "week_id", nullable = false)
  private Week week;

  @Column(name = "week_number", nullable = false)
  private int weekNumber;

  @Column(nullable = false)
  private LocalDate kickoffDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private KickoffSlot kickoffSlot;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "home_club_id", nullable = false)
  private Club homeClub;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "away_club_id", nullable = false)
  private Club awayClub;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private FixtureStatus status = FixtureStatus.SCHEDULED;

  private Integer homeScore;
  private Integer awayScore;

  @Column(nullable = false)
  private boolean overtime;

  @Column(nullable = false)
  private boolean divisional;

  @Column(name = "conference_game", nullable = false)
  private boolean conferenceGame;

  @Column(nullable = false)
  private boolean neutralSite;

  @Column(nullable = false)
  private long seed;

  public boolean isFinished() { return status == FixtureStatus.FINISHED; }

  public boolean involves(java.util.UUID clubId) {
    return homeClub.getId().equals(clubId) || awayClub.getId().equals(clubId);
  }
}
