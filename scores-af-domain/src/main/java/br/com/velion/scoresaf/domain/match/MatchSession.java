package br.com.velion.scoresaf.domain.match;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.season.Fixture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Partida ao vivo do usuário (RF27/RF29/RF42/RF62): o {@code MatchSnapshot} completo do motor em {@code snapshotJson} (é dele que a
 * partida retoma, bit a bit) mais colunas de consulta (situação, quarto, relógio, placar, posse). Uma por jogo do calendário; os
 * jogos da IA não têm sessão (simulados inteiros quando a semana fecha). {@code snapshotVersion} é a versão do snapshot que o
 * cliente devolve nas escritas (409 quando diverge); {@code version} é o lock otimista da linha. A decisão aberta (RF62) tem prazo
 * em {@code decisionDeadlineAt}: vencido, a IA chama a jogada ({@code CallSource.AUTO}). O nível de chamada de jogada da conta
 * (RF10) é congelado no kickoff.
 */
@Entity
@Table(name = "match_session", indexes = @Index(name = "ix_match_session_career_status", columnList = "career_save_id, status"))
@Getter
@Setter
@NoArgsConstructor
public class MatchSession extends AuditableEntity {

  @Version
  private Long version;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fixture_id", nullable = false, unique = true)
  private Fixture fixture;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private MatchSessionStatus status = MatchSessionStatus.NOT_STARTED;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private PlayCallingLevel playCallingLevel = PlayCallingLevel.KEY_MOMENTS;

  @Column(nullable = false)
  private int quarter;

  @Column(nullable = false)
  private int clockSeconds;

  @Column(columnDefinition = "BINARY(16)")
  private UUID possessionClubId;

  @Column(nullable = false)
  private int homeScore;

  @Column(nullable = false)
  private int awayScore;

  @Column(nullable = false)
  private long seed;

  @Column(nullable = false)
  private long rngCursor;

  @Column(nullable = false)
  private int engineVersion;

  /** Versão do snapshot do motor (sobe a cada avanço, decisão ou ajuste). */
  @Column(nullable = false)
  private long snapshotVersion;

  /** Id da decisão aberta (RF62); nulo sem decisão. */
  @Column(length = 64)
  private String decisionId;

  /** Prazo da decisão aberta; vencido, a IA decide. */
  private Instant decisionDeadlineAt;

  private Instant finishedAt;

  /** {@code MatchSnapshot} serializado (JSON da API, {@code Jsons}). */
  @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
  private String snapshotJson;

  public boolean isFinished() { return status == MatchSessionStatus.FINISHED; }
}
