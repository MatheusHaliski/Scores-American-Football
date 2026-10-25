package br.com.velion.scoresaf.application.usecase.rf16;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.dto.SeasonDtos;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.service.StandingsService;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.catalog.Division;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Standings;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF16 — classificação da temporada da carreira ({@code SeasonController.standings} do basquete): por divisão e por conferência, com
 * V-D-E, percentual, pontos pró e contra, saldo, sequência, últimos 5, casa/fora, divisão e conferência, semente (projeção durante a
 * temporada regular), o que cada franquia já garantiu e o critério de desempate de cada posição (D-95). Só a temporada regular conta.
 */
@Service
public class GetStandingsUseCase {
  private final CareerSupport support; private final FixtureRepository fixtures; private final StandingsService standings;

  public GetStandingsUseCase(CareerSupport support, FixtureRepository fixtures, StandingsService standings) {
    this.support = support; this.fixtures = fixtures; this.standings = standings;
  }

  @Transactional(readOnly = true)
  public SeasonDtos.StandingsDto execute(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    Season season = save.getSeason();
    if (season == null) throw new BusinessRuleException("NO_SEASON", "Carreira sem temporada");
    LeagueRules rules = CareerSupport.rules(save);
    StandingsService.League league = standings.league(season);
    List<Fixture> all = fixtures.findBySeasonId(season.getId());
    int through = throughWeek(season, all);
    Standings.Table table = standings.table(season, league, all, season.getRegularSeasonWeeks(), rules);
    Map<UUID, StandingsService.Clinch> clinches = standings.clinches(season, league, all, season.getRegularSeasonWeeks(), table, rules);
    UUID userClub = save.getClub() == null ? null : save.getClub().getId();
    List<SeasonDtos.ConferenceTableDto> confs = new ArrayList<>();
    for (Conference c : league.conferences()) {
      List<SeasonDtos.DivisionTableDto> divs = new ArrayList<>();
      for (Division d : league.divisions()) {
        if (!d.getConference().getId().equals(c.getId())) continue;
        List<Standings.Ranked> ranked = table.divisions().getOrDefault(d.getId().toString(), List.of());
        divs.add(new SeasonDtos.DivisionTableDto(d.getId(), d.getName(), ranked.stream().map(r -> row(r, table, league, clinches, userClub)).toList()));
      }
      List<Standings.Ranked> conf = table.conferences().getOrDefault(c.getId().toString(), List.of());
      confs.add(new SeasonDtos.ConferenceTableDto(c.getId(), c.getName(), c.getShortName(), divs,
          conf.stream().map(r -> row(r, table, league, clinches, userClub)).toList()));
    }
    boolean official = season.getPhase() != SeasonPhase.REGULAR;
    return new SeasonDtos.StandingsDto(season.getId(), season.getYear(), season.getPhase().name(), through, season.getRegularSeasonWeeks(), official,
        rules.getPlayoffSeedsPerConference(), rules.getPlayoffByeSeeds(), userClub, Standings.DIVISION_CHAIN.stream().map(Enum::name).toList(),
        Standings.WILD_CARD_CHAIN.stream().map(Enum::name).toList(), confs);
  }

  /** Última semana da temporada regular com jogo encerrado (0 antes do primeiro). */
  public static int throughWeek(Season season, List<Fixture> all) {
    return all.stream().filter(f -> f.isFinished() && f.getWeekNumber() <= season.getRegularSeasonWeeks()).mapToInt(Fixture::getWeekNumber).max().orElse(0);
  }

  static SeasonDtos.StandingRowDto row(Standings.Ranked r, Standings.Table table, StandingsService.League league, Map<UUID, StandingsService.Clinch> clinches,
                                       UUID userClub) {
    Standings.Line l = r.line();
    UUID id = UUID.fromString(l.team().key());
    Club c = league.byId().get(id);
    StandingsService.Clinch clinch = clinches.get(id);
    return new SeasonDtos.StandingRowDto(r.rank(), CareerAssembler.club(c), record(l.overall()), l.overall().pct(), l.pointsFor(), l.pointsAgainst(),
        l.pointDiff(), l.streak().isEmpty() ? null : l.streak(), l.lastFive(), record(l.home()), record(l.away()), record(l.division()),
        record(l.conference()), table.seedOf(l.team().key()), clinch == null || clinch == StandingsService.Clinch.ELIMINATED ? null : clinch.name(),
        clinch == StandingsService.Clinch.ELIMINATED, r.tiebreak() == null ? null : r.tiebreak().name(), id.equals(userClub));
  }

  public static CareerDtos.RecordDto record(Standings.Record r) { return CareerDtos.RecordDto.of(r.wins(), r.losses(), r.ties()); }
}
