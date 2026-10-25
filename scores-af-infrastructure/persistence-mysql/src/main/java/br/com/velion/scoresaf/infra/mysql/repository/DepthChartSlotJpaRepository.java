package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.DepthChartSlotRepository;
import br.com.velion.scoresaf.domain.match.DepthChartSlot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data implementa a porta {@link DepthChartSlotRepository}; a exclusão em lote é um DELETE JPQL (grava o pendente antes). */
public interface DepthChartSlotJpaRepository extends JpaRepository<DepthChartSlot, UUID>, DepthChartSlotRepository {
  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from DepthChartSlot x where x.depthChart.id = :id")
  void deleteByDepthChartId(@Param("id") UUID id);

  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from DepthChartSlot x where x.depthChart.id in (select d.id from DepthChart d where d.career.id = :id)")
  void deleteAllOfCareer(@Param("id") UUID id);
}
