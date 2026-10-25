package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.PlaybookSetupRepository;
import br.com.velion.scoresaf.domain.match.PlaybookSetup;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data implementa a porta {@link PlaybookSetupRepository}; a exclusão em lote é um DELETE JPQL (grava o pendente antes). */
public interface PlaybookSetupJpaRepository extends JpaRepository<PlaybookSetup, UUID>, PlaybookSetupRepository {
  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from PlaybookSetup x where x.career.id = :id")
  void deleteByCareerId(@Param("id") UUID id);
}
