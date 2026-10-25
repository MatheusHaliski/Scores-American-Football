package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.ConferenceRepository;
import br.com.velion.scoresaf.domain.catalog.Conference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link ConferenceRepository}. */
public interface ConferenceJpaRepository extends JpaRepository<Conference, UUID>, ConferenceRepository {}
