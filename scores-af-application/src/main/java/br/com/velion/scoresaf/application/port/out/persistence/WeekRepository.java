package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.season.Week;
import java.util.List;
import java.util.UUID;

/** Semanas do calendário (RF21). */
public interface WeekRepository extends BaseRepository<Week, UUID> {
  List<Week> findBySeasonIdOrderByNumberAsc(UUID seasonId);
  /** Exclusão em lote. */
  void deleteBySeasonId(UUID seasonId);
  /** Exclusão em lote das semanas de todas as temporadas da carreira. */
  void deleteAllOfCareer(UUID careerId);
}
