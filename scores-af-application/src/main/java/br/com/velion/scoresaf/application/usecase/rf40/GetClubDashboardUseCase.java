package br.com.velion.scoresaf.application.usecase.rf40;

import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.service.CapSheet;
import br.com.velion.scoresaf.application.service.DepthChartService;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.engine.ratings.CardTier;
import br.com.velion.scoresaf.domain.match.DepthChart;
import br.com.velion.scoresaf.domain.match.DepthChartPolicy;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.FixtureStatus;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.application.service.MoraleService;
import br.com.velion.scoresaf.application.service.PlayoffService;
import br.com.velion.scoresaf.application.service.StandingsService;
import br.com.velion.scoresaf.application.usecase.rf21.GetWeekNewsUseCase;
import br.com.velion.scoresaf.application.usecase.rf41.AnalysisUseCases;
import br.com.velion.scoresaf.application.usecase.rf66.PlayoffUseCases;
import br.com.velion.scoresaf.domain.season.Standings;
import br.com.velion.scoresaf.domain.season.Week;
import br.com.velion.scoresaf.domain.shared.WeekStatus;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF40 / HU-RF40 — central da franquia: carreira, franquia, head coach, próximo jogo (ou semana de folga), campanha da temporada
 * regular, posição e tabela da divisão (cadeia de desempate do RF16, D-95; sem jogos, a projeção de pré-temporada da D-48), próximos
 * jogos de divisão, destaques do elenco (5 maiores overalls), lesionados, teto salarial (cap hits + dead money), o estado do depth
 * chart, a partida ao vivo em andamento (bloco 6b) e, desde o bloco 6c (D-106), o resumo da moral do elenco, o último relatório do
 * analista, até 3 notícias da última semana encerrada e o usuário nos playoffs. Inbox e Scores Points vêm nulos até os blocos que os
 * criam (D-54).
 */
@Service
public class GetClubDashboardUseCase {
  private static final int HIGHLIGHTS = 5;
  private static final int NEWS = 3;

  private final CareerSupport support; private final FixtureRepository fixtures; private final ClubRepository clubs; private final PlayerRepository players;
  private final DepthChartService depthCharts; private final CapSheet cap; private final MatchSessionRepository sessions; private final StandingsService standings;
  private final MoraleService morale; private final AnalysisUseCases analysis; private final GetWeekNewsUseCase news; private final PlayoffUseCases playoffs;
  private final PlayoffService playoffService; private final WeekRepository weeks;

  public GetClubDashboardUseCase(CareerSupport support, FixtureRepository fixtures, ClubRepository clubs, PlayerRepository players,
                                 DepthChartService depthCharts, CapSheet cap, MatchSessionRepository sessions, StandingsService standings, MoraleService morale,
                                 AnalysisUseCases analysis, GetWeekNewsUseCase news, PlayoffUseCases playoffs, PlayoffService playoffService,
                                 WeekRepository weeks) {
    this.support = support; this.fixtures = fixtures; this.clubs = clubs; this.players = players; this.depthCharts = depthCharts; this.cap = cap;
    this.sessions = sessions; this.standings = standings; this.morale = morale; this.analysis = analysis; this.news = news; this.playoffs = playoffs;
    this.playoffService = playoffService; this.weeks = weeks;
  }

  /** Partida ao vivo não encerrada da carreira (bloco 6b), ou {@code null}. */
  private CareerDtos.LiveMatchRefDto live(CareerSave save) {
    return sessions.findByCareerIdOrderByCreatedAtAsc(save.getId()).stream().filter(s -> !s.isFinished()).findFirst()
        .map(s -> new CareerDtos.LiveMatchRefDto(s.getId(), s.getFixture().getId(), s.getFixture().getWeekNumber(), s.getStatus().name(), s.getHomeScore(),
            s.getAwayScore())).orElse(null);
  }

  @Transactional
  public CareerDtos.DashboardDto execute(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    Season season = save.getSeason();
    Club club = save.getClub();
    ManagerProfile m = save.getManager();
    CareerDtos.CoachDto coach = new CareerDtos.CoachDto(m.getId(), m.getName(), m.getNationality(), m.getCoachStyle().name(), m.getOverall(),
        m.getReputationLeague(), m.getCareerGames(), m.getCareerWins(), m.getCareerTitles());
    List<Fixture> all = season == null ? List.of() : fixtures.findBySeasonId(season.getId()).stream().sorted(CareerAssembler.FIXTURE_ORDER).toList();
    Map<UUID, CareerAssembler.Tally> tallies = CareerAssembler.tallies(all);
    CareerDtos.RecordDto record = club == null ? CareerDtos.RecordDto.of(0, 0, 0) : CareerAssembler.tally(tallies, club.getId()).record();
    CareerDtos.CareerDto career = CareerAssembler.career(save, record);
    if (club == null) {
      return new CareerDtos.DashboardDto(career, null, coach, save.getCurrentWeek(), null, false, record, null, List.of(), List.of(), List.of(), List.of(),
          null, null, null, null, null, List.of(), null, null, null, null);
    }
    UUID clubId = club.getId();
    List<Fixture> mine = all.stream().filter(f -> f.involves(clubId)).toList();
    Fixture next = mine.stream().filter(f -> f.getStatus() != FixtureStatus.FINISHED).findFirst().orElse(null);
    boolean bye = season != null && mine.stream().noneMatch(f -> f.getWeekNumber() == save.getCurrentWeek())
        && (save.getCurrentWeek() <= season.getRegularSeasonWeeks() ? !season.isCompleted() : byeInPlayoffs(season, clubId));
    CareerDtos.NextGameDto nextGame = null;
    if (next != null) {
      Club opp = next.getHomeClub().getId().equals(clubId) ? next.getAwayClub() : next.getHomeClub();
      nextGame = new CareerDtos.NextGameDto(CareerAssembler.fixture(next, clubId), next.getHomeClub().getId().equals(clubId), CareerAssembler.club(opp),
          CareerAssembler.tally(tallies, opp.getId()).record());
    }
    List<CareerDtos.StandingRowDto> division = new ArrayList<>();
    Integer rank = null;
    if (club.getDivision() != null && season != null) {
      StandingsService.League league = standings.league(season);
      Standings.Table table = standings.table(season, league, all, season.getRegularSeasonWeeks(), CareerSupport.rules(save));
      for (Standings.Ranked r : table.divisions().getOrDefault(club.getDivision().getId().toString(), List.of())) {
        Club c = league.byId().get(UUID.fromString(r.line().team().key()));
        CareerAssembler.Tally t = CareerAssembler.tally(tallies, c.getId());
        boolean user = c.getId().equals(clubId);
        if (user) rank = r.rank();
        division.add(new CareerDtos.StandingRowDto(r.rank(), CareerAssembler.club(c), t.record(), t.winPct(), t.pointsFor(), t.pointsAgainst(), user));
      }
    }
    List<CareerDtos.FixtureDto> divisionGames = mine.stream().filter(f -> f.isDivisional() && f.getStatus() != FixtureStatus.FINISHED).limit(3)
        .map(f -> CareerAssembler.fixture(f, clubId)).toList();
    List<Player> roster = players.findByClubIdOrderByOverallDesc(clubId);
    List<CareerDtos.PlayerHighlightDto> highlights = roster.stream().filter(p -> p.getRosterStatus().countsTowardRoster())
        .sorted(Comparator.comparingInt(Player::getOverall).reversed().thenComparingInt(Player::getJerseyNumber)).limit(HIGHLIGHTS)
        .map(p -> new CareerDtos.PlayerHighlightDto(p.getId(), p.getName(), p.getPosition().name(), p.getJerseyNumber(), p.getOverall(),
            CardTier.of(p.getOverall()).name())).toList();
    List<CareerDtos.InjuryDto> injuries = roster.stream().filter(p -> p.getInjuryWeeksOut() > 0 || p.getRosterStatus() == RosterStatus.IR)
        .sorted(Comparator.comparingInt(Player::getInjuryWeeksOut).reversed().thenComparing(Player::getName))
        .map(p -> new CareerDtos.InjuryDto(p.getId(), p.getName(), p.getPosition().name(), p.getRosterStatus().name(), p.getInjuryWeeksOut())).toList();
    LeagueRules rules = CareerSupport.rules(save);
    DepthChart dc = depthCharts.getOrCreate(save, club, rules.getGameDayActives());
    DepthChartPolicy.Result r = DepthChartPolicy.validate(depthCharts.read(dc), DepthChartService.byId(roster), clubId, rules.getGameDayActives());
    CareerDtos.DepthChartAlertDto chart = new CareerDtos.DepthChartAlertDto(dc.currentVersion(), r.valid(), r.errors().size(), r.warnings().size(),
        r.errors().stream().map(DepthChartPolicy.Issue::code).distinct().toList());
    Object moraleSummary = season == null ? null : MoraleService.summary(morale.compute(save).squad());
    Locale locale = LocaleContextHolder.getLocale();
    List<Object> latestNews = new ArrayList<>();
    Object playoff = null;
    if (season != null) {
      weeks.findBySeasonIdOrderByNumberAsc(season.getId()).stream().filter(w -> w.getStatus() == WeekStatus.COMPLETED).mapToInt(Week::getNumber).max()
          .ifPresent(n -> news.news(save, season, n, locale).items().stream().limit(NEWS).forEach(latestNews::add));
      playoff = playoffs.user(save);
    }
    return new CareerDtos.DashboardDto(career, CareerAssembler.club(club), coach, save.getCurrentWeek(), nextGame, bye, record, rank, division, divisionGames,
        highlights, injuries, cap.of(save, roster), chart, live(save), moraleSummary, analysis.latestSummary(save, locale), latestNews, playoff, null, null, null);
  }

  /** Semana de playoff em que a franquia folga (semente com folga na rodada). */
  private boolean byeInPlayoffs(Season season, UUID clubId) {
    if (season.isCompleted()) return false;
    java.util.Set<UUID> byes = playoffService.byes(season, season.getCurrentWeek());
    return byes != null && byes.contains(clubId);
  }
}
