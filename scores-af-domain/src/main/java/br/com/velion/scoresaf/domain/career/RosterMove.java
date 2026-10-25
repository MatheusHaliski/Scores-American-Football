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
 * Movimento do elenco da franquia do usuário (RF4): troca de situação (53 ↔ practice squad ↔ IR), dispensa para waivers com o
 * dead money calculado pelo contrato ou saída de waivers para o practice squad no avanço da semana (Q-23). É o histórico da carreira e a fonte das regras que dependem do tempo (semanas mínimas na IR,
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

  /** Dead money lançado na temporada do movimento (só em RELEASE; com a regra do 1º de junho, só a parcela dela, D-79). */
  @Column(nullable = false)
  private long deadMoney;

  /** Dead money que vai para a temporada seguinte (só em RELEASE a partir de 1º de junho, Q-24, D-79). */
  @Column(name = "dead_money_next_season", nullable = false)
  private long deadMoneyNextSeason;

  /** Cap hit da temporada que saiu da folha (só em RELEASE). */
  @Column(nullable = false)
  private long capHitRemoved;
}
