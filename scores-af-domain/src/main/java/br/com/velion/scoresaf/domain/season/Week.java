package br.com.velion.scoresaf.domain.season;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.shared.WeekKind;
import br.com.velion.scoresaf.domain.shared.WeekStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Semana do calendário (RF21): número, tipo (regular ou rodada de playoff), quinta-feira de início e situação. */
@Entity
@Table(name = "season_week", uniqueConstraints = @UniqueConstraint(name = "ux_season_week_number", columnNames = {"season_id", "number"}))
@Getter
@Setter
@NoArgsConstructor
public class Week extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "season_id", nullable = false)
  private Season season;

  @Column(nullable = false)
  private int number;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private WeekKind kind = WeekKind.REGULAR;

  /** Quinta-feira da semana (o jogo de quinta à noite abre a semana). */
  @Column(nullable = false)
  private LocalDate startsOn;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private WeekStatus status = WeekStatus.UPCOMING;
}
