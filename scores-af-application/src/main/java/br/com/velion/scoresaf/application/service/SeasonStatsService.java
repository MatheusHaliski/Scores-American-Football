package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerMatchStatsRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerSeasonTotals;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonAwardRepository;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.SeasonAward;
import br.com.velion.scoresaf.domain.season.SeasonAwards;
import br.com.velion.scoresaf.domain.season.Standings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Estatísticas da temporada regular por jogador (consulta agregada do box score) para os prêmios e os líderes (RF35, D-98), e a
 * gravação dos prêmios quando a final termina. Jogador que mudou de franquia na temporada soma tudo e fica com a franquia em que jogou
 * mais jogos. Calouro = sem experiência ({@code experienceYears == 0}).
 */
@Component
public class SeasonStatsService {
  private final PlayerMatchStatsRepository stats; private final PlayerRepository players; private final SeasonAwardRepository awards;

  public SeasonStatsService(PlayerMatchStatsRepository stats, PlayerRepository players, SeasonAwardRepository awards) {
    this.stats = stats; this.players = players; this.awards = awards;
  }

  public List<SeasonAwards.PlayerLine> lines(Season season) {
    List<PlayerSeasonTotals> rows = stats.totalsBySeason(season.getId(), season.getRegularSeasonWeeks());
    Map<UUID, List<PlayerSeasonTotals>> byPlayer = new LinkedHashMap<>();
    rows.forEach(r -> byPlayer.computeIfAbsent(r.playerId(), k -> new ArrayList<>()).add(r));
    Map<UUID, Player> info = players.findAllById(byPlayer.keySet()).stream().collect(Collectors.toMap(Player::getId, Function.identity()));
    List<SeasonAwards.PlayerLine> out = new ArrayList<>();
    for (Map.Entry<UUID, List<PlayerSeasonTotals>> e : byPlayer.entrySet()) {
      Player p = info.get(e.getKey());
      if (p == null) continue;
      List<PlayerSeasonTotals> l = e.getValue();
      UUID club = l.stream().max(java.util.Comparator.comparingLong(PlayerSeasonTotals::games).thenComparing(r -> r.clubId().toString()))
          .map(PlayerSeasonTotals::clubId).orElse(null);
      out.add(new SeasonAwards.PlayerLine(p.getId().toString(), club == null ? null : club.toString(), p.getName(), p.getPosition().name(),
          p.getExperienceYears() == 0, (int) sum(l, PlayerSeasonTotals::games),
          (int) sum(l, PlayerSeasonTotals::passAttempts), (int) sum(l, PlayerSeasonTotals::passCompletions), (int) sum(l, PlayerSeasonTotals::passYards),
          (int) sum(l, PlayerSeasonTotals::passTouchdowns), (int) sum(l, PlayerSeasonTotals::interceptionsThrown), (int) sum(l, PlayerSeasonTotals::sacked),
          (int) sum(l, PlayerSeasonTotals::rushAttempts), (int) sum(l, PlayerSeasonTotals::rushYards), (int) sum(l, PlayerSeasonTotals::rushTouchdowns),
          (int) sum(l, PlayerSeasonTotals::fumblesLost), (int) sum(l, PlayerSeasonTotals::targets), (int) sum(l, PlayerSeasonTotals::receptions),
          (int) sum(l, PlayerSeasonTotals::receivingYards), (int) sum(l, PlayerSeasonTotals::receivingTouchdowns), (int) sum(l, PlayerSeasonTotals::soloTackles),
          (int) sum(l, PlayerSeasonTotals::tacklesForLoss), (int) sum(l, PlayerSeasonTotals::sacks), (int) sum(l, PlayerSeasonTotals::interceptions),
          (int) sum(l, PlayerSeasonTotals::passesDefended), (int) sum(l, PlayerSeasonTotals::forcedFumbles), (int) sum(l, PlayerSeasonTotals::fumbleRecoveries),
          (int) sum(l, PlayerSeasonTotals::fieldGoalsMade), (int) sum(l, PlayerSeasonTotals::fieldGoalAttempts),
          (int) sum(l, PlayerSeasonTotals::extraPointsMade), (int) sum(l, PlayerSeasonTotals::extraPointAttempts), (int) sum(l, PlayerSeasonTotals::punts),
          (int) sum(l, PlayerSeasonTotals::puntYards)));
    }
    return out;
  }

  private static long sum(List<PlayerSeasonTotals> l, java.util.function.ToLongFunction<PlayerSeasonTotals> f) { return l.stream().mapToLong(f).sum(); }

  /** Franquias na temporada para o head coach do ano: campanha da tabela e prestígio. */
  public static List<SeasonAwards.TeamLine> teams(StandingsService.League league, Standings.Table table) {
    List<SeasonAwards.TeamLine> out = new ArrayList<>();
    for (Club c : league.clubs()) {
      Standings.Line l = table.line(c.getId().toString());
      out.add(new SeasonAwards.TeamLine(c.getId().toString(), c.getManager() == null ? null : c.getManager().getName(), l.overall().wins(),
          l.overall().games(), l.overall().pct(), c.getPrestige()));
    }
    return out;
  }

  /** Grava os prêmios da temporada (uma vez: temporada que já tem prêmios não muda). */
  public List<SeasonAward> persist(Season season, StandingsService.League league, Standings.Table table) {
    List<SeasonAward> existing = awards.findBySeasonId(season.getId());
    if (!existing.isEmpty()) return existing;
    List<SeasonAwards.Award> computed = SeasonAwards.compute(lines(season), teams(league, table), season.getGamesPerTeam());
    Map<UUID, Player> byId = new HashMap<>();
    List<UUID> ids = computed.stream().filter(a -> a.playerId() != null).map(a -> UUID.fromString(a.playerId())).toList();
    players.findAllById(ids).forEach(p -> byId.put(p.getId(), p));
    List<SeasonAward> out = new ArrayList<>();
    for (SeasonAwards.Award a : computed) {
      SeasonAward e = new SeasonAward();
      e.setSeason(season); e.setType(a.type()); e.setPlayer(a.playerId() == null ? null : byId.get(UUID.fromString(a.playerId())));
      e.setClub(league.byId().get(UUID.fromString(a.clubId()))); e.setWinnerName(a.name()); e.setPosition(a.position()); e.setScore(a.score());
      try {
        e.setStatLine(Jsons.MAPPER.writeValueAsString(a.statLine()));
      } catch (Exception ex) {
        throw new IllegalStateException("linha do prêmio não serializou", ex);
      }
      out.add(e);
    }
    return awards.saveAll(out);
  }
}
