package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.FreeAgencyWindowRepository;
import br.com.velion.scoresaf.domain.market.FreeAgencyWindow;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data implementa a porta {@link FreeAgencyWindowRepository}; a exclusão em lote é um DELETE JPQL (grava o pendente antes). */
public interface FreeAgencyWindowJpaRepository extends JpaRepository<FreeAgencyWindow, UUID>, FreeAgencyWindowRepository {
  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from FreeAgencyWindow x where x.career.id = :id")
  void deleteByCareerId(@Param("id") UUID id);
}
