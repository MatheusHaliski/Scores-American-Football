package br.com.velion.scoresaf.domain.career;

import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Movimento do elenco da franquia do usuário (RF4): troca de situação (53 ↔ practice squad ↔ IR) ou dispensa para waivers com o
 * dead money calculado pelo contrato. É o histórico da carreira e a fonte das regras que dependem do tempo (semanas mínimas na IR,
 * retornos da IR por temporada) e do dead money da temporada no teto. Dinheiro em centavos de S$.
 */
@Entity
@Table(name = "roster_move", indexes = {
    @Index(name = "ix_roster_move_career_player", columnList = "career_save_id, player_id"),
    @Index(name = "ix_roster_move_season", columnList = "season_id")})
@Getter
@Setter
@NoArgsConstructor
public class RosterMove extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "season_id", nullable = false)
  private Season season;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "player_id", nullable = false)
  private Player player;

  /** Franquia que fez o movimento (a que dispensou, no caso de RELEASE). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "club_id")
  private Club club;

  @Column(nullable = false)
  private int week;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private RosterMoveKind kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", nullable = false, columnDefinition = "VARCHAR(32)")
  private RosterStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, columnDefinition = "VARCHAR(32)")
  private RosterStatus toStatus;

  /** Dead money lançado na temporada (só em RELEASE). */
  @Column(nullable = false)
  private long deadMoney;

  /** Cap hit da temporada que saiu da folha (só em RELEASE). */
  @Column(nullable = false)
  private long capHitRemoved;
}
