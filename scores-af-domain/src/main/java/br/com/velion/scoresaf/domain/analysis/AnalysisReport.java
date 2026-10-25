package br.com.velion.scoresaf.domain.analysis;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.season.Season;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Relatório do analista do sistema (o "system AI analyst" do basquete, aqui só por regras, sem provedor externo): gerado no
 * {@code POST /analysis/refresh}, guarda a semana e o relatório em JSON (códigos e números; o texto é montado na leitura, no idioma
 * de quem lê). O {@code GET /analysis} devolve o mais recente da carreira.
 */
@Entity
@Table(name = "analysis_report", indexes = {@Index(name = "ix_analysis_report_career", columnList = "career_save_id, created_at"),
    @Index(name = "ix_analysis_report_season", columnList = "season_id")})
@Getter
@Setter
@NoArgsConstructor
public class AnalysisReport extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "season_id", nullable = false)
  private Season season;

  @Column(nullable = false)
  private int week;

  /** Origem do relatório: sempre {@code RULES} (sem IA externa). */
  @Column(nullable = false, length = 16)
  private String source = "RULES";

  @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
  private String reportJson;
}
