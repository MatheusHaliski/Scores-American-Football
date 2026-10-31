package br.com.velion.scoresaf.application.usecase.rf66;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.dto.SeasonDtos;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.application.service.PlayoffService;
import br.com.velion.scoresaf.application.service.StandingsService;
import br.com.velion.scoresaf.application.usecase.rf16.GetStandingsUseCase;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.PlayoffBracket;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Standings;
import br.com.velion.scoresaf.domain.season.Week;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
import br.com.velion.scoresaf.domain.shared.WeekKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF66 — playoffs da carreira: situação ({@code GET /playoffs}: sementes por conferência, projetadas pela classificação até o fim da
 * temporada regular, a rodada atual e o usuário) e a chave ({@code GET /playoffs/bracket}: as rodadas com os jogos e placares, a
 * primeira projetada antes de começar). As regras da chave vêm de {@code LeagueRules} (D-96).
 */
@Service
public class PlayoffUseCases {
  private final CareerSupport support; private final FixtureRepository fixtures; private final WeekRepository weeks; private final StandingsService standings;
  private final PlayoffService playoffs;

  public PlayoffUseCases(CareerSupport support, FixtureRepository fixtures, WeekRepository weeks, StandingsService standings, PlayoffService playoffs) {
    this.support = support; this.fixtures = fixtures; this.weeks = weeks; this.standings = standings; this.playoffs = playoffs;
  }

  /** Estado comum: temporada, regras, liga, jogos, tabela, sementes (gravadas ou projetadas) e resultados de playoff. */
  record Ctx(CareerSave save, Season season, LeagueRules rules, StandingsService.League league, List<Fixture> all, Standings.Table table,
             List<PlayoffBracket.Seed> seeds, boolean projection, List<PlayoffBracket.Result> results, UUID userClub) {
    String status() { return season.isCompleted() ? "FINISHED" : season.getPhase() == SeasonPhase.PLAYOFFS ? "IN_PROGRESS" : "NOT_STARTED"; }
    Map<UUID, Integer> seedOf() {
      Map<UUID, Integer> m = new HashMap<>();
      seeds.forEach(s -> m.put(UUID.fromString(s.club()), s.seed()));
      return m;
    }
    Set<UUID> eliminated() {
      Set<UUID> out = new HashSet<>();
      for (PlayoffBracket.Result r : results) out.add(UUID.fromString(r.winner().equals(r.home()) ? r.away() : r.home()));
      return out;
    }
  }

  Ctx ctx(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    if (save.getSeason() == null) throw new BusinessRuleException("NO_SEASON", "Carreira sem temporada");
    return ctxOf(save);
  }

  static List<PlayoffBracket.Seed> projected(StandingsService.League league, Standings.Table table) {
    List<PlayoffBracket.Seed> out = new ArrayList<>();
    for (Conference c : league.conferences()) {
      for (Standings.Seed s : table.seeds().getOrDefault(c.getId().toString(), List.of())) {
        out.add(new PlayoffBracket.Seed(s.key(), c.getId().toString(), s.seed(), s.divisionChampion()));
      }
    }
    return out;
  }

  @Transactional(readOnly = true)
  public SeasonDtos.PlayoffsDto situation(UUID careerId, UUID userId) {
    Ctx c = ctx(careerId, userId);
    Set<UUID> eliminated = c.eliminated();
    UUID champion = c.season().getChampionClub() == null ? null : c.season().getChampionClub().getId();
    Map<String, Standings.Seed> tableSeeds = new HashMap<>();
    c.table().seeds().values().forEach(l -> l.forEach(s -> tableSeeds.put(s.key(), s)));
    List<SeasonDtos.ConferenceSeedsDto> confs = new ArrayList<>();
    for (Conference conf : c.league().conferences()) {
      List<SeasonDtos.SeedDto> list = new ArrayList<>();
      for (PlayoffBracket.Seed s : c.seeds()) {
        if (!s.conference().equals(conf.getId().toString())) continue;
        UUID id = UUID.fromString(s.club());
        Club club = c.league().byId().get(id);
        String status = c.projection() ? "PROJECTED" : id.equals(champion) ? "CHAMPION" : eliminated.contains(id) ? "ELIMINATED" : "ALIVE";
        Standings.Seed ts = tableSeeds.get(s.club());
        list.add(new SeasonDtos.SeedDto(s.seed(), CareerAssembler.club(club), GetStandingsUseCase.record(c.table().line(s.club()).overall()),
            s.divisionChampion(), s.seed() <= c.rules().getPlayoffByeSeeds(), status, ts == null || ts.tiebreak() == null ? null : ts.tiebreak().name()));
      }
      confs.add(new SeasonDtos.ConferenceSeedsDto(conf.getId(), conf.getName(), conf.getShortName(), list));
    }
    String currentRound = null;
    if ("IN_PROGRESS".equals(c.status())) {
      currentRound = weeks.findBySeasonIdAndNumber(c.season().getId(), c.season().getCurrentWeek()).map(w -> w.getKind().name()).orElse(null);
    }
    return new SeasonDtos.PlayoffsDto(c.season().getId(), c.status(), c.projection(), currentRound, c.rules().getPlayoffSeedsPerConference(),
        c.rules().getPlayoffByeSeeds(), c.rules().isNeutralSiteFinal(), PlayoffService.plannedRounds(c.league(), c.rules()).stream().map(Enum::name).toList(),
        confs, user(c), c.season().getChampionClub() == null ? null : CareerAssembler.club(c.season().getChampionClub()),
        c.season().getRunnerUpClub() == null ? null : CareerAssembler.club(c.season().getRunnerUpClub()));
  }

  /** O usuário nos playoffs (também usado pela central). */
  public SeasonDtos.UserPlayoffDto user(CareerSave save) {
    if (save.getSeason() == null) return null;
    return user(ctxOf(save));
  }

  private Ctx ctxOf(CareerSave save) {
    Season season = save.getSeason();
    LeagueRules rules = CareerSupport.rules(save);
    StandingsService.League league = standings.league(season);
    List<Fixture> all = fixtures.findBySeasonId(season.getId()).stream().sorted(CareerAssembler.FIXTURE_ORDER).toList();
    Standings.Table table = standings.table(season, league, all, season.getRegularSeasonWeeks(), rules);
    List<PlayoffBracket.Seed> seeds = playoffs.bracketSeeds(season);
    boolean projection = seeds.isEmpty() && season.getPhase() == SeasonPhase.REGULAR;
    if (projection) seeds = projected(league, table);
    return new Ctx(save, season, rules, league, all, table, seeds, projection, PlayoffService.results(season, all),
        save.getClub() == null ? null : save.getClub().getId());
  }

  private SeasonDtos.UserPlayoffDto user(Ctx c) {
    if (c.userClub() == null) return null;
    Integer seed = c.seedOf().get(c.userClub());
    String status;
    String round = null;
    CareerDtos.FixtureDto next = null;
    if (c.projection()) {
      status = seed == null ? "PROJECTED_OUT" : "PROJECTED_IN";
    } else if (c.season().isCompleted()) {
      UUID champ = c.season().getChampionClub() == null ? null : c.season().getChampionClub().getId();
      UUID runner = c.season().getRunnerUpClub() == null ? null : c.season().getRunnerUpClub().getId();
      status = seed == null ? "MISSED" : c.userClub().equals(champ) ? "CHAMPION" : c.userClub().equals(runner) ? "RUNNER_UP" : "ELIMINATED";
    } else if (seed == null) {
      status = "MISSED";
    } else if (c.eliminated().contains(c.userClub())) {
      status = "ELIMINATED";
    } else {
      Optional<Week> w = weeks.findBySeasonIdAndNumber(c.season().getId(), c.season().getCurrentWeek());
      round = w.map(x -> x.getKind().name()).orElse(null);
      Optional<Fixture> mine = c.all().stream().filter(f -> f.getWeekNumber() > c.season().getRegularSeasonWeeks() && !f.isFinished() && f.involves(c.userClub()))
          .findFirst();
      next = mine.map(f -> CareerAssembler.fixture(f, c.userClub())).orElse(null);
      status = mine.isPresent() ? "ALIVE" : "BYE";
    }
    return new SeasonDtos.UserPlayoffDto(c.userClub(), seed, status, round, next);
  }

  @Transactional(readOnly = true)
  public SeasonDtos.BracketDto bracket(UUID careerId, UUID userId) {
    Ctx c = ctx(careerId, userId);
    Map<UUID, Integer> seedOf = c.seedOf();
    List<WeekKind> kinds = PlayoffService.plannedRounds(c.league(), c.rules());
    List<SeasonDtos.BracketRoundDto> rounds = new ArrayList<>();
    for (int i = 0; i < kinds.size(); i++) {
      int number = c.season().getRegularSeasonWeeks() + i + 1;
      Optional<Week> week = c.projection() ? Optional.empty() : weeks.findBySeasonIdAndNumber(c.season().getId(), number);
      if (week.isPresent()) {
        List<SeasonDtos.BracketGameDto> games = c.all().stream().filter(f -> f.getWeekNumber() == number).map(f -> game(f, seedOf, c)).toList();
        Set<UUID> byes = playoffs.byes(c.season(), number);
        rounds.add(new SeasonDtos.BracketRoundDto(i, week.get().getKind().name(), number, week.get().getStatus().name(), games,
            byes == null ? List.of() : byes.stream().map(id -> team(id, seedOf, c)).toList()));
      } else if (c.projection() && i == 0) {
        PlayoffBracket.Round r = PlayoffBracket.next(c.seeds(), c.rules().getPlayoffByeSeeds(), c.rules().isNeutralSiteFinal(), List.of());
        List<SeasonDtos.BracketGameDto> games = r == null ? List.of() : r.games().stream().map(m -> projectedGame(m, c)).toList();
        List<SeasonDtos.BracketTeamDto> byes = r == null ? List.of() : r.byes().stream().map(s -> team(UUID.fromString(s.club()), seedOf, c)).toList();
        rounds.add(new SeasonDtos.BracketRoundDto(i, kinds.get(i).name(), null, "PROJECTED", games, byes));
      } else {
        rounds.add(new SeasonDtos.BracketRoundDto(i, kinds.get(i).name(), null, "PENDING", List.of(), List.of()));
      }
    }
    return new SeasonDtos.BracketDto(c.season().getId(), c.status(), c.userClub(), rounds,
        c.season().getChampionClub() == null ? null : CareerAssembler.club(c.season().getChampionClub()));
  }

  private static SeasonDtos.BracketTeamDto team(UUID id, Map<UUID, Integer> seedOf, Ctx c) {
    return new SeasonDtos.BracketTeamDto(CareerAssembler.club(c.league().byId().get(id)), seedOf.getOrDefault(id, 0));
  }

  private static SeasonDtos.BracketGameDto game(Fixture f, Map<UUID, Integer> seedOf, Ctx c) {
    UUID winner = !f.isFinished() ? null : f.getAwayScore() > f.getHomeScore() ? f.getAwayClub().getId() : f.getHomeClub().getId();
    String conference = f.isConferenceGame() && f.getHomeClub().getConference() != null ? f.getHomeClub().getConference().getShortName() : null;
    return new SeasonDtos.BracketGameDto(f.getId(), conference, team(f.getHomeClub().getId(), seedOf, c), team(f.getAwayClub().getId(), seedOf, c),
        f.getHomeScore(), f.getAwayScore(), f.isOvertime(), winner, f.isNeutralSite(), f.getStatus().name(), f.getKickoffDate(), f.getKickoffSlot().name(),
        c.userClub() != null && f.involves(c.userClub()));
  }

  private static SeasonDtos.BracketGameDto projectedGame(PlayoffBracket.Matchup m, Ctx c) {
    UUID home = UUID.fromString(m.home()), away = UUID.fromString(m.away());
    Club h = c.league().byId().get(home);
    return new SeasonDtos.BracketGameDto(null, m.conference() == null || h.getConference() == null ? null : h.getConference().getShortName(),
        new SeasonDtos.BracketTeamDto(CareerAssembler.club(h), m.homeSeed()),
        new SeasonDtos.BracketTeamDto(CareerAssembler.club(c.league().byId().get(away)), m.awaySeed()), null, null, false, null, m.neutralSite(),
        "PROJECTED", null, null, c.userClub() != null && (home.equals(c.userClub()) || away.equals(c.userClub())));
  }
}
