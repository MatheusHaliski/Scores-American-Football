package br.com.velion.scoresaf.domain.season;

import br.com.velion.scoresaf.domain.career.SeedPolicy;
import br.com.velion.scoresaf.domain.shared.KickoffSlot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Calendário da temporada regular (RF21, RF34), determinístico pela semente: mesma liga (mesmas franquias nas mesmas divisões, na
 * mesma ordem de colocação) e mesma semente ⇒ mesmo calendário, em qualquer JVM. Puro: nada de banco nem relógio.
 *
 * <p><b>Confrontos.</b> Cada franquia joga {@code gamesPerTeam} jogos. Rivais de divisão se enfrentam duas vezes, uma em casa e
 * outra fora, enquanto os jogos de divisão não passam de 2/3 da temporada (divisões de até 6 franquias com 17 jogos); divisões
 * maiores (editor) se enfrentam uma vez. Na liga padrão (2 conferências × 4 divisões × 4 franquias, 17 jogos) vale a fórmula da liga profissional: 6 jogos de
 * divisão, 4 contra uma divisão da própria conferência (rodízio), 4 contra uma divisão da outra conferência (rodízio), 2 contra as
 * franquias de mesma colocação das outras duas divisões da conferência e o 17º contra a de mesma colocação de mais uma divisão da
 * outra conferência. Em ligas com outro formato (8–32 franquias, editor RF5), os jogos que faltam depois da divisão saem primeiro da
 * própria conferência, depois da outra e só então repetem adversário. O mando dos jogos fora da divisão é equilibrado por um
 * circuito euleriano: cada franquia fica com metade dos jogos em casa (±1).
 *
 * <p><b>Semanas.</b> {@code weeks} semanas; cada franquia folga {@code weeks − gamesPerTeam} vezes (1 bye no padrão), sempre no
 * miolo da temporada (semanas {@code weeks/4 + 1} a {@code weeks − weeks/4}), e nunca joga duas vezes na mesma semana. A distribuição
 * é uma busca com retrocesso (escolhe primeiro a franquia com menos adversários possíveis), evitando repetir o adversário da semana
 * anterior; se a busca estoura o orçamento, recomeça com outra semente derivada. Com número ímpar de franquias alguém folga toda
 * semana; se as folgas não bastarem, o número de jogos por franquia cai até caber ({@link Schedule#gamesPerTeam()}).
 *
 * <p><b>Horários.</b> Por semana: um jogo de quinta à noite (sem franquia que jogou na segunda anterior), um de domingo à noite, um de
 * segunda à noite e o resto domingo cedo e domingo à tarde.
 */
public final class ScheduleGenerator {
  private static final int MAX_ATTEMPTS = 80;
  private static final long NODE_BUDGET = 300_000;
  /** Tentativas com as folgas sorteadas antes da busca; depois delas, a busca decide as folgas (formatos estranhos do editor). */
  private static final int FIXED_BYE_ATTEMPTS = 8;

  private ScheduleGenerator() {}

  /** Franquia no gerador: chave estável (o id), índices de conferência e divisão e a colocação dentro da divisão (0 = primeira). */
  public record Team(String key, int conference, int division, int place) {}

  /** Jogo gerado: semana (1..weeks), mandante, visitante, horário e se é de divisão ou de conferência. */
  public record Game(int week, String homeKey, String awayKey, KickoffSlot slot, boolean divisional, boolean conference) {}

  public record Schedule(int weeks, int gamesPerTeam, List<Game> games) {
    public List<Game> week(int number) { return games.stream().filter(g -> g.week() == number).toList(); }
  }

  public static Schedule regularSeason(List<Team> input, int weeks, int gamesPerTeam, long seed) {
    if (input == null || input.size() < 2) throw new IllegalArgumentException("a liga precisa de pelo menos 2 franquias");
    if (weeks < 1 || gamesPerTeam < 1 || gamesPerTeam > weeks) throw new IllegalArgumentException("semanas e jogos por franquia inválidos");
    Team[] t = input.stream().sorted(Comparator.comparingInt(Team::conference).thenComparingInt(Team::division).thenComparingInt(Team::place)
        .thenComparing(Team::key)).toArray(Team[]::new);
    Set<String> keys = new HashSet<>();
    for (Team x : t) if (!keys.add(x.key())) throw new IllegalArgumentException("franquia repetida: " + x.key());
    int n = t.length;
    int g = gamesPerTeam;
    while (g > 0 && (((long) n * g) % 2 != 0 || (n % 2 == 1 && (long) n * (weeks - g) < weeks))) g--;
    if (g == 0) throw new IllegalArgumentException("formato de temporada impossível para " + n + " franquias");
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      SplittableRandom rnd = new SplittableRandom(SeedPolicy.derive(seed, "schedule", attempt));
      List<int[]> games = matchups(t, g, rnd);
      if (games == null) continue;
      List<int[]> placed = attempt < FIXED_BYE_ATTEMPTS ? assignWeeks(n, weeks, games, rnd) : assignWeeksFreeByes(n, weeks, games, rnd);
      if (placed == null) continue;
      return new Schedule(weeks, g, build(t, weeks, placed, rnd));
    }
    throw new IllegalStateException("não foi possível montar o calendário (" + n + " franquias, " + g + " jogos, " + weeks + " semanas)");
  }

  // ===== confrontos =====

  private static boolean sameDivision(Team a, Team b) { return a.conference() == b.conference() && a.division() == b.division(); }

  /** Jogos orientados {mandante, visitante} com exatamente {@code g} jogos por franquia; {@code null} se esta tentativa não fecha. */
  static List<int[]> matchups(Team[] t, int g, SplittableRandom rnd) {
    int n = t.length;
    int[][] m = new int[n][n];
    int[][] fixed = new int[n][n];
    int[] need = new int[n];
    Arrays.fill(need, g);
    List<int[]> oriented = new ArrayList<>();
    List<int[]> loose = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        if (!sameDivision(t[i], t[j])) continue;
        int size = divisionSize(t, t[i]);
        int per = 3 * 2 * (size - 1) <= 2 * g ? 2 : size - 1 <= g ? 1 : 0;
        if (per == 2) { oriented.add(new int[] {i, j}); oriented.add(new int[] {j, i}); }
        else if (per == 1) loose.add(new int[] {i, j});
        m[i][j] += per; m[j][i] += per; fixed[i][j] += per; fixed[j][i] += per; need[i] -= per; need[j] -= per;
      }
    }
    if (nflShape(t, g)) nflFormula(t, m, need, loose, rnd);
    else if (!fill(t, m, fixed, need, loose, rnd)) return null;
    for (int x : need) if (x != 0) return null;
    oriented.addAll(orient(n, loose, rnd));
    return oriented;
  }

  private static int divisionSize(Team[] t, Team of) {
    int s = 0;
    for (Team x : t) if (sameDivision(x, of)) s++;
    return s;
  }

  /** Liga padrão: 2 conferências × 4 divisões × 4 franquias com colocações 0..3, 17 jogos. */
  private static boolean nflShape(Team[] t, int g) {
    if (t.length != 32 || g != 17) return false;
    boolean[][][] seen = new boolean[2][4][4];
    for (Team x : t) {
      if (x.conference() < 0 || x.conference() > 1 || x.division() < 0 || x.division() > 3 || x.place() < 0 || x.place() > 3) return false;
      if (seen[x.conference()][x.division()][x.place()]) return false;
      seen[x.conference()][x.division()][x.place()] = true;
    }
    return true;
  }

  private static void nflFormula(Team[] t, int[][] m, int[] need, List<int[]> loose, SplittableRandom rnd) {
    int[][][] idx = new int[2][4][4];
    for (int i = 0; i < t.length; i++) idx[t[i].conference()][t[i].division()][t[i].place()] = i;
    int[][] options = {{1, 0, 3, 2}, {2, 3, 0, 1}, {3, 2, 1, 0}};
    int[][] partner = {options[rnd.nextInt(3)], options[rnd.nextInt(3)]};
    int shift = rnd.nextInt(4);
    int extra = 1 + rnd.nextInt(3);
    for (int c = 0; c < 2; c++) {
      for (int d = 0; d < 4; d++) {
        int e = partner[c][d];
        if (d < e) for (int p = 0; p < 4; p++) for (int q = 0; q < 4; q++) add(idx[c][d][p], idx[c][e][q], m, need, loose);
        for (int o = d + 1; o < 4; o++) {
          if (o == e) continue;
          for (int p = 0; p < 4; p++) add(idx[c][d][p], idx[c][o][p], m, need, loose);
        }
      }
    }
    for (int d = 0; d < 4; d++) {
      int other = (d + shift) % 4;
      for (int p = 0; p < 4; p++) for (int q = 0; q < 4; q++) add(idx[0][d][p], idx[1][other][q], m, need, loose);
      int seventeenth = (d + shift + extra) % 4;
      for (int p = 0; p < 4; p++) add(idx[0][d][p], idx[1][seventeenth][p], m, need, loose);
    }
  }

  private static void add(int a, int b, int[][] m, int[] need, List<int[]> loose) {
    loose.add(new int[] {Math.min(a, b), Math.max(a, b)});
    m[a][b]++; m[b][a]++; need[a]--; need[b]--;
  }

  private static int cap(Team[] t, int[][] fixed, int level, int i, int j) {
    return sameDivision(t[i], t[j]) ? fixed[i][j] + Math.max(0, level - 2) : level;
  }

  /** Ligas fora do padrão: completa os jogos por níveis (1: um jogo por par, conferência primeiro; 2: repete; 3+: repete também a divisão). */
  private static boolean fill(Team[] t, int[][] m, int[][] fixed, int[] need, List<int[]> loose, SplittableRandom rnd) {
    int n = t.length;
    for (int level = 1; level <= 6; level++) {
      List<int[]> cands = new ArrayList<>();
      for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) if (level >= 3 || !sameDivision(t[i], t[j])) cands.add(new int[] {i, j});
      shuffle(cands, rnd);
      if (level == 1 && conferencesEven(t)) cands.sort(Comparator.comparingInt(e -> t[e[0]].conference() == t[e[1]].conference() ? 0 : 1));
      for (int[] e : cands) {
        int i = e[0], j = e[1];
        if (need[i] > 0 && need[j] > 0 && m[i][j] < cap(t, fixed, level, i, j)) add(i, j, m, need, loose);
      }
      repair(t, m, fixed, need, loose, level, rnd);
      if (Arrays.stream(need).allMatch(x -> x == 0)) return true;
    }
    return false;
  }

  /**
   * Conferência primeiro só quando toda conferência tem número par de franquias: uma conferência ímpar fechada em si mesma obrigaria
   * uma folga dela em toda semana, o que 1 bye por franquia não cobre.
   */
  private static boolean conferencesEven(Team[] t) {
    java.util.Map<Integer, Integer> size = new java.util.HashMap<>();
    for (Team x : t) size.merge(x.conference(), 1, Integer::sum);
    return size.values().stream().allMatch(v -> v % 2 == 0);
  }

  private static void repair(Team[] t, int[][] m, int[][] fixed, int[] need, List<int[]> loose, int level, SplittableRandom rnd) {
    int n = t.length;
    for (int iter = 0; iter < 5_000; iter++) {
      List<Integer> open = new ArrayList<>();
      for (int i = 0; i < n; i++) if (need[i] > 0) open.add(i);
      if (open.isEmpty()) return;
      shuffle(open, rnd);
      int u = open.get(0);
      boolean done = false;
      for (int v : open) {
        if (v != u && m[u][v] < cap(t, fixed, level, u, v)) { add(u, v, m, need, loose); done = true; break; }
      }
      if (done) continue;
      List<Integer> order = new ArrayList<>();
      for (int k = 0; k < loose.size(); k++) order.add(k);
      shuffle(order, rnd);
      if (need[u] >= 2) {
        for (int k : order) {
          int a = loose.get(k)[0], b = loose.get(k)[1];
          if (a == u || b == u || m[a][b] <= fixed[a][b]) continue;
          if (m[u][a] < cap(t, fixed, level, u, a) && m[u][b] < cap(t, fixed, level, u, b)) {
            remove(k, m, need, loose); add(u, a, m, need, loose); add(u, b, m, need, loose); done = true; break;
          }
        }
      }
      if (!done) {
        outer:
        for (int v : open) {
          if (v == u) continue;
          for (int k : order) {
            int a = loose.get(k)[0], b = loose.get(k)[1];
            if (a == u || b == u || a == v || b == v || m[a][b] <= fixed[a][b]) continue;
            if (m[u][a] < cap(t, fixed, level, u, a) && m[v][b] < cap(t, fixed, level, v, b)) {
              remove(k, m, need, loose); add(u, a, m, need, loose); add(v, b, m, need, loose); done = true; break outer;
            }
            if (m[u][b] < cap(t, fixed, level, u, b) && m[v][a] < cap(t, fixed, level, v, a)) {
              remove(k, m, need, loose); add(u, b, m, need, loose); add(v, a, m, need, loose); done = true; break outer;
            }
          }
        }
      }
      if (!done) return;
    }
  }

  private static void remove(int k, int[][] m, int[] need, List<int[]> loose) {
    int[] e = loose.remove(k);
    m[e[0]][e[1]]--; m[e[1]][e[0]]--; need[e[0]]++; need[e[1]]++;
  }

  /**
   * Mando equilibrado: liga um vértice virtual a toda franquia de grau ímpar e percorre trilhas fechadas, orientando cada aresta no
   * sentido do percurso (mandante → visitante). Em trilha fechada cada vértice entra e sai o mesmo número de vezes, então cada franquia
   * fica com metade dos jogos em casa (±1, por causa da aresta virtual).
   */
  static List<int[]> orient(int n, List<int[]> edges, SplittableRandom rnd) {
    List<int[]> all = new ArrayList<>(edges);
    int[] deg = new int[n + 1];
    for (int[] e : edges) { deg[e[0]]++; deg[e[1]]++; }
    for (int i = 0; i < n; i++) if (deg[i] % 2 == 1) all.add(new int[] {i, n});
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i <= n; i++) adj.add(new ArrayList<>());
    for (int k = 0; k < all.size(); k++) { adj.get(all.get(k)[0]).add(k); adj.get(all.get(k)[1]).add(k); }
    for (List<Integer> l : adj) shuffle(l, rnd);
    boolean[] used = new boolean[all.size()];
    int[] ptr = new int[n + 1];
    List<Integer> starts = new ArrayList<>();
    for (int i = 0; i <= n; i++) starts.add(i);
    shuffle(starts, rnd);
    List<int[]> out = new ArrayList<>();
    for (int s : starts) {
      int cur = s;
      while (true) {
        List<Integer> a = adj.get(cur);
        while (ptr[cur] < a.size() && used[a.get(ptr[cur])]) ptr[cur]++;
        if (ptr[cur] >= a.size()) {
          if (cur == s) break;
          throw new IllegalStateException("trilha aberta na orientação do mando");
        }
        int k = a.get(ptr[cur]);
        used[k] = true;
        int[] e = all.get(k);
        int next = e[0] == cur ? e[1] : e[0];
        if (k < edges.size()) out.add(new int[] {cur, next});
        cur = next;
      }
    }
    return out;
  }

  // ===== semanas =====

  /** {semana (0-based), mandante, visitante} para cada jogo; {@code null} se a busca estourou o orçamento. */
  static List<int[]> assignWeeks(int n, int weeks, List<int[]> games, SplittableRandom rnd) {
    int[] played = new int[n];
    for (int[] g : games) { played[g[0]]++; played[g[1]]++; }
    boolean[][] bye = new boolean[weeks][n];
    if (!assignByes(n, weeks, played, components(n, games), bye, rnd)) return null;
    int[][] home = new int[n][n];
    int[][] rem = new int[n][n];
    for (int[] g : games) { home[g[0]][g[1]]++; rem[g[0]][g[1]]++; rem[g[1]][g[0]]++; }
    Search s = new Search(n, weeks, bye, home, rem, rnd);
    try {
      return s.solve(0) ? s.placed : null;
    } catch (BudgetExceeded e) {
      return null;
    }
  }

  /**
   * Plano B para formatos que as folgas sorteadas não resolvem (ex.: divisões ímpares quase sem jogos fora delas, onde a paridade de
   * cada divisão amarra as folgas): a própria busca decide quem folga. Franquia com jogos restantes iguais às semanas restantes joga;
   * as outras podem folgar — no miolo da temporada, até a cota da semana, a folga é tentada primeiro.
   */
  static List<int[]> assignWeeksFreeByes(int n, int weeks, List<int[]> games, SplittableRandom rnd) {
    int[][] home = new int[n][n];
    int[][] rem = new int[n][n];
    for (int[] g : games) { home[g[0]][g[1]]++; rem[g[0]][g[1]]++; rem[g[1]][g[0]]++; }
    FreeSearch s = new FreeSearch(n, weeks, home, rem, rnd);
    try {
      return s.solve(0) ? s.placed : null;
    } catch (BudgetExceeded e) {
      return null;
    }
  }

  private static final class FreeSearch {
    final int n, weeks, lo, hi;
    final int[][] home, rem, opponent;
    final int[] left;
    final SplittableRandom rnd;
    final List<int[]> placed = new ArrayList<>();
    long nodes;

    FreeSearch(int n, int weeks, int[][] home, int[][] rem, SplittableRandom rnd) {
      this.n = n; this.weeks = weeks; this.home = home; this.rem = rem; this.rnd = rnd;
      int l = weeks / 4, h = weeks - weeks / 4 - 1;
      if (l > h) { l = 0; h = weeks - 1; }
      lo = l; hi = h;
      left = new int[n];
      for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) left[i] += rem[i][j];
      opponent = new int[weeks][n];
      for (int[] row : opponent) Arrays.fill(row, -1);
    }

    boolean solve(int w) {
      if (w == weeks) return true;
      int weeksLeft = weeks - w;
      int slack = 0;
      for (int i = 0; i < n; i++) { if (left[i] > weeksLeft) return false; slack += weeksLeft - left[i]; }
      int windowLeft = Math.max(1, Math.min(hi, weeks - 1) - Math.max(lo, w) + 1);
      int quota = w >= lo && w <= hi ? (slack + windowLeft - 1) / windowLeft : 0;
      return week(w, new boolean[n], quota);
    }

    boolean week(int w, boolean[] decided, int quota) {
      if (++nodes > NODE_BUDGET) throw new BudgetExceeded();
      int weeksLeft = weeks - w;
      int best = -1, bestCount = Integer.MAX_VALUE;
      boolean bestMust = false;
      for (int i = 0; i < n; i++) {
        if (decided[i]) continue;
        boolean must = left[i] == weeksLeft;
        int c = 0;
        for (int j = 0; j < n; j++) if (j != i && !decided[j] && rem[i][j] > 0) c++;
        if (must && c == 0) return false;
        if ((must && !bestMust) || (must == bestMust && c < bestCount)) { best = i; bestCount = c; bestMust = must; }
      }
      if (best < 0) return solve(w + 1);
      final int b = best;
      List<Integer> cand = new ArrayList<>();
      for (int j = 0; j < n; j++) if (j != b && !decided[j] && rem[b][j] > 0) cand.add(j);
      shuffle(cand, rnd);
      final int last = w > 0 ? opponent[w - 1][b] : -1;
      cand.sort(Comparator.comparingInt((Integer j) -> j == last ? 1 : 0).thenComparingInt(j -> -rem[b][j]));
      boolean canRest = left[b] < weeksLeft;
      boolean restFirst = canRest && quota > 0;
      if (restFirst && rest(w, decided, b, quota - 1)) return true;
      for (int j : cand) {
        int h, a;
        if (home[b][j] > 0 && (home[j][b] == 0 || rnd.nextBoolean())) { h = b; a = j; } else { h = j; a = b; }
        home[h][a]--; rem[b][j]--; rem[j][b]--; left[b]--; left[j]--; decided[b] = true; decided[j] = true;
        opponent[w][b] = j; opponent[w][j] = b;
        placed.add(new int[] {w, h, a});
        if (week(w, decided, quota)) return true;
        placed.remove(placed.size() - 1);
        home[h][a]++; rem[b][j]++; rem[j][b]++; left[b]++; left[j]++; decided[b] = false; decided[j] = false;
        opponent[w][b] = -1; opponent[w][j] = -1;
      }
      return canRest && !restFirst && rest(w, decided, b, quota);
    }

    boolean rest(int w, boolean[] decided, int b, int quota) {
      decided[b] = true;
      if (week(w, decided, quota)) return true;
      decided[b] = false;
      return false;
    }
  }

  /**
   * Folgas de cada franquia ({@code weeks − jogos}). Franquias que nunca se enfrentam, nem indiretamente, formam componentes
   * independentes; em cada um, o número de franquias que jogam na semana precisa ser par. Componente ímpar: uma folga por semana em
   * toda semana; o resto das folgas vai em pares (duas franquias do mesmo componente) para as semanas do miolo menos carregadas.
   */
  private static boolean assignByes(int n, int weeks, int[] played, int[] component, boolean[][] bye, SplittableRandom rnd) {
    int[] count = new int[weeks];
    int lo = weeks / 4, hi = weeks - weeks / 4 - 1;
    if (lo > hi) { lo = 0; hi = weeks - 1; }
    List<Integer> window = new ArrayList<>();
    for (int w = lo; w <= hi; w++) window.add(w);
    List<Integer> everyWeek = new ArrayList<>();
    for (int w = 0; w < weeks; w++) everyWeek.add(w);
    int components = Arrays.stream(component).max().orElse(-1) + 1;
    for (int c = 0; c < components; c++) {
      List<Integer> pool = new ArrayList<>();
      int size = 0;
      for (int i = 0; i < n; i++) {
        if (component[i] != c) continue;
        size++;
        for (int k = 0; k < weeks - played[i]; k++) pool.add(i);
      }
      shuffle(pool, rnd);
      if (size % 2 == 1) {
        for (int w = 0; w < weeks; w++) {
          int pick = -1;
          for (int k = 0; k < pool.size(); k++) if (!bye[w][pool.get(k)]) { pick = k; break; }
          if (pick < 0) return false;
          bye[w][pool.remove(pick)] = true;
          count[w]++;
        }
      }
      while (!pool.isEmpty()) {
        int x = pool.remove(0);
        int k = -1;
        for (int i = 0; i < pool.size(); i++) if (pool.get(i) != x) { k = i; break; }
        if (k < 0) return false;
        int y = pool.remove(k);
        int week = leastLoaded(window, count, bye, x, y, rnd);
        if (week < 0) week = leastLoaded(everyWeek, count, bye, x, y, rnd);
        if (week < 0) return false;
        bye[week][x] = true; bye[week][y] = true; count[week] += 2;
      }
    }
    return true;
  }

  /** Componente conexo de cada franquia no grafo dos confrontos. */
  private static int[] components(int n, List<int[]> games) {
    int[] parent = new int[n];
    for (int i = 0; i < n; i++) parent[i] = i;
    for (int[] g : games) parent[find(parent, g[0])] = find(parent, g[1]);
    int[] id = new int[n];
    Arrays.fill(id, -1);
    int next = 0;
    int[] out = new int[n];
    for (int i = 0; i < n; i++) {
      int r = find(parent, i);
      if (id[r] < 0) id[r] = next++;
      out[i] = id[r];
    }
    return out;
  }

  private static int find(int[] parent, int i) {
    while (parent[i] != i) { parent[i] = parent[parent[i]]; i = parent[i]; }
    return i;
  }

  private static int leastLoaded(List<Integer> weeks, int[] count, boolean[][] bye, int x, int y, SplittableRandom rnd) {
    List<Integer> order = new ArrayList<>(weeks);
    shuffle(order, rnd);
    int best = -1;
    for (int w : order) if (!bye[w][x] && !bye[w][y] && (best < 0 || count[w] < count[best])) best = w;
    return best;
  }

  private static final class BudgetExceeded extends RuntimeException {
    BudgetExceeded() { super(null, null, false, false); }
  }

  /** Busca com retrocesso semana a semana: cada semana é um emparelhamento perfeito das franquias que não folgam. */
  private static final class Search {
    final int n, weeks;
    final boolean[][] bye;
    final int[][] home, rem, opponent;
    final SplittableRandom rnd;
    final List<int[]> placed = new ArrayList<>();
    long nodes;

    Search(int n, int weeks, boolean[][] bye, int[][] home, int[][] rem, SplittableRandom rnd) {
      this.n = n; this.weeks = weeks; this.bye = bye; this.home = home; this.rem = rem; this.rnd = rnd;
      this.opponent = new int[weeks][n];
      for (int[] row : opponent) Arrays.fill(row, -1);
    }

    boolean solve(int w) {
      if (w == weeks) return true;
      boolean[] free = new boolean[n];
      int left = 0;
      for (int i = 0; i < n; i++) if (!bye[w][i]) { free[i] = true; left++; }
      return match(w, free, left);
    }

    boolean match(int w, boolean[] free, int left) {
      if (++nodes > NODE_BUDGET) throw new BudgetExceeded();
      if (left == 0) return solve(w + 1);
      int best = -1, bestCount = Integer.MAX_VALUE;
      for (int i = 0; i < n; i++) {
        if (!free[i]) continue;
        int c = 0;
        for (int j = 0; j < n; j++) if (j != i && free[j] && rem[i][j] > 0) c++;
        if (c == 0) return false;
        if (c < bestCount) { bestCount = c; best = i; }
      }
      final int b = best;
      List<Integer> cand = new ArrayList<>();
      for (int j = 0; j < n; j++) if (j != b && free[j] && rem[b][j] > 0) cand.add(j);
      shuffle(cand, rnd);
      final int last = w > 0 ? opponent[w - 1][b] : -1;
      cand.sort(Comparator.comparingInt((Integer j) -> j == last ? 1 : 0).thenComparingInt(j -> -rem[b][j]));
      for (int j : cand) {
        int h, a;
        if (home[b][j] > 0 && (home[j][b] == 0 || rnd.nextBoolean())) { h = b; a = j; } else { h = j; a = b; }
        home[h][a]--; rem[b][j]--; rem[j][b]--; free[b] = false; free[j] = false; opponent[w][b] = j; opponent[w][j] = b;
        placed.add(new int[] {w, h, a});
        if (match(w, free, left - 2)) return true;
        placed.remove(placed.size() - 1);
        home[h][a]++; rem[b][j]++; rem[j][b]++; free[b] = true; free[j] = true; opponent[w][b] = -1; opponent[w][j] = -1;
      }
      return false;
    }
  }

  // ===== horários =====

  private static List<Game> build(Team[] t, int weeks, List<int[]> placed, SplittableRandom rnd) {
    List<Game> out = new ArrayList<>();
    java.util.Map<String, Integer> homeIndex = new java.util.HashMap<>();
    Set<Integer> mondayLastWeek = new HashSet<>();
    for (int w = 0; w < weeks; w++) {
      final int week = w;
      List<int[]> games = new ArrayList<>(placed.stream().filter(p -> p[0] == week).toList());
      games.sort(Comparator.comparingInt((int[] p) -> p[1]));
      shuffle(games, rnd);
      KickoffSlot[] slots = new KickoffSlot[games.size()];
      int k = games.size();
      Set<Integer> monday = new HashSet<>();
      if (k >= 3) {
        int thu = 0;
        for (int i = 0; i < k; i++) {
          if (!mondayLastWeek.contains(games.get(i)[1]) && !mondayLastWeek.contains(games.get(i)[2])) { thu = i; break; }
        }
        java.util.Collections.swap(games, 0, thu);
        slots[0] = KickoffSlot.THU_NIGHT;
        slots[k - 1] = KickoffSlot.MON_NIGHT;
        int from = 1, to = k - 1;
        if (k >= 4) { slots[k - 2] = KickoffSlot.SUN_NIGHT; to = k - 2; }
        int rest = to - from;
        for (int i = 0; i < rest; i++) slots[from + i] = i < (rest + 1) / 2 ? KickoffSlot.SUN_EARLY : KickoffSlot.SUN_LATE;
        monday.add(games.get(k - 1)[1]);
        monday.add(games.get(k - 1)[2]);
      } else {
        for (int i = 0; i < k; i++) slots[i] = i == 0 ? KickoffSlot.SUN_EARLY : KickoffSlot.SUN_LATE;
      }
      for (int i = 0; i < k; i++) {
        Team h = t[games.get(i)[1]], a = t[games.get(i)[2]];
        out.add(new Game(w + 1, h.key(), a.key(), slots[i], sameDivision(h, a), h.conference() == a.conference()));
        homeIndex.put(h.key(), games.get(i)[1]);
      }
      mondayLastWeek = monday;
    }
    // ordem pelo índice (conferência, divisão, colocação), nunca pela chave: o mesmo mundo clonado (ids novos) dá o mesmo calendário
    out.sort(Comparator.comparingInt(Game::week).thenComparingInt(g -> g.slot().ordinal()).thenComparingInt(g -> homeIndex.get(g.homeKey())));
    return List.copyOf(out);
  }

  private static <T> void shuffle(List<T> list, SplittableRandom rnd) {
    for (int i = list.size() - 1; i > 0; i--) java.util.Collections.swap(list, i, rnd.nextInt(i + 1));
  }
}
