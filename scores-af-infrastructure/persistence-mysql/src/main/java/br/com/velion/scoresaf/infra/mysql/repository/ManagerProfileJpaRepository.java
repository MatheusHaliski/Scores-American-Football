package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.ManagerProfileRepository;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link ManagerProfileRepository}. */
public interface ManagerProfileJpaRepository extends JpaRepository<ManagerProfile, UUID>, ManagerProfileRepository {}
