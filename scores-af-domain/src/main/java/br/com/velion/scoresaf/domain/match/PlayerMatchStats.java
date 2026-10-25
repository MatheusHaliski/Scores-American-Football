package br.com.velion.scoresaf.domain.match;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.season.Fixture;
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
 * Linha do box score de um jogador num jogo (docs/01 §3.6), em colunas para os agregados da temporada (RF16 líderes, RF35 prêmios,
 * blocos seguintes). Só o que o motor gera: tackles assistidos e QB hits não têm coluna (D-35). O box score completo do jogo, com
 * mais longos, YAC, drops e retornos, fica no {@link MatchReport}.
 */
@Entity
@Table(name = "player_match_stats",
    indexes = {@Index(name = "ix_player_match_stats_player", columnList = "player_id"), @Index(name = "ix_player_match_stats_career", columnList = "career_save_id"),
        @Index(name = "ix_player_match_stats_club", columnList = "club_id")},
    uniqueConstraints = @UniqueConstraint(name = "ux_player_match_stats_fixture_player", columnNames = {"fixture_id", "player_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PlayerMatchStats extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fixture_id", nullable = false)
  private Fixture fixture;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "player_id", nullable = false)
  private Player player;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "club_id", nullable = false)
  private Club club;

  @Column(nullable = false) private int snaps;
  @Column(nullable = false) private int passCompletions;
  @Column(nullable = false) private int passAttempts;
  @Column(nullable = false) private int passYards;
  @Column(nullable = false) private int passTouchdowns;
  @Column(nullable = false) private int interceptionsThrown;
  @Column(nullable = false) private int sacked;
  @Column(nullable = false) private int rushAttempts;
  @Column(nullable = false) private int rushYards;
  @Column(nullable = false) private int rushTouchdowns;
  @Column(nullable = false) private int fumblesLost;
  @Column(nullable = false) private int targets;
  @Column(nullable = false) private int receptions;
  @Column(nullable = false) private int receivingYards;
  @Column(nullable = false) private int receivingTouchdowns;
  @Column(nullable = false) private int soloTackles;
  @Column(nullable = false) private int tacklesForLoss;
  @Column(nullable = false) private int sacks;
  @Column(nullable = false) private int interceptions;
  @Column(nullable = false) private int passesDefended;
  @Column(nullable = false) private int forcedFumbles;
  @Column(nullable = false) private int fumbleRecoveries;
  @Column(nullable = false) private int fieldGoalsMade;
  @Column(nullable = false) private int fieldGoalAttempts;
  @Column(nullable = false) private int extraPointsMade;
  @Column(nullable = false) private int extraPointAttempts;
  @Column(nullable = false) private int punts;
  @Column(nullable = false) private int puntYards;
}
