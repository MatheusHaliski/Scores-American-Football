package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.season.SeasonAward;
import java.util.List;
import java.util.UUID;

/** Prêmios individuais da temporada (RF35). */
public interface SeasonAwardRepository extends BaseRepository<SeasonAward, UUID> {
  List<SeasonAward> findBySeasonId(UUID seasonId);
  /** Exclusão em lote dos prêmios de todas as temporadas da carreira. */
  void deleteAllOfCareer(UUID careerId);
}
