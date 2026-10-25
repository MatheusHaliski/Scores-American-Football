package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.catalog.Uniform;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Uniformes dos clubes. */
public interface UniformRepository extends BaseRepository<Uniform, UUID> {
  List<Uniform> findByClubId(UUID clubId);
  List<Uniform> findByClubIdIn(Collection<UUID> clubIds);
}
