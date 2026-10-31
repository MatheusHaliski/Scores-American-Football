package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.dto.MoraleDtos;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerMatchStatsRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.application.usecase.rf42.MatchTeams;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.engine.model.TeamSnapshot;
import br.com.velion.scoresaf.domain.engine.morale.PlayerMoraleEngine;
import br.com.velion.scoresaf.domain.match.ChartSlot;
import br.com.velion.scoresaf.domain.match.DepthChartPolicy;
import br.com.velion.scoresaf.domain.match.PlayerMatchStats;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Week;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.Unit;
import br.com.velion.scoresaf.domain.shared.WeekStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Moral do elenco do usuário (RF41, D-102), calculada na leitura pelo {@link PlayerMoraleEngine}: cada jogador parte da moral guardada
 * no início da temporada ({@code Player.morale}) e passa pelas semanas encerradas, uma a uma (resultado e saldo do time, snaps × fatia
 * esperada para o papel no depth chart efetivo de hoje, sequência, contrato, idade). Sem estado novo no banco: mesmos jogos ⇒ mesma
 * moral. No avanço da temporada a moral final volta 30 % para 60 e vira a de partida da temporada seguinte.
 */
@Component
public class MoraleService {
  /** Moral de referência para onde a moral volta na virada da temporada. */
  public static final int SEASON_BASELINE = 60;

  private final PlayerRepository players; private final FixtureRepository fixtures; private final WeekRepository weeks; private final PlayerMatchStatsRepository stats;
  private final DepthChartService depthCharts;

  public MoraleService(PlayerRepository players, FixtureRepository fixtures, WeekRepository weeks, PlayerMatchStatsRepository stats,
                       DepthChartService depthCharts) {
    this.players = players; this.fixtures = fixtures; this.weeks = weeks; this.stats = stats; this.depthCharts = depthCharts;
  }

  /** Estado da moral por jogador depois da última semana encerrada, mais o que a tela mostra. */
  public record Result(MoraleDtos.SquadMoraleDto squad, Map<UUID, PlayerMoraleEngine.State> states) {}

  public Result compute(CareerSave save) {
    Season season = save.getSeason();
    Club club = save.getClub();
    List<Player> roster = players.findByClubIdOrderByOverallDesc(club.getId()).stream().filter(p -> p.getRosterStatus().requiresClub()).toList();
    int actives = CareerSupport.rules(save).getGameDayActives();
    var saved = depthCharts.read(depthCharts.getOrCreate(save, club, actives));
    TeamSnapshot team = MatchTeams.team(club, roster, actives, DepthChartPolicy.toEngine(saved));
    var resolved = team.resolvedDepthChart();
    Map<UUID, Double> expected = new HashMap<>();
    java.util.Set<UUID> inChart = new java.util.HashSet<>();
    for (ChartSlot slot : ChartSlot.values()) {
      if (slot.engineSlot() == null) continue;
      List<UUID> ids = resolved.get(slot.engineSlot());
      for (int i = 0; i < ids.size(); i++) {
        inChart.add(ids.get(i));
        expected.merge(ids.get(i), PlayerMoraleEngine.expectedShare(slot, i + 1), Math::max);
      }
    }
    List<Integer> completed = weeks.findBySeasonIdOrderByNumberAsc(season.getId()).stream().filter(w -> w.getStatus() == WeekStatus.COMPLETED)
        .map(Week::getNumber).toList();
    Map<Integer, Fixture> games = new HashMap<>();
    for (Fixture f : fixtures.findBySeasonId(season.getId())) if (f.isFinished() && f.involves(club.getId())) games.put(f.getWeekNumber(), f);
    Map<UUID, Map<UUID, Integer>> snaps = new HashMap<>();
    Map<UUID, int[]> unitSnaps = new HashMap<>();
    for (PlayerMatchStats s : stats.findByFixtureSeasonIdAndClubId(season.getId(), club.getId())) {
      snaps.computeIfAbsent(s.getFixture().getId(), k -> new HashMap<>()).put(s.getPlayer().getId(), s.getSnaps());
      Unit u = s.getPlayer().getPosition().unit();
      int[] m = unitSnaps.computeIfAbsent(s.getFixture().getId(), k -> new int[3]);
      m[u.ordinal()] = Math.max(m[u.ordinal()], s.getSnaps());
    }
    // sequência do time depois de cada jogo
    Map<Integer, Integer> streakAfter = new HashMap<>();
    int streak = 0, w = 0, l = 0, t = 0;
    for (int week : completed) {
      Fixture f = games.get(week);
      if (f == null) continue;
      int pf = f.getHomeClub().getId().equals(club.getId()) ? f.getHomeScore() : f.getAwayScore();
      int pa = f.getHomeClub().getId().equals(club.getId()) ? f.getAwayScore() : f.getHomeScore();
      if (pf > pa) { streak = streak > 0 ? streak + 1 : 1; if (week <= season.getRegularSeasonWeeks()) w++; }
      else if (pf < pa) { streak = streak < 0 ? streak - 1 : -1; if (week <= season.getRegularSeasonWeeks()) l++; }
      else { streak = 0; if (week <= season.getRegularSeasonWeeks()) t++; }
      streakAfter.put(week, streak);
    }
    Map<UUID, PlayerMoraleEngine.State> states = new LinkedHashMap<>();
    List<MoraleDtos.PlayerMoraleDto> rows = new ArrayList<>();
    for (Player p : roster) {
      double exp = expected.getOrDefault(p.getId(), 0.0);
      PlayerMoraleEngine.Role role = PlayerMoraleEngine.role(p.getPosition(), exp, inChart.contains(p.getId()));
      int yearsLeft = p.getContract() == null ? 0 : Math.max(0, p.getContract().endSeason() - season.getYear());
      boolean finalYear = p.getContract() != null && p.getContract().endSeason() <= season.getYear();
      PlayerMoraleEngine.Profile profile = new PlayerMoraleEngine.Profile(role, exp, p.getAge(), finalYear);
      boolean available = p.getRosterStatus() == RosterStatus.ACTIVE && p.getInjuryWeeksOut() == 0;
      PlayerMoraleEngine.State s = PlayerMoraleEngine.start(p.getMorale());
      Double lastShare = null;
      long mySnaps = 0, teamSnaps = 0;
      Unit unit = p.getPosition().unit();
      for (int week : completed) {
        Fixture f = games.get(week);
        if (f == null) { s = PlayerMoraleEngine.week(s, profile, PlayerMoraleEngine.Week.bye()); continue; }
        boolean home = f.getHomeClub().getId().equals(club.getId());
        int pf = home ? f.getHomeScore() : f.getAwayScore(), pa = home ? f.getAwayScore() : f.getHomeScore();
        int sn = snaps.getOrDefault(f.getId(), Map.of()).getOrDefault(p.getId(), 0);
        int us = unit == Unit.SPECIAL_TEAMS ? 0 : unitSnaps.getOrDefault(f.getId(), new int[3])[unit.ordinal()];
        s = PlayerMoraleEngine.week(s, profile, new PlayerMoraleEngine.Week(true, available, sn, us, pf > pa, pf < pa, pf - pa, streakAfter.getOrDefault(week, 0)));
        if (us > 0) { lastShare = Math.min(1.0, round3(sn / (double) us)); mySnaps += sn; teamSnaps += us; }
      }
      states.put(p.getId(), s);
      boolean countsSnaps = role != PlayerMoraleEngine.Role.SPECIALIST && role != PlayerMoraleEngine.Role.RESERVE;
      int m = s.rounded();
      rows.add(new MoraleDtos.PlayerMoraleDto(p.getId(), p.getName(), p.getPosition().name(), p.getJerseyNumber(), p.getRosterStatus().name(), m,
          PlayerMoraleEngine.label(m), trend(s.delta()), s.delta(), role.name(), countsSnaps ? exp : null, countsSnaps ? lastShare : null,
          countsSnaps && teamSnaps > 0 ? round3(mySnaps / (double) teamSnaps) : null, finalYear, yearsLeft, s.tradeRequest(),
          PlayerMoraleEngine.performanceModifier(m), s.factors()));
    }
    rows.sort(Comparator.comparingInt(MoraleDtos.PlayerMoraleDto::morale).thenComparing(MoraleDtos.PlayerMoraleDto::name)
        .thenComparing(r -> r.playerId().toString()));
    List<MoraleDtos.PlayerMoraleDto> main = rows.stream().filter(r -> RosterStatus.valueOf(r.rosterStatus()).countsTowardRoster()).toList();
    int avg = main.isEmpty() ? 0 : (int) Math.round(main.stream().mapToInt(MoraleDtos.PlayerMoraleDto::morale).average().orElse(0));
    double lastDelta = main.isEmpty() ? 0 : Math.round(main.stream().mapToDouble(MoraleDtos.PlayerMoraleDto::lastDelta).average().orElse(0) * 100) / 100.0;
    Map<String, Integer> dist = new LinkedHashMap<>();
    for (String label : List.of("HIGH", "GOOD", "NEUTRAL", "LOW", "CRITICAL")) dist.put(label, 0);
    main.forEach(r -> dist.merge(r.label(), 1, Integer::sum));
    int trades = (int) rows.stream().filter(MoraleDtos.PlayerMoraleDto::tradeRequest).count();
    String streakText = streak == 0 ? null : (streak > 0 ? "W" : "L") + Math.abs(streak);
    int through = completed.isEmpty() ? 0 : completed.get(completed.size() - 1);
    MoraleDtos.SquadMoraleDto squad = new MoraleDtos.SquadMoraleDto(season.getId(), through, avg, PlayerMoraleEngine.label(avg), trend(lastDelta), lastDelta,
        dist, trades, streakText, CareerDtos.RecordDto.of(w, l, t), rows);
    return new Result(squad, states);
  }

  public static MoraleDtos.MoraleSummaryDto summary(MoraleDtos.SquadMoraleDto s) {
    return new MoraleDtos.MoraleSummaryDto(s.average(), s.label(), s.trend(), s.distribution().get("LOW") + s.distribution().get("CRITICAL"), s.tradeRequests());
  }

  /** Moral de partida da temporada seguinte: a final volta 30 % para {@value #SEASON_BASELINE}. */
  public static int nextSeasonMorale(int finalMorale) { return (int) Math.round(finalMorale * 0.7 + SEASON_BASELINE * 0.3); }

  static String trend(double delta) { return delta > 0.5 ? "UP" : delta < -0.5 ? "DOWN" : "STABLE"; }

  static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
}
