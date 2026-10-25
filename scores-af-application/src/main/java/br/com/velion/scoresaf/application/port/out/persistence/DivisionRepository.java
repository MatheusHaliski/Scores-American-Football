package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.catalog.Division;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Divisões das conferências de uma liga. */
public interface DivisionRepository extends BaseRepository<Division, UUID> {
  List<Division> findByConferenceIdOrderBySortOrderAsc(UUID conferenceId);
  List<Division> findByConferenceLeagueIdOrderByConferenceSortOrderAscSortOrderAsc(UUID leagueId);
}
