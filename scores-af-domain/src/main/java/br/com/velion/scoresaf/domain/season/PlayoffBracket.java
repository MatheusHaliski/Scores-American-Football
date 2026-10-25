package br.com.velion.scoresaf.domain.season;

import br.com.velion.scoresaf.domain.shared.WeekKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chave dos playoffs (RF66), pura: sementes por conferência (as da {@link Standings}), folga das melhores sementes na primeira rodada
 * ({@code LeagueRules.playoffByeSeeds}), re-seed a cada rodada (a melhor semente viva enfrenta a pior), mando da melhor semente e final
 * entre os campeões de conferência, em campo neutro quando {@code LeagueRules.neutralSiteFinal}. Liga de uma conferência: a última
 * rodada da conferência é a final.
 *
 * <p>Rodada {@code r}: na primeira, as {@code byes} melhores sementes folgam (mais uma, se sobrar número ímpar de franquias); depois,
 * vivas = vencedoras da rodada anterior + as que folgaram; número ímpar de vivas dá folga à melhor semente. Uma conferência que já
 * tem campeã espera a outra. Os nomes das rodadas contam do fim: ... WILD_CARD, DIVISIONAL, CONFERENCE, FINAL.
 */
public final class PlayoffBracket {
  private PlayoffBracket() {}

  /** Semente na chave: franquia, conferência, número e se é campeã de divisão. */
  public record Seed(String club, String conference, int seed, boolean divisionChampion) {}

  /** Jogo da chave: conferência ({@code null} na final entre conferências), mandante, visitante, sementes e campo neutro. */
  public record Matchup(String conference, String home, String away, int homeSeed, int awaySeed, boolean neutralSite) {}

  /** Rodada planejada: índice (0 = primeira), tipo, jogos e as franquias que folgam. */
  public record Round(int index, WeekKind kind, List<Matchup> games, List<Seed> byes) {}

  /** Vencedor de um jogo já disputado (rodada, mandante, visitante, vencedor). */
  public record Result(int round, String home, String away, String winner) {}

  /** Número de rodadas da chave (conferências + final, quando há duas conferências). */
  public static int rounds(Map<String, Integer> seedsPerConference, int byes) {
    int confRounds = 0;
    for (int n : seedsPerConference.values()) confRounds = Math.max(confRounds, conferenceRounds(n, byes));
    int conferences = (int) seedsPerConference.values().stream().filter(n -> n > 0).count();
    return conferences > 1 ? confRounds + 1 : confRounds;
  }

  /** Rodadas para uma conferência com {@code n} sementes chegar à campeã. */
  static int conferenceRounds(int n, int byes) {
    int alive = n, r = 0;
    boolean first = true;
    while (alive > 1) {
      int bye = first ? Math.min(Math.max(0, byes), alive) : 0;
      if ((alive - bye) % 2 == 1) bye++;
      if (bye >= alive) bye = alive - 2;
      int playing = alive - bye;
      alive = bye + playing / 2;
      r++;
      first = false;
    }
    return r;
  }

  /** Tipo de cada rodada, contando do fim (a última é a final). */
  public static List<WeekKind> kinds(int rounds) {
    List<WeekKind> all = List.of(WeekKind.WILD_CARD, WeekKind.DIVISIONAL, WeekKind.CONFERENCE, WeekKind.FINAL);
    if (rounds > all.size()) throw new IllegalArgumentException("chave com rodadas demais: " + rounds);
    return all.subList(all.size() - rounds, all.size());
  }

  /**
   * Próxima rodada a partir das sementes e dos resultados das anteriores ({@code results}); {@code null} quando a chave acabou (há
   * campeã). {@code byes}: sementes que folgam na primeira rodada.
   */
  public static Round next(List<Seed> seeds, int byes, boolean neutralSiteFinal, List<Result> results) {
    Map<String, Integer> perConf = new LinkedHashMap<>();
    for (Seed s : seeds) perConf.merge(s.conference(), 1, Integer::sum);
    int total = rounds(perConf, byes);
    if (total == 0) return null;
    int round = results.stream().mapToInt(Result::round).max().orElse(-1) + 1;
    if (round >= total) return null;
    WeekKind kind = kinds(total).get(round);
    Map<String, List<Seed>> alive = alive(seeds, byes, results, round);
    boolean multipleConferences = perConf.size() > 1;
    boolean finalRound = round == total - 1;
    List<Matchup> games = new ArrayList<>();
    List<Seed> roundByes = new ArrayList<>();
    if (finalRound && multipleConferences) {
      List<Seed> champs = new ArrayList<>();
      alive.values().forEach(l -> champs.addAll(l));
      champs.sort(Comparator.comparingInt(Seed::seed));
      if (champs.size() == 2) {
        Seed h = champs.get(0), a = champs.get(1);
        games.add(new Matchup(null, h.club(), a.club(), h.seed(), a.seed(), neutralSiteFinal));
      }
      return new Round(round, kind, games, roundByes);
    }
    for (Map.Entry<String, List<Seed>> e : alive.entrySet()) {
      List<Seed> l = new ArrayList<>(e.getValue());
      l.sort(Comparator.comparingInt(Seed::seed));
      if (l.size() <= 1) { roundByes.addAll(l); continue; }
      int bye = round == 0 ? Math.min(Math.max(0, byes), l.size()) : 0;
      if ((l.size() - bye) % 2 == 1) bye++;
      if (bye >= l.size()) bye = l.size() - 2;
      roundByes.addAll(l.subList(0, bye));
      List<Seed> playing = l.subList(bye, l.size());
      boolean neutral = finalRound && neutralSiteFinal;
      for (int i = 0; i < playing.size() / 2; i++) {
        Seed h = playing.get(i), a = playing.get(playing.size() - 1 - i);
        games.add(new Matchup(e.getKey(), h.club(), a.club(), h.seed(), a.seed(), neutral));
      }
    }
    return new Round(round, kind, games, roundByes);
  }

  /** Vivas por conferência antes da rodada {@code round}. */
  static Map<String, List<Seed>> alive(List<Seed> seeds, int byes, List<Result> results, int round) {
    Map<String, List<Seed>> alive = new LinkedHashMap<>();
    for (Seed s : seeds) alive.computeIfAbsent(s.conference(), k -> new ArrayList<>()).add(s);
    for (int r = 0; r < round; r++) {
      final int rr = r;
      Set<String> losers = new java.util.HashSet<>();
      for (Result x : results) if (x.round() == rr) losers.add(x.winner().equals(x.home()) ? x.away() : x.home());
      for (List<Seed> l : alive.values()) l.removeIf(s -> losers.contains(s.club()));
    }
    return alive;
  }

  /** Campeã quando a final já tem resultado; {@code null} antes. */
  public static String champion(List<Seed> seeds, int byes, List<Result> results) {
    Map<String, Integer> perConf = new LinkedHashMap<>();
    for (Seed s : seeds) perConf.merge(s.conference(), 1, Integer::sum);
    int total = rounds(perConf, byes);
    if (total == 0) return seeds.stream().min(Comparator.comparingInt(Seed::seed)).map(Seed::club).orElse(null);
    return results.stream().filter(r -> r.round() == total - 1).map(Result::winner).findFirst().orElse(null);
  }
}
