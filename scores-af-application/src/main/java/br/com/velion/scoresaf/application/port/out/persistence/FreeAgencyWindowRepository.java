package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.market.FreeAgencyWindow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Janelas de free agency das temporadas da carreira (bloco 7a). */
public interface FreeAgencyWindowRepository extends BaseRepository<FreeAgencyWindow, UUID> {
  Optional<FreeAgencyWindow> findBySeasonId(UUID seasonId);
  List<FreeAgencyWindow> findByCareerIdOrderByCreatedAtAsc(UUID careerId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
