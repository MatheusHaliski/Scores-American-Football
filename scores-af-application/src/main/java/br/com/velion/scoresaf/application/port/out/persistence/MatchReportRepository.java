package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.match.MatchReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Relatórios pós-jogo (RF27/RF31). */
public interface MatchReportRepository extends BaseRepository<MatchReport, UUID> {
  Optional<MatchReport> findByFixtureId(UUID fixtureId);
  List<MatchReport> findByCareerIdOrderByCreatedAtAsc(UUID careerId);
  long countByCareerId(UUID careerId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
