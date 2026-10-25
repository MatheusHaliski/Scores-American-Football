package br.com.velion.scoresaf.domain.match;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.season.Fixture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Relatório pós-jogo (RF27/RF31) de qualquer jogo do calendário, do usuário ou da IA: placar, semente e versões do motor e das
 * regras (reprodutível), e em {@code reportJson} o box score das equipes e dos jogadores, os drives, as jogadas de pontuação e as
 * chamadas do manager, como o motor entregou (o que ele não gera continua {@code null}, D-35).
 */
@Entity
@Table(name = "match_report", indexes = @Index(name = "ix_match_report_career", columnList = "career_save_id"))
@Getter
@Setter
@NoArgsConstructor
public class MatchReport extends AuditableEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fixture_id", nullable = false, unique = true)
  private Fixture fixture;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @Column(nullable = false)
  private int homeScore;

  @Column(nullable = false)
  private int awayScore;

  @Column(nullable = false)
  private boolean overtime;

  /** Nulo = empate. */
  @Column(columnDefinition = "BINARY(16)")
  private UUID winnerClubId;

  @Column(nullable = false)
  private long seed;

  @Column(nullable = false)
  private int engineVersion;

  @Column(nullable = false)
  private int rulesVersion;

  @Column(nullable = false)
  private int playCount;

  /** Partida jogada pelo usuário (sessão ao vivo) ou simulada inteira pela IA. */
  @Column(nullable = false)
  private boolean userGame;

  @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
  private String reportJson;
}
