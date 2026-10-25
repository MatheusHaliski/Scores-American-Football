package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.domain.match.MatchSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data implementa a porta {@link MatchSessionRepository}; a exclusão em lote é um DELETE JPQL (grava o pendente antes) e a
 * leitura para escrita trava a linha ({@code PESSIMISTIC_WRITE}, D-91).
 */
public interface MatchSessionJpaRepository extends JpaRepository<MatchSession, UUID>, MatchSessionRepository {
  @Override
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select x from MatchSession x where x.id = :id")
  Optional<MatchSession> findByIdForUpdate(@Param("id") UUID id);

  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from MatchSession x where x.career.id = :id")
  void deleteByCareerId(@Param("id") UUID id);
}
