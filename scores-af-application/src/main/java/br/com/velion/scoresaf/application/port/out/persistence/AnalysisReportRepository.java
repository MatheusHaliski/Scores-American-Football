package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.analysis.AnalysisReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Relatórios do analista do sistema (D-104). */
public interface AnalysisReportRepository extends BaseRepository<AnalysisReport, UUID> {
  Optional<AnalysisReport> findFirstByCareerIdOrderByCreatedAtDesc(UUID careerId);
  List<AnalysisReport> findByCareerIdOrderByCreatedAtAsc(UUID careerId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
