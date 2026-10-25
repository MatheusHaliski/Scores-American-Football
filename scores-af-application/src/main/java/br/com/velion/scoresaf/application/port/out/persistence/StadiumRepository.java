package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.catalog.Stadium;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Estádios (RF12, RF26). */
public interface StadiumRepository extends BaseRepository<Stadium, UUID> {
  List<Stadium> findByWorldId(UUID worldId);
}
