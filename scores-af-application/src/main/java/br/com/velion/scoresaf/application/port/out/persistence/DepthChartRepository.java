package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.match.DepthChart;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Depth charts salvos (RF60). */
public interface DepthChartRepository extends BaseRepository<DepthChart, UUID>, FlushingRepository<DepthChart> {
  Optional<DepthChart> findByCareerIdAndClubId(UUID careerId, UUID clubId);
  /** O depth chart salvo da franquia (cada franquia é de um mundo, e o mundo SAVE é de uma carreira só). */
  Optional<DepthChart> findFirstByClubId(UUID clubId);
  List<DepthChart> findByCareerId(UUID careerId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
