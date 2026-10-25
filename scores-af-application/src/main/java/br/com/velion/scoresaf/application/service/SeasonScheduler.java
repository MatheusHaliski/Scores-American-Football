package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ConferenceRepository;
import br.com.velion.scoresaf.application.port.out.persistence.DivisionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.SeedPolicy;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.catalog.Division;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.ScheduleGenerator;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Week;
import br.com.velion.scoresaf.domain.shared.KickoffSlot;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
import br.com.velion.scoresaf.domain.shared.WeekKind;
import br.com.velion.scoresaf.domain.shared.WeekStatus;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria a temporada da carreira com o calendário da temporada regular (RF34, RF21): monta as franquias da liga por conferência,
 * divisão e colocação (prestígio, depois nome — nunca o id, para o mundo clonado dar o mesmo calendário), chama o
 * {@link ScheduleGenerator} com a semente derivada da carreira e grava {@link Season}, {@link Week} e {@link Fixture}. A semana 1
 * começa na quinta-feira depois da primeira segunda-feira de setembro, como na liga profissional (quinta depois do Labor Day).
 */
@Service
public class SeasonScheduler {
  private final SeasonRepository seasons; private final WeekRepository weeks; private final FixtureRepository fixtures;
  private final ClubRepository clubs; private final ConferenceRepository conferences; private final DivisionRepository divisions;

  public SeasonScheduler(SeasonRepository seasons, WeekRepository weeks, FixtureRepository fixtures, ClubRepository clubs,
                         ConferenceRepository conferences, DivisionRepository divisions) {
    this.seasons = seasons; this.weeks = weeks; this.fixtures = fixtures; this.clubs = clubs; this.conferences = conferences; this.divisions = divisions;
  }

  @Transactional
  public Season createFirstSeason(CareerSave save, League league, int year) {
    LeagueRules rules = league.getRules() == null ? LeagueRules.defaults(league.getRuleset()) : league.getRules();
    Map<String, Club> byKey = new HashMap<>();
    List<ScheduleGenerator.Team> teams = teams(league, byKey);
    long scheduleSeed = SeedPolicy.derive(save.getSeed(), "calendar", 1);
    ScheduleGenerator.Schedule schedule;
    try {
      schedule = ScheduleGenerator.regularSeason(teams, rules.getRegularSeasonWeeks(), rules.getGamesPerTeam(), scheduleSeed);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw new BusinessRuleException("LEAGUE_STRUCTURE_INVALID", "A liga não tem um formato de temporada válido: " + e.getMessage());
    }
    Season season = new Season();
    season.setCareer(save); season.setLeague(league); season.setNumber(1); season.setYear(year); season.setPhase(SeasonPhase.REGULAR);
    season.setCurrentWeek(1); season.setRegularSeasonWeeks(schedule.weeks()); season.setGamesPerTeam(schedule.gamesPerTeam());
    season.setScheduleSeed(scheduleSeed);
    seasons.save(season);
    LocalDate thursday = firstThursday(year);
    Map<Integer, Week> weekByNumber = new HashMap<>();
    List<Week> ws = new ArrayList<>();
    for (int w = 1; w <= schedule.weeks(); w++) {
      Week week = new Week();
      week.setSeason(season); week.setNumber(w); week.setKind(WeekKind.REGULAR); week.setStartsOn(thursday.plusWeeks(w - 1L));
      week.setStatus(w == 1 ? WeekStatus.CURRENT : WeekStatus.UPCOMING);
      weekByNumber.put(w, week); ws.add(week);
    }
    weeks.saveAll(ws);
    List<Fixture> fs = new ArrayList<>();
    int index = 0;
    for (ScheduleGenerator.Game g : schedule.games()) {
      Week week = weekByNumber.get(g.week());
      Fixture f = new Fixture();
      f.setSeason(season); f.setWeek(week); f.setWeekNumber(g.week()); f.setKickoffSlot(g.slot());
      f.setKickoffDate(week.getStartsOn().plusDays(dayOffset(g.slot())));
      f.setHomeClub(byKey.get(g.homeKey())); f.setAwayClub(byKey.get(g.awayKey()));
      f.setDivisional(g.divisional()); f.setConferenceGame(g.conference()); f.setNeutralSite(false);
      f.setSeed(SeedPolicy.derive(save.getSeed(), "fixture", 1, index++));
      fs.add(f);
    }
    fixtures.saveAll(fs);
    return season;
  }

  /** Franquias da liga no formato do gerador; liga sem estrutura completa (franquia fora de divisão, menos de 8) é recusada. */
  List<ScheduleGenerator.Team> teams(League league, Map<String, Club> byKey) {
    List<Club> leagueClubs = clubs.findByLeagueIdOrderByNameAsc(league.getId());
    if (leagueClubs.size() < League.MIN_TEAMS || leagueClubs.size() > League.MAX_TEAMS) {
      throw new BusinessRuleException("LEAGUE_STRUCTURE_INVALID", "A liga precisa ter de 8 a 32 franquias em divisões");
    }
    List<Conference> confs = conferences.findByLeagueIdOrderBySortOrderAsc(league.getId());
    List<Division> divs = divisions.findByConferenceLeagueIdOrderByConferenceSortOrderAscSortOrderAsc(league.getId());
    Map<UUID, Integer> confIndex = new HashMap<>();
    for (int i = 0; i < confs.size(); i++) confIndex.put(confs.get(i).getId(), i);
    Map<UUID, Integer> divIndex = new HashMap<>();
    Map<UUID, Integer> perConf = new HashMap<>();
    for (Division d : divs) divIndex.put(d.getId(), perConf.merge(d.getConference().getId(), 1, Integer::sum) - 1);
    Map<UUID, List<Club>> byDivision = new HashMap<>();
    for (Club c : leagueClubs) {
      if (c.getDivision() == null || !divIndex.containsKey(c.getDivision().getId())) {
        throw new BusinessRuleException("LEAGUE_STRUCTURE_INVALID", "Toda franquia da liga precisa estar numa divisão");
      }
      byDivision.computeIfAbsent(c.getDivision().getId(), k -> new ArrayList<>()).add(c);
    }
    List<ScheduleGenerator.Team> out = new ArrayList<>();
    for (Division d : divs) {
      List<Club> members = new ArrayList<>(byDivision.getOrDefault(d.getId(), List.of()));
      members.sort(Comparator.comparingInt(Club::getPrestige).reversed().thenComparing(Club::getName).thenComparing(Club::getShortName));
      for (int p = 0; p < members.size(); p++) {
        Club c = members.get(p);
        String key = c.getId().toString();
        byKey.put(key, c);
        out.add(new ScheduleGenerator.Team(key, confIndex.get(d.getConference().getId()), divIndex.get(d.getId()), p));
      }
    }
    return out;
  }

  /** Quinta-feira depois da primeira segunda-feira de setembro (Labor Day): abertura da temporada. */
  public static LocalDate firstThursday(int year) {
    return LocalDate.of(year, 9, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)).plusDays(3);
  }

  static long dayOffset(KickoffSlot slot) {
    return switch (slot) {
      case THU_NIGHT -> 0;
      case SUN_EARLY, SUN_LATE, SUN_NIGHT -> 3;
      case MON_NIGHT -> 4;
    };
  }
}
