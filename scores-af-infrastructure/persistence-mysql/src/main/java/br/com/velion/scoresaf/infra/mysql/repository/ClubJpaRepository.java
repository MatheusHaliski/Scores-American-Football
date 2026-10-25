package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.domain.catalog.Club;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link ClubRepository}. */
public interface ClubJpaRepository extends JpaRepository<Club, UUID>, ClubRepository {}
