package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.domain.career.CareerSave;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data implementa a porta {@link CareerSaveRepository}; a leitura para escrita trava a linha ({@code PESSIMISTIC_WRITE}, D-91). */
public interface CareerSaveJpaRepository extends JpaRepository<CareerSave, UUID>, CareerSaveRepository {
  @Override
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select x from CareerSave x where x.id = :id")
  Optional<CareerSave> findByIdForUpdate(@Param("id") UUID id);
}
