package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.season.Fixture;
import java.util.List;
import java.util.UUID;

/** Jogos do calendário (RF16, RF21, RF27). */
public interface FixtureRepository extends BaseRepository<Fixture, UUID> {
  List<Fixture> findBySeasonId(UUID seasonId);
  /** Jogos de uma semana (RF27). */
  List<Fixture> findBySeasonIdAndWeekNumber(UUID seasonId, int weekNumber);
  long countBySeasonId(UUID seasonId);
  /** Exclusão em lote. */
  void deleteBySeasonId(UUID seasonId);
  /** Exclusão em lote dos jogos de todas as temporadas da carreira. */
  void deleteAllOfCareer(UUID careerId);
}
