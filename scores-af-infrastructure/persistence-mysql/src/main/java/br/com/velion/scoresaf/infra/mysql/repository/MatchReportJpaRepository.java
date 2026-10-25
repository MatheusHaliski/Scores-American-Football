package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.MatchReportRepository;
import br.com.velion.scoresaf.domain.match.MatchReport;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data implementa a porta {@link MatchReportRepository}; a exclusão em lote é um DELETE JPQL (grava o pendente antes). */
public interface MatchReportJpaRepository extends JpaRepository<MatchReport, UUID>, MatchReportRepository {
  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from MatchReport x where x.career.id = :id")
  void deleteByCareerId(@Param("id") UUID id);
}
