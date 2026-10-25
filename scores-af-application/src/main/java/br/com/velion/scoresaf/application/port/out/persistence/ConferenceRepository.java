package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.catalog.Conference;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Conferências de uma liga. */
public interface ConferenceRepository extends BaseRepository<Conference, UUID> {
  List<Conference> findByLeagueIdOrderBySortOrderAsc(UUID leagueId);
}
