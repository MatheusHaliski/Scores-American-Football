package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.StadiumRepository;
import br.com.velion.scoresaf.domain.catalog.Stadium;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link StadiumRepository}. */
public interface StadiumJpaRepository extends JpaRepository<Stadium, UUID>, StadiumRepository {}
