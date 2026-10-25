package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.match.PlayerMatchStats;
import java.util.List;
import java.util.UUID;

/** Box score por jogador e jogo (docs/01 §3.6). */
public interface PlayerMatchStatsRepository extends BaseRepository<PlayerMatchStats, UUID> {
  List<PlayerMatchStats> findByFixtureId(UUID fixtureId);
  long countByCareerId(UUID careerId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
