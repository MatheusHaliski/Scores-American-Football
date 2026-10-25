package br.com.velion.scoresaf.domain.season;

import br.com.velion.scoresaf.domain.career.SeedPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Classificação da temporada regular (RF16) e sementes dos playoffs (RF66), puras: mesmos jogos e mesma semente ⇒ mesma tabela.
 * Substitui o desempate provisório da D-48 pela cadeia da liga profissional (D-95).
 *
 * <p><b>Ordem.</b> Percentual de vitórias (empate vale meio jogo); franquias com o mesmo percentual passam pela cadeia de desempate,
 * critério a critério. Quando um critério separa só parte do grupo (ou elimina alguém), o grupo que sobrou recomeça do primeiro
 * critério; quando uma franquia é colocada, as demais do grupo recomeçam do primeiro critério (procedimento da liga profissional).
 *
 * <ul>
 *   <li><b>Mesma divisão</b> ({@link #DIVISION_CHAIN}): confronto direto (todos contra todos no grupo), campanha na divisão, jogos em
 *   comum, campanha na conferência, força das vitórias, força da tabela, saldo de pontos, projeção (só sem nenhum jogo: prestígio,
 *   depois nome — a projeção de pré-temporada da D-48) e sorteio pela semente.</li>
 *   <li><b>Divisões diferentes</b> ({@link #WILD_CARD_CHAIN}, wild cards e ordem dos campeões de divisão): antes de tudo, de cada
 *   divisão só a mais bem colocada do grupo segue; depois confronto direto (só se uma franquia venceu todas as outras ou perdeu para
 *   todas), campanha na conferência, jogos em comum (mínimo de {@value #MIN_COMMON_GAMES_WILD_CARD}), força das vitórias, força da
 *   tabela, saldo, projeção e sorteio.</li>
 * </ul>
 *
 * <p><b>Sementes.</b> Por conferência, {@code min(sementes, franquias)} vagas: os campeões de divisão nas primeiras (no máximo uma por
 * divisão), ordenados entre si pela cadeia das divisões diferentes, e as demais vagas para os wild cards. A tabela da conferência
 * segue o mesmo procedimento até a última franquia.
 */
public final class Standings {
  private Standings() {}

  /** Franquia: chave estável (id), conferência e divisão (chaves), prestígio e nome (projeção sem jogos). */
  public record Team(String key, String conference, String division, int prestige, String name) {}

  /** Jogo encerrado da temporada regular. */
  public record Game(int week, String home, String away, int homeScore, int awayScore) {}

  /** Critérios de desempate, na ordem em que podem aparecer. */
  public enum Tiebreak { HEAD_TO_HEAD, DIVISION, COMMON_GAMES, CONFERENCE, STRENGTH_OF_VICTORY, STRENGTH_OF_SCHEDULE, POINT_DIFFERENTIAL, PROJECTION, DRAW }

  public static final List<Tiebreak> DIVISION_CHAIN = List.of(Tiebreak.HEAD_TO_HEAD, Tiebreak.DIVISION, Tiebreak.COMMON_GAMES, Tiebreak.CONFERENCE,
      Tiebreak.STRENGTH_OF_VICTORY, Tiebreak.STRENGTH_OF_SCHEDULE, Tiebreak.POINT_DIFFERENTIAL, Tiebreak.PROJECTION, Tiebreak.DRAW);
  public static final List<Tiebreak> WILD_CARD_CHAIN = List.of(Tiebreak.HEAD_TO_HEAD, Tiebreak.CONFERENCE, Tiebreak.COMMON_GAMES,
      Tiebreak.STRENGTH_OF_VICTORY, Tiebreak.STRENGTH_OF_SCHEDULE, Tiebreak.POINT_DIFFERENTIAL, Tiebreak.PROJECTION, Tiebreak.DRAW);
  public static final int MIN_COMMON_GAMES_WILD_CARD = 4;

  /** Vitórias, derrotas e empates. */
  public record Record(int wins, int losses, int ties) {
    public static final Record ZERO = new Record(0, 0, 0);
    public int games() { return wins + losses + ties; }
    /** Percentual com 3 casas (empate vale meio). */
    public double pct() { return games() == 0 ? 0.0 : Math.round((wins + ties / 2.0) / games() * 1000) / 1000.0; }
    Record plus(int pf, int pa) { return pf > pa ? new Record(wins + 1, losses, ties) : pf < pa ? new Record(wins, losses + 1, ties) : new Record(wins, losses, ties + 1); }
    Record plus(Record o) { return new Record(wins + o.wins, losses + o.losses, ties + o.ties); }
    /** Percentual exato como fração (meios-jogos ganhos / meios-jogos jogados), para comparar sem erro de ponto flutuante. */
    Frac frac() { return games() == 0 ? Frac.ZERO : new Frac(2L * wins + ties, 2L * games()); }
  }

  /** Linha de uma franquia: geral, casa, fora, divisão, conferência, pontos, sequência ({@code W3}, {@code L1}, {@code T1}) e últimos 5. */
  public record Line(Team team, Record overall, Record home, Record away, Record division, Record conference, int pointsFor, int pointsAgainst,
                     String streak, List<String> lastFive) {
    public int pointDiff() { return pointsFor - pointsAgainst; }
  }

  /** Franquia colocada; {@code tiebreak} = critério que a pôs à frente das empatadas logo abaixo ({@code null} sem empate). */
  public record Ranked(Line line, int rank, Tiebreak tiebreak) {}

  /** Semente de playoff da conferência. */
  public record Seed(int seed, String key, boolean divisionChampion, Tiebreak tiebreak) {}

  /**
   * Tabela: linhas por franquia, divisões (chave da divisão → ordem), conferências (chave → ordem completa, sementes primeiro) e
   * sementes por conferência.
   */
  public record Table(Map<String, Line> lines, Map<String, List<Ranked>> divisions, Map<String, List<Ranked>> conferences,
                      Map<String, List<Seed>> seeds) {
    public Line line(String key) { return lines.get(key); }

    /** Semente da franquia na conferência dela, ou {@code null} fora da zona de playoff. */
    public Integer seedOf(String key) {
      for (List<Seed> l : seeds.values()) for (Seed s : l) if (s.key().equals(key)) return s.seed();
      return null;
    }
  }

  /**
   * Monta a tabela.
   *
   * @param teams franquias da liga (com conferência e divisão)
   * @param games jogos encerrados da temporada regular (os de playoff ficam fora)
   * @param seedsPerConference sementes por conferência ({@code LeagueRules.playoffSeedsPerConference})
   * @param drawSeed semente do sorteio (último critério)
   */
  public static Table compute(List<Team> teams, List<Game> games, int seedsPerConference, long drawSeed) {
    Ctx ctx = new Ctx(teams, games, drawSeed);
    Map<String, List<String>> divisionMembers = new LinkedHashMap<>();
    Map<String, List<String>> conferenceDivisions = new LinkedHashMap<>();
    for (Team t : teams) {
      divisionMembers.computeIfAbsent(t.division(), k -> new ArrayList<>()).add(t.key());
      List<String> divs = conferenceDivisions.computeIfAbsent(t.conference(), k -> new ArrayList<>());
      if (!divs.contains(t.division())) divs.add(t.division());
    }
    Map<String, List<Ranked>> divisions = new LinkedHashMap<>();
    divisionMembers.forEach((div, members) -> divisions.put(div, ctx.rankDivision(members)));
    Map<String, List<Ranked>> conferences = new LinkedHashMap<>();
    Map<String, List<Seed>> seeds = new LinkedHashMap<>();
    conferenceDivisions.forEach((conf, divs) -> {
      List<Ranked> order = new ArrayList<>();
      List<Seed> s = new ArrayList<>();
      ctx.seedConference(conf, divs, divisions, seedsPerConference, order, s);
      conferences.put(conf, List.copyOf(order));
      seeds.put(conf, List.copyOf(s));
    });
    return new Table(Map.copyOf(ctx.lines), Map.copyOf(divisions), Map.copyOf(conferences), Map.copyOf(seeds));
  }

  // ===================================================================================================================== cálculo

  /** Fração não negativa comparável sem ponto flutuante. */
  record Frac(long num, long den) implements Comparable<Frac> {
    static final Frac ZERO = new Frac(0, 1);
    @Override public int compareTo(Frac o) { return Long.compare(num * o.den, o.num * den); }
    @Override public boolean equals(Object o) { return o instanceof Frac f && compareTo(f) == 0; }
    @Override public int hashCode() { return Double.hashCode(den == 0 ? 0 : (double) num / den); }
  }

  /** Resultado de um jogo do ponto de vista de uma franquia. */
  private record Result(int week, String opponent, int pointsFor, int pointsAgainst, boolean home) {}

  private record Pick(String key, Tiebreak tiebreak) {}

  private enum Mode { DIVISION, WILD_CARD }

  private static final class Ctx {
    final Map<String, Team> teams = new LinkedHashMap<>();
    final Map<String, List<Result>> results = new HashMap<>();
    final Map<String, Line> lines = new LinkedHashMap<>();
    final long drawSeed;
    /** Posição de cada franquia na divisão (para o filtro "uma por divisão" dos wild cards). */
    final Map<String, Integer> divisionRank = new HashMap<>();
    final Map<String, Tiebreak> divisionTiebreak = new HashMap<>();

    Ctx(List<Team> list, List<Game> games, long drawSeed) {
      this.drawSeed = drawSeed;
      for (Team t : list) { teams.put(t.key(), t); results.put(t.key(), new ArrayList<>()); }
      List<Game> sorted = new ArrayList<>(games);
      sorted.sort(Comparator.comparingInt(Game::week));
      for (Game g : sorted) {
        if (!teams.containsKey(g.home()) || !teams.containsKey(g.away())) continue;
        results.get(g.home()).add(new Result(g.week(), g.away(), g.homeScore(), g.awayScore(), true));
        results.get(g.away()).add(new Result(g.week(), g.home(), g.awayScore(), g.homeScore(), false));
      }
      for (Team t : list) lines.put(t.key(), line(t));
    }

    Line line(Team t) {
      Record all = Record.ZERO, home = Record.ZERO, away = Record.ZERO, div = Record.ZERO, conf = Record.ZERO;
      int pf = 0, pa = 0;
      List<String> marks = new ArrayList<>();
      for (Result r : results.get(t.key())) {
        Team o = teams.get(r.opponent());
        all = all.plus(r.pointsFor(), r.pointsAgainst());
        if (r.home()) home = home.plus(r.pointsFor(), r.pointsAgainst()); else away = away.plus(r.pointsFor(), r.pointsAgainst());
        if (o.division().equals(t.division())) div = div.plus(r.pointsFor(), r.pointsAgainst());
        if (o.conference().equals(t.conference())) conf = conf.plus(r.pointsFor(), r.pointsAgainst());
        pf += r.pointsFor(); pa += r.pointsAgainst();
        marks.add(r.pointsFor() > r.pointsAgainst() ? "W" : r.pointsFor() < r.pointsAgainst() ? "L" : "T");
      }
      String streak = "";
      if (!marks.isEmpty()) {
        String last = marks.get(marks.size() - 1);
        int n = 0;
        for (int i = marks.size() - 1; i >= 0 && marks.get(i).equals(last); i--) n++;
        streak = last + n;
      }
      List<String> lastFive = List.copyOf(marks.subList(Math.max(0, marks.size() - 5), marks.size()));
      return new Line(t, all, home, away, div, conf, pf, pa, streak, lastFive);
    }

    Frac pct(String key) { return lines.get(key).overall().frac(); }

    // ---------------------------------------------------------------- divisão

    List<Ranked> rankDivision(List<String> members) {
      List<Ranked> out = new ArrayList<>();
      for (List<String> group : byPct(members)) {
        List<String> remaining = new ArrayList<>(group);
        while (!remaining.isEmpty()) {
          Pick p = remaining.size() == 1 ? new Pick(remaining.get(0), null) : pickBest(remaining, Mode.DIVISION);
          out.add(new Ranked(lines.get(p.key()), out.size() + 1, p.tiebreak()));
          divisionRank.put(p.key(), out.size());
          divisionTiebreak.put(p.key(), p.tiebreak());
          remaining.remove(p.key());
        }
      }
      return List.copyOf(out);
    }

    /** Grupos de franquias com o mesmo percentual, do maior para o menor (ordem estável pela chave dentro do grupo). */
    List<List<String>> byPct(List<String> keys) {
      List<String> sorted = new ArrayList<>(keys);
      sorted.sort(Comparator.comparing((String k) -> pct(k)).reversed().thenComparing(k -> k));
      List<List<String>> groups = new ArrayList<>();
      for (String k : sorted) {
        if (groups.isEmpty() || pct(groups.get(groups.size() - 1).get(0)).compareTo(pct(k)) != 0) groups.add(new ArrayList<>());
        groups.get(groups.size() - 1).add(k);
      }
      return groups;
    }

    // ---------------------------------------------------------------- conferência e sementes

    void seedConference(String conf, List<String> divs, Map<String, List<Ranked>> divisions, int seedsPerConference, List<Ranked> order, List<Seed> seeds) {
      int size = (int) teams.values().stream().filter(t -> t.conference().equals(conf)).count();
      int slots = Math.max(0, Math.min(seedsPerConference, size));
      List<String> champions = new ArrayList<>();
      for (String d : divs) { List<Ranked> r = divisions.get(d); if (r != null && !r.isEmpty()) champions.add(r.get(0).line().team().key()); }
      int championSlots = Math.min(champions.size(), slots);
      // campeões de divisão nas primeiras sementes (no máximo uma por divisão e até as vagas), ordenados entre si pela cadeia de
      // divisões diferentes; um campeão sem vaga (mais divisões que sementes) volta para a disputa dos wild cards
      List<String> remainingChampions = new ArrayList<>(champions);
      Set<String> placed = new LinkedHashSet<>();
      while (seeds.size() < championSlots) {
        Pick p = pickFromPctGroups(remainingChampions);
        seeds.add(new Seed(seeds.size() + 1, p.key(), true, p.tiebreak()));
        order.add(new Ranked(lines.get(p.key()), order.size() + 1, p.tiebreak()));
        placed.add(p.key());
        remainingChampions.remove(p.key());
      }
      List<String> rest = new ArrayList<>();
      for (Team t : teams.values()) if (t.conference().equals(conf) && !placed.contains(t.key())) rest.add(t.key());
      while (!rest.isEmpty()) {
        Pick p = pickFromPctGroups(rest);
        if (seeds.size() < slots) seeds.add(new Seed(seeds.size() + 1, p.key(), false, p.tiebreak()));
        order.add(new Ranked(lines.get(p.key()), order.size() + 1, p.tiebreak()));
        rest.remove(p.key());
      }
    }

    /**
     * A melhor franquia de um conjunto de divisões diferentes (ou misturadas): o grupo do maior percentual; dele, só a mais bem
     * colocada de cada divisão segue; entre essas, a cadeia de divisões diferentes.
     */
    Pick pickFromPctGroups(List<String> candidates) {
      List<String> top = byPct(candidates).get(0);
      if (top.size() == 1) return new Pick(top.get(0), null);
      Map<String, String> bestOfDivision = new LinkedHashMap<>();
      for (String k : top) {
        String d = teams.get(k).division();
        String cur = bestOfDivision.get(d);
        if (cur == null || divisionRank.get(k) < divisionRank.get(cur)) bestOfDivision.put(d, k);
      }
      List<String> eligible = new ArrayList<>(bestOfDivision.values());
      if (eligible.size() == 1) {
        String k = eligible.get(0);
        Tiebreak tb = divisionTiebreak.get(k);
        return new Pick(k, tb == null ? Tiebreak.DIVISION : tb);
      }
      return pickBest(eligible, Mode.WILD_CARD);
    }

    // ---------------------------------------------------------------- cadeia

    Pick pickBest(List<String> group, Mode mode) {
      List<Tiebreak> chain = mode == Mode.DIVISION ? DIVISION_CHAIN : WILD_CARD_CHAIN;
      for (Tiebreak c : chain) {
        if (c == Tiebreak.PROJECTION) {
          if (group.stream().anyMatch(k -> lines.get(k).overall().games() > 0)) continue;
          String best = group.stream().min(Comparator.comparingInt((String k) -> -teams.get(k).prestige()).thenComparing(k -> teams.get(k).name())
              .thenComparing(k -> k)).orElseThrow();
          return new Pick(best, c);
        }
        if (c == Tiebreak.DRAW) {
          // pelo nome (estável no mundo clonado, como o calendário), nunca pelo id
          String best = group.stream().min(Comparator.comparingLong((String k) -> SeedPolicy.derive(drawSeed, "tiebreak", teams.get(k).name().hashCode()))
              .thenComparing(k -> teams.get(k).name()).thenComparing(k -> k)).orElseThrow();
          return new Pick(best, c);
        }
        Map<String, Frac> v = values(c, group, mode);
        if (v == null) continue;
        Frac max = v.values().stream().max(Comparator.naturalOrder()).orElseThrow();
        List<String> top = group.stream().filter(k -> v.get(k).compareTo(max) == 0).toList();
        if (top.size() == 1) return new Pick(top.get(0), c);
        if (top.size() < group.size()) {
          Pick inner = pickBest(new ArrayList<>(top), mode);
          return new Pick(inner.key(), inner.tiebreak());
        }
      }
      throw new IllegalStateException("cadeia de desempate sem decisão");
    }

    /** Valor de cada franquia no critério; {@code null} se o critério não se aplica ao grupo. */
    Map<String, Frac> values(Tiebreak c, List<String> group, Mode mode) {
      Map<String, Frac> out = new LinkedHashMap<>();
      switch (c) {
        case HEAD_TO_HEAD -> { return headToHead(group, mode); }
        case DIVISION -> {
          String d = teams.get(group.get(0)).division();
          if (group.stream().anyMatch(k -> !teams.get(k).division().equals(d))) return null;
          group.forEach(k -> out.put(k, lines.get(k).division().frac()));
        }
        case CONFERENCE -> group.forEach(k -> out.put(k, lines.get(k).conference().frac()));
        case COMMON_GAMES -> { return commonGames(group, mode); }
        case STRENGTH_OF_VICTORY -> group.forEach(k -> out.put(k, opponents(k, true).frac()));
        case STRENGTH_OF_SCHEDULE -> group.forEach(k -> out.put(k, opponents(k, false).frac()));
        case POINT_DIFFERENTIAL -> {
          // saldo deslocado para não negativo (a fração compara numeradores com o mesmo denominador)
          group.forEach(k -> out.put(k, new Frac(lines.get(k).pointDiff() + 100_000L, 1)));
        }
        default -> { return null; }
      }
      return out;
    }

    /**
     * Confronto direto. Divisão (ou 2 franquias): percentual nos jogos entre as franquias do grupo, se cada par se enfrentou. Divisões
     * diferentes com 3+: só se uma venceu todas as outras (fica na frente) ou perdeu para todas (sai do grupo).
     */
    Map<String, Frac> headToHead(List<String> group, Mode mode) {
      Set<String> g = new HashSet<>(group);
      Map<String, Map<String, Record>> vs = new HashMap<>();
      for (String k : group) {
        Map<String, Record> m = new HashMap<>();
        for (Result r : results.get(k)) if (g.contains(r.opponent())) m.merge(r.opponent(), Record.ZERO.plus(r.pointsFor(), r.pointsAgainst()), Record::plus);
        vs.put(k, m);
      }
      for (String a : group) for (String b : group) if (!a.equals(b) && !vs.get(a).containsKey(b)) {
        if (mode == Mode.DIVISION || group.size() == 2) return null;
      }
      Map<String, Frac> out = new LinkedHashMap<>();
      if (mode == Mode.DIVISION || group.size() == 2) {
        for (String k : group) {
          Record total = Record.ZERO;
          for (Record r : vs.get(k).values()) total = total.plus(r);
          out.put(k, total.frac());
        }
        return out;
      }
      String sweeper = null;
      List<String> swept = new ArrayList<>();
      for (String k : group) {
        boolean beatAll = true, lostAll = true;
        for (String o : group) {
          if (o.equals(k)) continue;
          Record r = vs.get(k).get(o);
          if (r == null || r.wins() == 0 || r.losses() > 0 || r.ties() > 0) beatAll = false;
          if (r == null || r.losses() == 0 || r.wins() > 0 || r.ties() > 0) lostAll = false;
        }
        if (beatAll) sweeper = k;
        if (lostAll) swept.add(k);
      }
      if (sweeper != null) { for (String k : group) out.put(k, k.equals(sweeper) ? new Frac(1, 1) : Frac.ZERO); return out; }
      if (!swept.isEmpty() && swept.size() < group.size()) { for (String k : group) out.put(k, swept.contains(k) ? Frac.ZERO : new Frac(1, 1)); return out; }
      return null;
    }

    /** Campanha contra os adversários que todas as franquias do grupo enfrentaram (wild card: pelo menos 4 jogos de cada uma). */
    Map<String, Frac> commonGames(List<String> group, Mode mode) {
      Set<String> common = null;
      for (String k : group) {
        Set<String> opp = new HashSet<>();
        for (Result r : results.get(k)) opp.add(r.opponent());
        if (common == null) common = opp; else common.retainAll(opp);
      }
      if (common == null) return null;
      common.removeAll(group);
      if (common.isEmpty()) return null;
      Map<String, Frac> out = new LinkedHashMap<>();
      for (String k : group) {
        Record r = Record.ZERO;
        for (Result x : results.get(k)) if (common.contains(x.opponent())) r = r.plus(x.pointsFor(), x.pointsAgainst());
        if (mode == Mode.WILD_CARD && r.games() < MIN_COMMON_GAMES_WILD_CARD) return null;
        out.put(k, r.frac());
      }
      return out;
    }

    /** Campanha somada dos adversários batidos ({@code victories}) ou de todos os enfrentados, um por jogo. */
    Record opponents(String key, boolean victories) {
      Record sum = Record.ZERO;
      for (Result r : results.get(key)) {
        if (victories && r.pointsFor() <= r.pointsAgainst()) continue;
        sum = sum.plus(lines.get(r.opponent()).overall());
      }
      return sum;
    }
  }
}
