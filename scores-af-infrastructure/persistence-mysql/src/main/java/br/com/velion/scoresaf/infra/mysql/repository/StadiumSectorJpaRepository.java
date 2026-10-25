package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.StadiumSectorRepository;
import br.com.velion.scoresaf.domain.catalog.StadiumSector;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link StadiumSectorRepository}. */
public interface StadiumSectorJpaRepository extends JpaRepository<StadiumSector, UUID>, StadiumSectorRepository {}
