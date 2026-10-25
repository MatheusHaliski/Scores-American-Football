package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.catalog.HelmetDesign;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Capacetes dos clubes (um por clube). */
public interface HelmetDesignRepository extends BaseRepository<HelmetDesign, UUID> {
  Optional<HelmetDesign> findByClubId(UUID clubId);
  List<HelmetDesign> findByClubIdIn(Collection<UUID> clubIds);
}
