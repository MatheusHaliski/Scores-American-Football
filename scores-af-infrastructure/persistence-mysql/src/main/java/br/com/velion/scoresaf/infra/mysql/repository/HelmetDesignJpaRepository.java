package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.HelmetDesignRepository;
import br.com.velion.scoresaf.domain.catalog.HelmetDesign;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link HelmetDesignRepository}. */
public interface HelmetDesignJpaRepository extends JpaRepository<HelmetDesign, UUID>, HelmetDesignRepository {}
