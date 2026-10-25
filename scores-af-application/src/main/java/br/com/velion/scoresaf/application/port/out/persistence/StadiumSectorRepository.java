package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.catalog.StadiumSector;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Setores dos estádios. */
public interface StadiumSectorRepository extends BaseRepository<StadiumSector, UUID> {
  List<StadiumSector> findByStadiumId(UUID stadiumId);
  List<StadiumSector> findByStadiumIdIn(Collection<UUID> stadiumIds);
}
