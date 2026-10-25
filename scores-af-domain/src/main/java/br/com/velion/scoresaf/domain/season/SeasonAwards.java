package br.com.velion.scoresaf.domain.season;

import br.com.velion.scoresaf.domain.shared.AwardType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Prêmios individuais e líderes da temporada regular (RF35, D-98), puros, só com o que o motor gera (D-35).
 *
 * <ul>
 *   <li><b>Produção ofensiva</b>: jardas de passe ÷ 25 + 4 × TD de passe − 2 × interceptação lançada + jardas de corrida ÷ 10 + 6 × TD
 *   de corrida + jardas de recepção ÷ 10 + 6 × TD de recepção − 2 × fumble perdido.</li>
 *   <li><b>Produção defensiva</b>: tackles + 1,5 × tackle para perda + 4 × sack + 5 × interceptação + 1,5 × passe defendido + 3 ×
 *   fumble forçado + 2 × fumble recuperado.</li>
 *   <li><b>MVP</b>: produção ofensiva × (0,6 + percentual de vitórias da franquia). <b>OPOY</b>/<b>DPOY</b>: maior produção ofensiva /
 *   defensiva. <b>OROY</b>/<b>DROY</b>: o mesmo entre os calouros (sem temporada anterior). Elegível: jogou pelo menos 2/3 dos jogos
 *   da temporada (arredondado para cima). <b>COACH</b>: head coach da franquia que mais superou a projeção (vitórias − jogos ×
 *   prestígio ÷ 100; empate: mais vitórias).</li>
 *   <li>Empates: maior produção, depois menos jogos, nome e id.</li>
 * </ul>
 */
public final class SeasonAwards {
  private SeasonAwards() {}

  /** Estatísticas somadas de um jogador na temporada regular. {@code games} = jogos com pelo menos um snap. */
  public record PlayerLine(String playerId, String clubId, String name, String position, boolean rookie, int games,
                           int passAttempts, int passCompletions, int passYards, int passTouchdowns, int interceptionsThrown, int sacked,
                           int rushAttempts, int rushYards, int rushTouchdowns, int fumblesLost,
                           int targets, int receptions, int receivingYards, int receivingTouchdowns,
                           int soloTackles, int tacklesForLoss, int sacks, int interceptions, int passesDefended, int forcedFumbles, int fumbleRecoveries,
                           int fieldGoalsMade, int fieldGoalAttempts, int extraPointsMade, int extraPointAttempts, int punts, int puntYards) {
    public double offense() {
      return passYards / 25.0 + 4 * passTouchdowns - 2 * interceptionsThrown + rushYards / 10.0 + 6 * rushTouchdowns + receivingYards / 10.0
          + 6 * receivingTouchdowns - 2 * fumblesLost;
    }
    public double defense() {
      return soloTackles + 1.5 * tacklesForLoss + 4 * sacks + 5 * interceptions + 1.5 * passesDefended + 3 * forcedFumbles + 2 * fumbleRecoveries;
    }
  }

  /** Franquia na temporada: head coach, vitórias, jogos, percentual e prestígio. */
  public record TeamLine(String clubId, String coachName, int wins, int games, double winPct, int prestige) {}

  /** Prêmio: tipo, jogador ({@code null} no COACH), franquia, nome, posição, pontuação e as estatísticas que o sustentam. */
  public record Award(AwardType type, String playerId, String clubId, String name, String position, double score, Map<String, Integer> statLine) {}

  /** Líder de uma categoria. */
  public record Leader(String playerId, String clubId, String name, String position, int value, int games) {}

  public static final List<String> LEADER_CATEGORIES = List.of("PASS_YARDS", "PASS_TOUCHDOWNS", "RUSH_YARDS", "RUSH_TOUCHDOWNS", "RECEIVING_YARDS",
      "RECEPTIONS", "SACKS", "INTERCEPTIONS", "TACKLES");

  public static int minGames(int gamesPerTeam) { return Math.max(1, (gamesPerTeam * 2 + 2) / 3); }

  public static List<Award> compute(List<PlayerLine> players, List<TeamLine> teams, int gamesPerTeam) {
    Map<String, TeamLine> byClub = new LinkedHashMap<>();
    teams.forEach(t -> byClub.put(t.clubId(), t));
    int min = minGames(gamesPerTeam);
    List<PlayerLine> eligible = players.stream().filter(p -> p.games() >= min).toList();
    List<Award> out = new ArrayList<>();
    ToDoubleFunction<PlayerLine> mvp = p -> p.offense() * (0.6 + (byClub.containsKey(p.clubId()) ? byClub.get(p.clubId()).winPct() : 0));
    best(eligible, mvp).ifPresent(p -> out.add(award(AwardType.MVP, p, mvp.applyAsDouble(p), true)));
    best(eligible, PlayerLine::offense).ifPresent(p -> out.add(award(AwardType.OPOY, p, p.offense(), true)));
    best(eligible, PlayerLine::defense).ifPresent(p -> out.add(award(AwardType.DPOY, p, p.defense(), false)));
    List<PlayerLine> rookies = eligible.stream().filter(PlayerLine::rookie).toList();
    best(rookies, PlayerLine::offense).ifPresent(p -> out.add(award(AwardType.OROY, p, p.offense(), true)));
    best(rookies, PlayerLine::defense).ifPresent(p -> out.add(award(AwardType.DROY, p, p.defense(), false)));
    teams.stream().filter(t -> t.games() > 0 && t.coachName() != null)
        .max(Comparator.comparingDouble((TeamLine t) -> t.wins() - t.games() * t.prestige() / 100.0).thenComparingInt(TeamLine::wins)
            .thenComparing(TeamLine::coachName, Comparator.reverseOrder()).thenComparing(TeamLine::clubId, Comparator.reverseOrder()))
        .ifPresent(t -> out.add(new Award(AwardType.COACH, null, t.clubId(), t.coachName(), null,
            round1(t.wins() - t.games() * t.prestige() / 100.0), coachLine(t))));
    return List.copyOf(out);
  }

  private static Map<String, Integer> coachLine(TeamLine t) {
    Map<String, Integer> s = new LinkedHashMap<>();
    s.put("games", t.games());
    s.put("wins", t.wins());
    s.put("prestige", t.prestige());
    return s;
  }

  private static java.util.Optional<PlayerLine> best(List<PlayerLine> list, ToDoubleFunction<PlayerLine> f) {
    return list.stream().filter(p -> f.applyAsDouble(p) > 0)
        .min(Comparator.comparingDouble((PlayerLine p) -> -f.applyAsDouble(p)).thenComparingInt(PlayerLine::games).thenComparing(PlayerLine::name)
            .thenComparing(PlayerLine::playerId));
  }

  private static Award award(AwardType type, PlayerLine p, double score, boolean offense) {
    Map<String, Integer> s = new LinkedHashMap<>();
    s.put("games", p.games());
    if (offense) {
      if (p.passAttempts() > 0) { s.put("passYards", p.passYards()); s.put("passTouchdowns", p.passTouchdowns()); s.put("interceptionsThrown", p.interceptionsThrown()); }
      if (p.rushAttempts() > 0) { s.put("rushYards", p.rushYards()); s.put("rushTouchdowns", p.rushTouchdowns()); }
      if (p.receptions() > 0) { s.put("receptions", p.receptions()); s.put("receivingYards", p.receivingYards()); s.put("receivingTouchdowns", p.receivingTouchdowns()); }
    } else {
      s.put("soloTackles", p.soloTackles()); s.put("sacks", p.sacks()); s.put("interceptions", p.interceptions()); s.put("forcedFumbles", p.forcedFumbles());
    }
    return new Award(type, p.playerId(), p.clubId(), p.name(), p.position(), round1(score), s);
  }

  /** Os {@code limit} líderes de cada categoria (só quem registrou algo). */
  public static Map<String, List<Leader>> leaders(List<PlayerLine> players, int limit) {
    Map<String, List<Leader>> out = new LinkedHashMap<>();
    out.put("PASS_YARDS", top(players, PlayerLine::passYards, limit));
    out.put("PASS_TOUCHDOWNS", top(players, PlayerLine::passTouchdowns, limit));
    out.put("RUSH_YARDS", top(players, PlayerLine::rushYards, limit));
    out.put("RUSH_TOUCHDOWNS", top(players, PlayerLine::rushTouchdowns, limit));
    out.put("RECEIVING_YARDS", top(players, PlayerLine::receivingYards, limit));
    out.put("RECEPTIONS", top(players, PlayerLine::receptions, limit));
    out.put("SACKS", top(players, PlayerLine::sacks, limit));
    out.put("INTERCEPTIONS", top(players, PlayerLine::interceptions, limit));
    out.put("TACKLES", top(players, PlayerLine::soloTackles, limit));
    return out;
  }

  private static List<Leader> top(List<PlayerLine> players, ToIntFunction<PlayerLine> f, int limit) {
    return players.stream().filter(p -> f.applyAsInt(p) > 0)
        .sorted(Comparator.comparingInt((PlayerLine p) -> -f.applyAsInt(p)).thenComparingInt(PlayerLine::games).thenComparing(PlayerLine::name)
            .thenComparing(PlayerLine::playerId))
        .limit(limit).map(p -> new Leader(p.playerId(), p.clubId(), p.name(), p.position(), f.applyAsInt(p), p.games())).toList();
  }

  static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
