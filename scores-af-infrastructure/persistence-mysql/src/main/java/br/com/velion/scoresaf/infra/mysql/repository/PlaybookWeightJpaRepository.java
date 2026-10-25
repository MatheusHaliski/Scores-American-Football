package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.PlaybookWeightRepository;
import br.com.velion.scoresaf.domain.match.PlaybookWeight;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data implementa a porta {@link PlaybookWeightRepository}; a exclusão em lote é um DELETE JPQL (grava o pendente antes). */
public interface PlaybookWeightJpaRepository extends JpaRepository<PlaybookWeight, UUID>, PlaybookWeightRepository {
  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from PlaybookWeight x where x.playbook.id = :id")
  void deleteByPlaybookId(@Param("id") UUID id);

  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from PlaybookWeight x where x.playbook.id in (select p.id from PlaybookSetup p where p.career.id = :id)")
  void deleteAllOfCareer(@Param("id") UUID id);
}
