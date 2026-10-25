package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.LeagueRepository;
import br.com.velion.scoresaf.domain.catalog.League;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link LeagueRepository}. */
public interface LeagueJpaRepository extends JpaRepository<League, UUID>, LeagueRepository {}
