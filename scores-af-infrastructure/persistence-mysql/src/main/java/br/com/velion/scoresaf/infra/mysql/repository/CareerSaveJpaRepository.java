package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.domain.career.CareerSave;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link CareerSaveRepository}. */
public interface CareerSaveJpaRepository extends JpaRepository<CareerSave, UUID>, CareerSaveRepository {}
