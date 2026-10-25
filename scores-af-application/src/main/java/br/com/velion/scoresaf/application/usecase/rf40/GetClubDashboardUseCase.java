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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF40 / HU-RF40 — central da franquia, com o que existe no bloco 6a: carreira, franquia, head coach, próximo jogo (ou semana de
 * folga), campanha, posição e tabela da divisão (empate: saldo de pontos, depois prestígio — a projeção de pré-temporada, D-48), próximos jogos de divisão, destaques do elenco (5 maiores overalls), lesionados,
 * teto salarial (cap hits + dead money), o estado do depth chart e a partida ao vivo em andamento (bloco 6b). Moral, análise, inbox e
 * Scores Points vêm nulos até os blocos que os criam.
 */
@Service
public class GetClubDashboardUseCase {
  private static final int HIGHLIGHTS = 5;

  private final CareerSupport support; private final FixtureRepository fixtures; private final ClubRepository clubs; private final PlayerRepository players;
  private final DepthChartService depthCharts; private final CapSheet cap; private final MatchSessionRepository sessions;

  public GetClubDashboardUseCase(CareerSupport support, FixtureRepository fixtures, ClubRepository clubs, PlayerRepository players,
                                 DepthChartService depthCharts, CapSheet cap, MatchSessionRepository sessions) {
    this.support = support; this.fixtures = fixtures; this.clubs = clubs; this.players = players; this.depthCharts = depthCharts; this.cap = cap;
    this.sessions = sessions;
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
          null, null, null, null, null, null, null, null);
    }
    UUID clubId = club.getId();
    List<Fixture> mine = all.stream().filter(f -> f.involves(clubId)).toList();
    Fixture next = mine.stream().filter(f -> f.getStatus() != FixtureStatus.FINISHED).findFirst().orElse(null);
    boolean bye = mine.stream().noneMatch(f -> f.getWeekNumber() == save.getCurrentWeek()) && season != null
        && save.getCurrentWeek() <= season.getRegularSeasonWeeks();
    CareerDtos.NextGameDto nextGame = null;
    if (next != null) {
      Club opp = next.getHomeClub().getId().equals(clubId) ? next.getAwayClub() : next.getHomeClub();
      nextGame = new CareerDtos.NextGameDto(CareerAssembler.fixture(next, clubId), next.getHomeClub().getId().equals(clubId), CareerAssembler.club(opp),
          CareerAssembler.tally(tallies, opp.getId()).record());
    }
    List<CareerDtos.StandingRowDto> division = new ArrayList<>();
    Integer rank = null;
    if (club.getDivision() != null && season != null) {
      List<Club> rivals = clubs.findByLeagueIdOrderByNameAsc(season.getLeague().getId()).stream()
          .filter(c -> c.getDivision() != null && c.getDivision().getId().equals(club.getDivision().getId()))
          .sorted(Comparator.comparingDouble((Club c) -> -CareerAssembler.tally(tallies, c.getId()).winPct())
              .thenComparingInt(c -> -(CareerAssembler.tally(tallies, c.getId()).pointsFor() - CareerAssembler.tally(tallies, c.getId()).pointsAgainst()))
              .thenComparing(Comparator.comparingInt(Club::getPrestige).reversed()).thenComparing(Club::getName))
          .toList();
      for (int i = 0; i < rivals.size(); i++) {
        Club c = rivals.get(i);
        CareerAssembler.Tally t = CareerAssembler.tally(tallies, c.getId());
        boolean user = c.getId().equals(clubId);
        if (user) rank = i + 1;
        division.add(new CareerDtos.StandingRowDto(i + 1, CareerAssembler.club(c), t.record(), t.winPct(), t.pointsFor(), t.pointsAgainst(), user));
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
    return new CareerDtos.DashboardDto(career, CareerAssembler.club(club), coach, save.getCurrentWeek(), nextGame, bye, record, rank, division, divisionGames,
        highlights, injuries, cap.of(save, roster), chart, live(save), null, null, null, null, null);
  }
}
