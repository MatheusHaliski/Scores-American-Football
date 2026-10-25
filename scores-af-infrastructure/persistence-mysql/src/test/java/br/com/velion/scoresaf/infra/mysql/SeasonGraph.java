package br.com.velion.scoresaf.infra.mysql;

import br.com.velion.scoresaf.domain.analysis.AnalysisReport;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.season.PlayoffSeed;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.SeasonAward;
import br.com.velion.scoresaf.domain.shared.AwardType;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.infra.mysql.repository.AnalysisReportJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.PlayoffSeedJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.RosterMoveJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.SeasonAwardJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.SeasonJpaRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Grava a temporada encerrada mínima (bloco 6c) sobre a carreira do {@link CareerGraph}: campeã, vice e fim da temporada, uma semente
 * de playoff, um prêmio, um relatório do analista e o movimento CONTRACT_EXPIRED — todas as tabelas e colunas da V6.
 */
@Component
class SeasonGraph {
  final SeasonJpaRepository seasons; final PlayoffSeedJpaRepository seeds; final SeasonAwardJpaRepository awards; final AnalysisReportJpaRepository reports;
  final RosterMoveJpaRepository moves;

  SeasonGraph(SeasonJpaRepository seasons, PlayoffSeedJpaRepository seeds, SeasonAwardJpaRepository awards, AnalysisReportJpaRepository reports,
              RosterMoveJpaRepository moves) {
    this.seasons = seasons; this.seeds = seeds; this.awards = awards; this.reports = reports; this.moves = moves;
  }

  record Saved(PlayoffSeed seed, SeasonAward award, AnalysisReport report, RosterMove expired) {}

  Saved persist(CareerGraph.Saved career, Player player) {
    Season s = career.season();
    Club home = career.fixture().getHomeClub(), away = career.fixture().getAwayClub();
    s.setChampionClub(home); s.setRunnerUpClub(away); s.setCompletedAt(Instant.parse("2027-02-07T23:30:00Z"));
    seasons.save(s);
    PlayoffSeed seed = new PlayoffSeed();
    seed.setSeason(s); seed.setConference(home.getConference()); seed.setClub(home); seed.setSeed(1); seed.setDivisionChampion(true); seed.setTiebreak("HEAD_TO_HEAD");
    seeds.save(seed);
    SeasonAward a = new SeasonAward();
    a.setSeason(s); a.setType(AwardType.MVP); a.setPlayer(player); a.setClub(home); a.setWinnerName(player.getName()); a.setPosition(player.getPosition().name());
    a.setScore(412.5); a.setStatLine("{\"games\":17}");
    awards.save(a);
    AnalysisReport r = new AnalysisReport();
    r.setCareer(career.career()); r.setSeason(s); r.setWeek(3); r.setReportJson("{\"strength\":74}");
    reports.save(r);
    RosterMove m = new RosterMove();
    m.setCareer(career.career()); m.setSeason(s); m.setPlayer(player); m.setClub(home); m.setWeek(23); m.setKind(RosterMoveKind.CONTRACT_EXPIRED);
    m.setFromStatus(RosterStatus.ACTIVE); m.setToStatus(RosterStatus.FREE_AGENT);
    moves.save(m);
    return new Saved(seed, a, r, m);
  }
}
