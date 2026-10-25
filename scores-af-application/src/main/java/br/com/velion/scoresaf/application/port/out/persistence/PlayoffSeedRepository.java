package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.season.PlayoffSeed;
import java.util.List;
import java.util.UUID;

/** Sementes dos playoffs (RF66), congeladas no fim da temporada regular. */
public interface PlayoffSeedRepository extends BaseRepository<PlayoffSeed, UUID> {
  List<PlayoffSeed> findBySeasonIdOrderBySeedAsc(UUID seasonId);
  List<PlayoffSeed> findByClubIdOrderByCreatedAtAsc(UUID clubId);
  /** Exclusão em lote das sementes de todas as temporadas da carreira. */
  void deleteAllOfCareer(UUID careerId);
}
