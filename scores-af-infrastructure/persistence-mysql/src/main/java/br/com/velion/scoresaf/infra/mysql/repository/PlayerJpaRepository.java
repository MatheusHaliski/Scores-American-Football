package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.domain.catalog.Player;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link PlayerRepository}. */
public interface PlayerJpaRepository extends JpaRepository<Player, UUID>, PlayerRepository {}
