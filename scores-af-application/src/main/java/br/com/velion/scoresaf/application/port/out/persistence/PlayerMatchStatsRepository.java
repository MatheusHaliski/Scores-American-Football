package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.match.PlayerMatchStats;
import java.util.List;
import java.util.UUID;

/** Box score por jogador e jogo (docs/01 §3.6). */
public interface PlayerMatchStatsRepository extends BaseRepository<PlayerMatchStats, UUID> {
  List<PlayerMatchStats> findByFixtureId(UUID fixtureId);
  /** Linhas de uma franquia na temporada (moral do elenco, RF41). */
  List<PlayerMatchStats> findByFixtureSeasonIdAndClubId(UUID seasonId, UUID clubId);
  /**
   * Totais por jogador e franquia nas semanas {@code 1..lastWeek} da temporada, só jogos com snap (prêmios e líderes, RF35); a
   * infraestrutura faz uma consulta agregada.
   */
  List<PlayerSeasonTotals> totalsBySeason(UUID seasonId, int lastWeek);
  long countByCareerId(UUID careerId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
