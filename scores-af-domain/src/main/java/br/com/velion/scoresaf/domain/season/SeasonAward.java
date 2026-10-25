package br.com.velion.scoresaf.domain.season;

import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.shared.AwardType;
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

/**
 * Prêmio individual da temporada (RF35), gravado quando a final termina ({@link SeasonAwards}): tipo, jogador (nulo no head coach do
 * ano), franquia, nome do premiado na época, pontuação da fórmula e a linha de estatísticas que a sustenta.
 */
@Entity
@Table(name = "season_award", indexes = {@Index(name = "ix_season_award_player", columnList = "player_id"),
    @Index(name = "ix_season_award_club", columnList = "club_id")},
    uniqueConstraints = @UniqueConstraint(name = "ux_season_award_type", columnNames = {"season_id", "award_type"}))
@Getter
@Setter
@NoArgsConstructor
public class SeasonAward extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "season_id", nullable = false)
  private Season season;

  @Enumerated(EnumType.STRING)
  @Column(name = "award_type", nullable = false, columnDefinition = "VARCHAR(32)")
  private AwardType type;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "player_id")
  private Player player;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "club_id", nullable = false)
  private Club club;

  /** Nome do jogador (ou do head coach) quando ganhou. */
  @Column(nullable = false, length = 120)
  private String winnerName;

  /** Posição do jogador quando ganhou (nulo no head coach). */
  @Column(length = 32)
  private String position;

  @Column(nullable = false)
  private double score;

  /** Estatísticas que sustentam o prêmio (JSON pequeno: chave → valor). */
  @Column(nullable = false, length = 512)
  private String statLine;
}
