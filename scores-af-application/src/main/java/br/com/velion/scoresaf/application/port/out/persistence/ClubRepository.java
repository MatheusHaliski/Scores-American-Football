package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.catalog.Club;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Franquias (RF5, RF40). */
public interface ClubRepository extends BaseRepository<Club, UUID> {
  List<Club> findByWorldId(UUID worldId);
  List<Club> findByLeagueIdOrderByNameAsc(UUID leagueId);
  long countByLeagueId(UUID leagueId);
  long countByWorldId(UUID worldId);
  long countByDivisionId(UUID divisionId);
}
