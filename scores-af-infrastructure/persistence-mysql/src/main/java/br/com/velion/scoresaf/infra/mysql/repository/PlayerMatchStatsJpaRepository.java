package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.PlayerMatchStatsRepository;
import br.com.velion.scoresaf.domain.match.PlayerMatchStats;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data implementa a porta {@link PlayerMatchStatsRepository}; a exclusão em lote é um DELETE JPQL (grava o pendente antes). */
public interface PlayerMatchStatsJpaRepository extends JpaRepository<PlayerMatchStats, UUID>, PlayerMatchStatsRepository {
  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from PlayerMatchStats x where x.career.id = :id")
  void deleteByCareerId(@Param("id") UUID id);

  @Override
  @Query("select new br.com.velion.scoresaf.application.port.out.persistence.PlayerSeasonTotals(x.player.id, x.club.id, count(x), sum(x.snaps), "
      + "sum(x.passAttempts), sum(x.passCompletions), sum(x.passYards), sum(x.passTouchdowns), sum(x.interceptionsThrown), sum(x.sacked), "
      + "sum(x.rushAttempts), sum(x.rushYards), sum(x.rushTouchdowns), sum(x.fumblesLost), sum(x.targets), sum(x.receptions), sum(x.receivingYards), "
      + "sum(x.receivingTouchdowns), sum(x.soloTackles), sum(x.tacklesForLoss), sum(x.sacks), sum(x.interceptions), sum(x.passesDefended), "
      + "sum(x.forcedFumbles), sum(x.fumbleRecoveries), sum(x.fieldGoalsMade), sum(x.fieldGoalAttempts), sum(x.extraPointsMade), "
      + "sum(x.extraPointAttempts), sum(x.punts), sum(x.puntYards)) from PlayerMatchStats x "
      + "where x.fixture.season.id = :season and x.fixture.weekNumber <= :lastWeek and x.snaps > 0 group by x.player.id, x.club.id")
  java.util.List<br.com.velion.scoresaf.application.port.out.persistence.PlayerSeasonTotals> totalsBySeason(@Param("season") UUID seasonId,
      @Param("lastWeek") int lastWeek);
}
