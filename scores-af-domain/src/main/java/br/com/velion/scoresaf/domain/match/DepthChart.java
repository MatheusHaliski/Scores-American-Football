package br.com.velion.scoresaf.domain.match;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Depth chart salvo da franquia do usuário numa carreira (RF60). As vagas ficam em {@link DepthChartSlot}. {@code version} é a
 * versão otimista que o cliente manda de volta no PUT (409 quando diverge); {@code revision} sobe a cada gravação para a versão
 * subir mesmo quando só as vagas mudam. {@code validated} = a última gravação passou por todas as regras. As franquias da IA não
 * têm depth chart salvo: o motor deriva o automático do elenco (bloco 5).
 */
@Entity
@Table(name = "depth_chart", indexes = @Index(name = "ix_depth_chart_club", columnList = "club_id"),
    uniqueConstraints = @UniqueConstraint(name = "ux_depth_chart_career_club", columnNames = {"career_save_id", "club_id"}))
@Getter
@Setter
@NoArgsConstructor
public class DepthChart extends AuditableEntity {

  @Version
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "club_id", nullable = false)
  private Club club;

  @Column(nullable = false)
  private boolean validated;

  @Column(nullable = false)
  private int revision;

  /** Marca uma gravação: a versão otimista sobe no flush. */
  public void touch(boolean valid) { validated = valid; revision++; }

  /** Versão atual para o cliente (0 antes do primeiro flush). */
  public long currentVersion() { return version == null ? 0 : version; }
}
