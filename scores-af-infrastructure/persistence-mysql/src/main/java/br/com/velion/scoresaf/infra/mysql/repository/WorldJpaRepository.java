package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.WorldRepository;
import br.com.velion.scoresaf.domain.catalog.World;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link WorldRepository}. */
public interface WorldJpaRepository extends JpaRepository<World, UUID>, WorldRepository {}
