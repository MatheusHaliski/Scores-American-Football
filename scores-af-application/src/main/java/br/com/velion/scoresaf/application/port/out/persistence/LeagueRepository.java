package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.catalog.League;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ligas de um mundo (RF5, RF16). */
public interface LeagueRepository extends BaseRepository<League, UUID> {
  List<League> findByWorldIdOrderByTierAscNameAsc(UUID worldId);
  long countByWorldId(UUID worldId);
}
