package br.com.velion.scoresaf.application.usecase.rf35;

import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.dto.SeasonDtos;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayoffSeedRepository;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonAwardRepository;
import br.com.velion.scoresaf.application.service.PlayoffService;
import br.com.velion.scoresaf.application.service.StandingsService;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.PlayoffSeed;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.SeasonAward;
import br.com.velion.scoresaf.domain.season.Standings;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/**
 * Leitura de uma temporada encerrada ou em andamento para o resumo (RF35) e a sala de troféus: a tabela final, as sementes gravadas,
 * os prêmios e o resultado de uma franquia nos playoffs.
 */
@Component
public class SeasonResults {
  private final FixtureRepository fixtures; private final PlayoffSeedRepository seeds; private final SeasonAwardRepository awards;
  private final StandingsService standings;

  public SeasonResults(FixtureRepository fixtures, PlayoffSeedRepository seeds, SeasonAwardRepository awards, StandingsService standings) {
    this.fixtures = fixtures; this.seeds = seeds; this.awards = awards; this.standings = standings;
  }

  /** O que se sabe de uma temporada: liga, jogos, tabela da temporada regular, sementes gravadas e prêmios. */
  public record View(Season season, StandingsService.League league, List<Fixture> fixtures, Standings.Table table, List<PlayoffSeed> seeds,
                     List<SeasonAward> awards) {
    public Integer seedOf(UUID club) {
      if (!seeds.isEmpty()) return seeds.stream().filter(s -> s.getClub().getId().equals(club)).map(PlayoffSeed::getSeed).findFirst().orElse(null);
      return table.seedOf(club.toString());
    }

    /**
     * Resultado nos playoffs: IN_PROGRESS (temporada regular ou ainda viva), MISSED, CHAMPION, RUNNER_UP ou a rodada em que caiu
     * (WILD_CARD, DIVISIONAL, CONFERENCE).
     */
    public String playoffResult(UUID club) {
      if (club == null) return null;
      if (season.getPhase() == SeasonPhase.REGULAR) return "IN_PROGRESS";
      if (seedOf(club) == null) return "MISSED";
      if (season.getChampionClub() != null && season.getChampionClub().getId().equals(club)) return "CHAMPION";
      if (season.getRunnerUpClub() != null && season.getRunnerUpClub().getId().equals(club)) return "RUNNER_UP";
      for (Fixture f : fixtures) {
        if (f.getWeekNumber() <= season.getRegularSeasonWeeks() || !f.isFinished() || !f.involves(club)) continue;
        boolean home = f.getHomeClub().getId().equals(club);
        boolean lost = home ? f.getHomeScore() < f.getAwayScore() : f.getAwayScore() < f.getHomeScore();
        if (lost) return f.getWeek().getKind().name();
      }
      return season.isCompleted() ? "MISSED" : "IN_PROGRESS";
    }
  }

  public View view(Season season, LeagueRules rules) {
    StandingsService.League league = standings.league(season);
    List<Fixture> all = fixtures.findBySeasonId(season.getId()).stream().sorted(CareerAssembler.FIXTURE_ORDER).toList();
    return new View(season, league, all, standings.table(season, league, all, season.getRegularSeasonWeeks(), rules),
        seeds.findBySeasonIdOrderBySeedAsc(season.getId()), awards.findBySeasonId(season.getId()));
  }

  public static SeasonDtos.AwardDto award(SeasonAward a) {
    Map<String, Integer> line;
    try {
      line = br.com.velion.scoresaf.application.common.json.Jsons.MAPPER.readValue(a.getStatLine(), new TypeReference<java.util.LinkedHashMap<String, Integer>>() {});
    } catch (Exception e) {
      line = Map.of();
    }
    return new SeasonDtos.AwardDto(a.getType().name(), a.getPlayer() == null ? null : a.getPlayer().getId(), a.getWinnerName(), a.getPosition(),
        CareerAssembler.club(a.getClub()), a.getScore(), line);
  }

  /** Campanha de uma franquia na temporada regular (da tabela). */
  public static CareerDtos.RecordDto record(View v, UUID club) {
    Standings.Line l = v.table().line(club.toString());
    return l == null ? CareerDtos.RecordDto.of(0, 0, 0) : CareerDtos.RecordDto.of(l.overall().wins(), l.overall().losses(), l.overall().ties());
  }

  /** Posição na divisão e na conferência. */
  public static Integer rank(List<Standings.Ranked> list, UUID club) {
    if (list == null) return null;
    return list.stream().filter(r -> r.line().team().key().equals(club.toString())).map(Standings.Ranked::rank).findFirst().orElse(null);
  }

  public static Club club(View v, UUID id) { return v.league().byId().get(id); }

  /** Atalho: a chave de playoffs desta temporada já tem jogos. */
  public static boolean playoffsStarted(View v) { return !PlayoffService.results(v.season(), v.fixtures()).isEmpty() || !v.seeds().isEmpty(); }
}
