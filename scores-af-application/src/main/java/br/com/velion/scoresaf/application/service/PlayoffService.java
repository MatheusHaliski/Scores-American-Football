package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayoffSeedRepository;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.SeedPolicy;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.PlayoffBracket;
import br.com.velion.scoresaf.domain.season.PlayoffSeed;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Standings;
import br.com.velion.scoresaf.domain.season.Week;
import br.com.velion.scoresaf.domain.shared.KickoffSlot;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
import br.com.velion.scoresaf.domain.shared.WeekKind;
import br.com.velion.scoresaf.domain.shared.WeekStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Playoffs da carreira (RF66, D-96/D-97), chamados pelo {@link WeekProcessor} quando uma semana fecha:
 *
 * <ul>
 *   <li>Fim da última semana da temporada regular: congela as sementes ({@link PlayoffSeed}, pela {@link Standings}), passa a
 *   temporada a PLAYOFFS e cria a semana da primeira rodada com os jogos ({@link PlayoffBracket}).</li>
 *   <li>Fim de uma semana de playoff: cria a semana da rodada seguinte, com re-seed; depois da final, grava campeã, vice e o fim da
 *   temporada (fase OFFSEASON), os prêmios ({@link SeasonStatsService}) e o título do head coach campeão.</li>
 * </ul>
 * As semanas de playoff seguem a numeração da temporada regular (uma por semana, quinta-feira seguinte); os jogos são do calendário
 * comum ({@link Fixture}), eliminatórios no motor ({@code MatchSetup.knockout}, sem empate, D-40). Fechar uma semana de playoff também
 * processa os waivers, como na temporada regular (Q-39).
 */
@Service
public class PlayoffService {
  private static final List<KickoffSlot> SLOTS = List.of(KickoffSlot.SUN_EARLY, KickoffSlot.SUN_LATE, KickoffSlot.SUN_NIGHT, KickoffSlot.SUN_EARLY,
      KickoffSlot.SUN_LATE, KickoffSlot.MON_NIGHT);

  private final PlayoffSeedRepository seeds; private final FixtureRepository fixtures; private final WeekRepository weeks; private final SeasonRepository seasons;
  private final StandingsService standings; private final SeasonStatsService stats; private final AuditService audit;

  public PlayoffService(PlayoffSeedRepository seeds, FixtureRepository fixtures, WeekRepository weeks, SeasonRepository seasons, StandingsService standings,
                        SeasonStatsService stats, AuditService audit) {
    this.seeds = seeds; this.fixtures = fixtures; this.weeks = weeks; this.seasons = seasons; this.standings = standings; this.stats = stats;
    this.audit = audit;
  }

  /** Depois de a semana {@code week} fechar (a semana atual da temporada já é a seguinte). */
  public void afterWeek(CareerSave save, Season season, int week) {
    if (season.isCompleted()) return;
    if (season.getPhase() == SeasonPhase.REGULAR && week >= season.getRegularSeasonWeeks()) start(save, season, week);
    else if (season.getPhase() == SeasonPhase.PLAYOFFS) {
      for (Fixture f : fixtures.findBySeasonIdAndWeekNumber(season.getId(), week)) {
        if (!f.isFinished()) continue;
        Club winner = f.getAwayScore() > f.getHomeScore() ? f.getAwayClub() : f.getHomeClub();
        if (winner.getManager() != null) winner.getManager().setCareerPlayoffWins(winner.getManager().getCareerPlayoffWins() + 1);
      }
      scheduleNext(save, season, week + 1);
    }
  }

  private void start(CareerSave save, Season season, int lastWeek) {
    LeagueRules rules = CareerSupport.rules(save);
    StandingsService.League league = standings.league(season);
    List<Fixture> all = fixtures.findBySeasonId(season.getId());
    Standings.Table table = standings.table(season, league, all, season.getRegularSeasonWeeks(), rules);
    Map<String, Conference> confs = new HashMap<>();
    league.conferences().forEach(c -> confs.put(c.getId().toString(), c));
    List<PlayoffSeed> rows = new ArrayList<>();
    for (Map.Entry<String, List<Standings.Seed>> e : table.seeds().entrySet()) {
      for (Standings.Seed s : e.getValue()) {
        PlayoffSeed p = new PlayoffSeed();
        p.setSeason(season); p.setConference(confs.get(e.getKey())); p.setClub(league.byId().get(UUID.fromString(s.key()))); p.setSeed(s.seed());
        p.setDivisionChampion(s.divisionChampion()); p.setTiebreak(s.tiebreak() == null ? null : s.tiebreak().name());
        rows.add(p);
      }
    }
    seeds.saveAll(rows);
    season.setPhase(SeasonPhase.PLAYOFFS);
    seasons.save(season);
    audit.ok(AuditActions.PLAYOFFS_STARTED, "Season", season.getId(), rows.size() + " sementes");
    scheduleNext(save, season, lastWeek + 1);
  }

  /** Sementes da chave, na ordem de conferência e de semente. */
  public List<PlayoffBracket.Seed> bracketSeeds(Season season) {
    return seeds.findBySeasonIdOrderBySeedAsc(season.getId()).stream()
        .sorted(Comparator.comparingInt((PlayoffSeed s) -> s.getConference().getSortOrder()).thenComparingInt(PlayoffSeed::getSeed))
        .map(s -> new PlayoffBracket.Seed(s.getClub().getId().toString(), s.getConference().getId().toString(), s.getSeed(), s.isDivisionChampion()))
        .toList();
  }

  /** Jogos de playoff encerrados como resultados da chave (rodada = semana − temporada regular − 1). */
  public static List<PlayoffBracket.Result> results(Season season, List<Fixture> all) {
    List<PlayoffBracket.Result> out = new ArrayList<>();
    for (Fixture f : all) {
      if (f.getWeekNumber() <= season.getRegularSeasonWeeks() || !f.isFinished()) continue;
      String home = f.getHomeClub().getId().toString(), away = f.getAwayClub().getId().toString();
      String winner = f.getAwayScore() > f.getHomeScore() ? away : home;
      out.add(new PlayoffBracket.Result(f.getWeekNumber() - season.getRegularSeasonWeeks() - 1, home, away, winner));
    }
    return out;
  }

  private void scheduleNext(CareerSave save, Season season, int number) {
    if (weeks.findBySeasonIdAndNumber(season.getId(), number).isPresent()) return;
    LeagueRules rules = CareerSupport.rules(save);
    List<Fixture> all = fixtures.findBySeasonId(season.getId());
    List<PlayoffBracket.Seed> bracket = bracketSeeds(season);
    PlayoffBracket.Round round = PlayoffBracket.next(bracket, rules.getPlayoffByeSeeds(), rules.isNeutralSiteFinal(), results(season, all));
    if (round == null || round.games().isEmpty()) { finish(save, season, all, bracket, rules); return; }
    LocalDate thursday = weeks.findBySeasonIdOrderByNumberAsc(season.getId()).stream().map(Week::getStartsOn).max(Comparator.naturalOrder())
        .orElse(SeasonScheduler.firstThursday(season.getYear())).plusWeeks(1);
    Week w = new Week();
    w.setSeason(season); w.setNumber(number); w.setKind(round.kind()); w.setStartsOn(thursday); w.setStatus(WeekStatus.CURRENT);
    weeks.save(w);
    Map<UUID, Club> clubs = new HashMap<>();
    all.forEach(f -> { clubs.put(f.getHomeClub().getId(), f.getHomeClub()); clubs.put(f.getAwayClub().getId(), f.getAwayClub()); });
    seeds.findBySeasonIdOrderBySeedAsc(season.getId()).forEach(s -> clubs.put(s.getClub().getId(), s.getClub()));
    List<Fixture> created = new ArrayList<>();
    for (int i = 0; i < round.games().size(); i++) {
      PlayoffBracket.Matchup m = round.games().get(i);
      Club home = clubs.get(UUID.fromString(m.home())), away = clubs.get(UUID.fromString(m.away()));
      KickoffSlot slot = round.games().size() == 1 ? KickoffSlot.SUN_NIGHT : SLOTS.get(Math.min(i, SLOTS.size() - 1));
      Fixture f = new Fixture();
      f.setSeason(season); f.setWeek(w); f.setWeekNumber(number); f.setKickoffSlot(slot); f.setKickoffDate(thursday.plusDays(SeasonScheduler.dayOffset(slot)));
      f.setHomeClub(home); f.setAwayClub(away);
      f.setDivisional(home.getDivision() != null && away.getDivision() != null && home.getDivision().getId().equals(away.getDivision().getId()));
      f.setConferenceGame(home.getConference() != null && away.getConference() != null && home.getConference().getId().equals(away.getConference().getId()));
      f.setNeutralSite(m.neutralSite());
      f.setSeed(SeedPolicy.derive(save.getSeed(), "playoff", season.getNumber(), round.index(), i));
      created.add(f);
    }
    fixtures.saveAll(created);
  }

  private void finish(CareerSave save, Season season, List<Fixture> all, List<PlayoffBracket.Seed> bracket, LeagueRules rules) {
    String champion = PlayoffBracket.champion(bracket, rules.getPlayoffByeSeeds(), results(season, all));
    StandingsService.League league = standings.league(season);
    if (champion != null) {
      Club champ = league.byId().get(UUID.fromString(champion));
      season.setChampionClub(champ);
      all.stream().filter(f -> f.getWeekNumber() > season.getRegularSeasonWeeks() && f.isFinished())
          .max(Comparator.comparingInt(Fixture::getWeekNumber))
          .filter(f -> f.involves(champ.getId()))
          .ifPresent(f -> season.setRunnerUpClub(f.getHomeClub().getId().equals(champ.getId()) ? f.getAwayClub() : f.getHomeClub()));
      ManagerProfile coach = champ.getManager();
      if (coach != null) coach.setCareerTitles(coach.getCareerTitles() + 1);
    }
    season.setCompletedAt(Instant.now());
    season.setPhase(SeasonPhase.OFFSEASON);
    seasons.save(season);
    stats.persist(season, league, standings.table(season, league, all, season.getRegularSeasonWeeks(), rules));
    audit.ok(AuditActions.SEASON_COMPLETED, "Season", season.getId(), "campeã=" + (champion == null ? "-" : champion));
  }

  /**
   * Franquias que folgam na semana de playoff {@code week} (as melhores sementes na primeira rodada; a campeã de conferência que espera
   * a outra); {@code null} fora dos playoffs.
   */
  public Set<UUID> byes(Season season, int week) {
    if (week <= season.getRegularSeasonWeeks()) return null;
    List<PlayoffBracket.Seed> bracket = bracketSeeds(season);
    if (bracket.isEmpty()) return Set.of();
    int round = week - season.getRegularSeasonWeeks() - 1;
    List<PlayoffBracket.Result> before = results(season, fixtures.findBySeasonId(season.getId())).stream().filter(r -> r.round() < round).toList();
    LeagueRules rules = rules(season);
    PlayoffBracket.Round r = PlayoffBracket.next(bracket, rules.getPlayoffByeSeeds(), rules.isNeutralSiteFinal(), before);
    Set<UUID> out = new LinkedHashSet<>();
    if (r != null && r.index() == round) r.byes().forEach(s -> out.add(UUID.fromString(s.club())));
    return out;
  }

  /** Rodadas planejadas da chave (tipo de cada uma) pelas regras e pelo tamanho das conferências. */
  public static List<WeekKind> plannedRounds(StandingsService.League league, LeagueRules rules) {
    Map<String, Integer> perConf = new LinkedHashMap<>();
    for (Conference c : league.conferences()) {
      int size = league.ofConference(c.getId()).size();
      if (size > 0) perConf.put(c.getId().toString(), Math.min(rules.getPlayoffSeedsPerConference(), size));
    }
    int rounds = PlayoffBracket.rounds(perConf, rules.getPlayoffByeSeeds());
    return PlayoffBracket.kinds(rounds);
  }

  /** Regras da liga da temporada. */
  public static LeagueRules rules(Season season) {
    var l = season.getLeague();
    return l.getRules() == null ? LeagueRules.defaults(l.getRuleset()) : l.getRules();
  }
}
