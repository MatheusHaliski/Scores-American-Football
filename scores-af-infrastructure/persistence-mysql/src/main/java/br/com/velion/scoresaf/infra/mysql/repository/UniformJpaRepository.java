package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.UniformRepository;
import br.com.velion.scoresaf.domain.catalog.Uniform;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link UniformRepository}. */
public interface UniformJpaRepository extends JpaRepository<Uniform, UUID>, UniformRepository {}
