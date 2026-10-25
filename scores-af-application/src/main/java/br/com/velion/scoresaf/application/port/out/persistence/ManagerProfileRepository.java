package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Head coaches do mundo. */
public interface ManagerProfileRepository extends BaseRepository<ManagerProfile, UUID> {
  List<ManagerProfile> findByWorldId(UUID worldId);
}
