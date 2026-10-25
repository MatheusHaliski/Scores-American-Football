package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.DivisionRepository;
import br.com.velion.scoresaf.domain.catalog.Division;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link DivisionRepository}. */
public interface DivisionJpaRepository extends JpaRepository<Division, UUID>, DivisionRepository {}
