package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.match.DepthChartSlot;
import java.util.List;
import java.util.UUID;

/** Vagas dos depth charts salvos (RF60). */
public interface DepthChartSlotRepository extends BaseRepository<DepthChartSlot, UUID> {
  List<DepthChartSlot> findByDepthChartId(UUID depthChartId);
  /** Exclusão em lote, executada antes de gravar as vagas novas (as chaves únicas de vaga e profundidade não colidem). */
  void deleteByDepthChartId(UUID depthChartId);
  /** Exclusão em lote das vagas de todos os depth charts da carreira (sem carregar as entidades). */
  void deleteAllOfCareer(UUID careerId);
}
