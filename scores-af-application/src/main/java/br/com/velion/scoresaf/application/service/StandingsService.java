package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ConferenceRepository;
import br.com.velion.scoresaf.application.port.out.persistence.DivisionRepository;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.catalog.Division;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Standings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Classificação da temporada da carreira (RF16, D-95): monta a {@link Standings} da liga com os jogos encerrados da temporada
 * regular até uma semana (os de playoff ficam fora), com a semente do calendário no sorteio. Também diz quem já garantiu título de
 * divisão, vaga ou folga e quem foi eliminado (conta conservadora, só com vitórias e empates possíveis; os desempates não entram).
 */
@Component
public class StandingsService {
  private final ClubRepository clubs; private final ConferenceRepository conferences; private final DivisionRepository divisions;

  public StandingsService(ClubRepository clubs, ConferenceRepository conferences, DivisionRepository divisions) {
    this.clubs = clubs; this.conferences = conferences; this.divisions = divisions;
  }

  /** A liga da temporada: franquias (conferência, divisão, nome), conferências e divisões na ordem do editor. */
  public record League(List<Club> clubs, Map<UUID, Club> byId, List<Conference> conferences, List<Division> divisions) {
    public List<Club> ofConference(UUID conferenceId) {
      return clubs.stream().filter(c -> c.getConference() != null && c.getConference().getId().equals(conferenceId)).toList();
    }
  }

  public League league(Season season) {
    UUID leagueId = season.getLeague().getId();
    List<Conference> confs = conferences.findByLeagueIdOrderBySortOrderAsc(leagueId);
    List<Division> divs = divisions.findByConferenceLeagueIdOrderByConferenceSortOrderAscSortOrderAsc(leagueId);
    List<Club> list = new ArrayList<>(clubs.findByLeagueIdOrderByNameAsc(leagueId));
    list.sort(Comparator.comparing((Club c) -> c.getConference() == null ? Integer.MAX_VALUE : c.getConference().getSortOrder())
        .thenComparing(c -> c.getDivision() == null ? Integer.MAX_VALUE : c.getDivision().getSortOrder()).thenComparing(Club::getName));
    Map<UUID, Club> byId = new LinkedHashMap<>();
    list.forEach(c -> byId.put(c.getId(), c));
    return new League(List.copyOf(list), byId, confs, divs);
  }

  public static List<Standings.Team> teams(League l) {
    List<Standings.Team> out = new ArrayList<>();
    for (Club c : l.clubs()) {
      String conf = c.getConference() == null ? "-" : c.getConference().getId().toString();
      String div = c.getDivision() == null ? conf + "/-" : c.getDivision().getId().toString();
      out.add(new Standings.Team(c.getId().toString(), conf, div, c.getPrestige(), c.getName()));
    }
    return out;
  }

  /** Jogos da temporada regular encerrados até a semana {@code throughWeek} (inclusive). */
  public static List<Standings.Game> games(Season season, List<Fixture> all, int throughWeek) {
    int last = Math.min(throughWeek, season.getRegularSeasonWeeks());
    List<Standings.Game> out = new ArrayList<>();
    for (Fixture f : all) {
      if (f.getWeekNumber() > last || !f.isFinished() || f.getHomeScore() == null || f.getAwayScore() == null) continue;
      out.add(new Standings.Game(f.getWeekNumber(), f.getHomeClub().getId().toString(), f.getAwayClub().getId().toString(), f.getHomeScore(), f.getAwayScore()));
    }
    return out;
  }

  public Standings.Table table(Season season, League league, List<Fixture> all, int throughWeek, LeagueRules rules) {
    return Standings.compute(teams(league), games(season, all, throughWeek), rules.getPlayoffSeedsPerConference(), season.getScheduleSeed());
  }

  /** Situação garantida da franquia: título de divisão, vaga, folga ou eliminação. */
  public enum Clinch { BYE, DIVISION, PLAYOFFS, ELIMINATED }

  /**
   * Quem já garantiu o quê com os jogos encerrados até {@code throughWeek}: pontos = 2 × vitórias + empates; o piso é o atual e o
   * teto soma 2 por jogo que falta na temporada regular. Os campeões de divisão entram nas primeiras sementes mesmo com campanha pior
   * ({@link Standings}), então a conta (conservadora: na dúvida, não marca) é, para a franquia X e o seu piso:
   *
   * <ul>
   *   <li><b>À frente no pior caso</b>: os rivais da conferência que alcançam o piso de X, mais uma por divisão rival em que ninguém
   *   alcança (o campeão dela entra à frente com campanha pior) — D-125.</li>
   *   <li><b>Título de divisão</b> (DIVISION): nenhum rival da divisão alcança o piso de X.</li>
   *   <li><b>Folga</b> (BYE): título de divisão garantido e menos rivais que alcançam X do que folgas, ou menos franquias à frente no
   *   pior caso do que folgas (o rival da própria divisão pode ganhar a divisão e levar a folga) — D-125.</li>
   *   <li><b>Vaga</b> (PLAYOFFS): menos franquias à frente no pior caso do que vagas — D-125.</li>
   *   <li><b>Eliminada</b> (ELIMINATED): algum rival da divisão tem piso acima do teto de X (não ganha mais a divisão, que garante
   *   semente) e pelo menos tantos rivais da conferência com piso acima do teto de X quanto as vagas — D-124.</li>
   * </ul>
   * Depois da temporada regular, vale a semente. As divisões são as da {@link Standings} (franquia sem divisão fica na divisão "-" da
   * conferência, como em {@link #teams}).
   */
  public Map<UUID, Clinch> clinches(Season season, League league, List<Fixture> all, int throughWeek, Standings.Table table, LeagueRules rules) {
    Map<UUID, Clinch> out = new HashMap<>();
    boolean over = throughWeek >= season.getRegularSeasonWeeks()
        && all.stream().filter(f -> f.getWeekNumber() <= season.getRegularSeasonWeeks()).allMatch(Fixture::isFinished);
    Map<UUID, Integer> remaining = new HashMap<>();
    for (Fixture f : all) {
      if (f.getWeekNumber() > season.getRegularSeasonWeeks()) continue;
      boolean pending = f.getWeekNumber() > throughWeek || !f.isFinished();
      if (!pending) continue;
      remaining.merge(f.getHomeClub().getId(), 1, Integer::sum);
      remaining.merge(f.getAwayClub().getId(), 1, Integer::sum);
    }
    for (Conference conf : league.conferences()) {
      List<Club> members = league.ofConference(conf.getId());
      int slots = Math.min(rules.getPlayoffSeedsPerConference(), members.size());
      int byes = Math.min(rules.getPlayoffByeSeeds(), slots);
      for (Club c : members) {
        String key = c.getId().toString();
        if (over) {
          Integer seed = table.seedOf(key);
          if (seed == null) { out.put(c.getId(), Clinch.ELIMINATED); continue; }
          boolean champ = table.seeds().values().stream().flatMap(List::stream).anyMatch(s -> s.key().equals(key) && s.divisionChampion());
          out.put(c.getId(), seed <= byes ? Clinch.BYE : champ ? Clinch.DIVISION : Clinch.PLAYOFFS);
          continue;
        }
        int floor = points(table, key), max = floor + 2 * remaining.getOrDefault(c.getId(), 0);
        String division = divisionKey(c);
        List<Club> rivals = members.stream().filter(o -> !o.getId().equals(c.getId())).toList();
        Predicate<Club> reaches = o -> points(table, o.getId().toString()) + 2 * remaining.getOrDefault(o.getId(), 0) >= floor;
        long canReach = rivals.stream().filter(reaches).count();
        long divisionReach = rivals.stream().filter(o -> divisionKey(o).equals(division)).filter(reaches).count();
        // divisões rivais em que ninguém alcança o piso de X: o campeão delas entra à frente mesmo com campanha pior (D-125)
        long weakDivisions = rivals.stream().map(StandingsService::divisionKey).filter(d -> !d.equals(division)).distinct()
            .filter(d -> rivals.stream().noneMatch(o -> divisionKey(o).equals(d) && reaches.test(o))).count();
        long ahead = canReach + weakDivisions;
        boolean divisionClinched = divisionReach == 0;
        // sem a divisão, só por wild card: algum rival da divisão já tem piso acima do teto de X (D-124)
        boolean divisionLost = rivals.stream().anyMatch(o -> divisionKey(o).equals(division) && points(table, o.getId().toString()) > max);
        long above = rivals.stream().filter(o -> points(table, o.getId().toString()) > max).count();
        if (byes > 0 && ((divisionClinched && canReach < byes) || ahead < byes)) out.put(c.getId(), Clinch.BYE);
        else if (divisionClinched && c.getDivision() != null) out.put(c.getId(), Clinch.DIVISION);
        else if (ahead < slots) out.put(c.getId(), Clinch.PLAYOFFS);
        else if (divisionLost && above >= slots) out.put(c.getId(), Clinch.ELIMINATED);
      }
    }
    return out;
  }

  /** Divisão da franquia como a {@link Standings} a vê ({@link #teams}): sem divisão, a divisão "-" da conferência. */
  private static String divisionKey(Club c) {
    String conf = c.getConference() == null ? "-" : c.getConference().getId().toString();
    return c.getDivision() == null ? conf + "/-" : c.getDivision().getId().toString();
  }

  private static int points(Standings.Table t, String key) {
    Standings.Record r = t.line(key).overall();
    return 2 * r.wins() + r.ties();
  }
}
