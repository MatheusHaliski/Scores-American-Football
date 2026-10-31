package br.com.velion.scoresaf.application.usecase.rf21;

import br.com.velion.scoresaf.application.service.StandingsService.Clinch;
import br.com.velion.scoresaf.domain.season.Standings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Notícias da semana geradas por regras a partir dos jogos e dos relatórios gravados (RF21, D-103), puras: mesmos dados ⇒ mesmas
 * notícias, na mesma ordem. Sem IA externa e sem lesões (o motor só lesiona no RF64).
 *
 * <ul>
 *   <li>O jogo do usuário (vitória, derrota ou empate, com a campanha depois da semana).</li>
 *   <li>Temporada regular: o atropelo da semana (diferença de 21 ou mais) e o jogo mais emocionante (prorrogação, senão diferença de
 *   até 3), fora o do usuário.</li>
 *   <li>Destaques individuais: o melhor passador (300+ jardas ou 4+ TD), corredor (130+), recebedor (130+) e defensor (3+ sacks ou 2+
 *   interceptações) da semana.</li>
 *   <li>Sequências: a maior série de vitórias e a de derrotas em andamento (4 ou mais) e a do usuário (3 ou mais).</li>
 *   <li>Classificação: quem garantiu folga, título de divisão ou vaga, e quem foi eliminado nesta semana; na última semana da
 *   temporada regular, a semente 1 de cada conferência e a semente (ou a ausência) do usuário.</li>
 *   <li>Playoffs: cada jogo (quem avança e quem cai) e a campeã na final.</li>
 * </ul>
 * Os parâmetros de cada notícia ficam na ordem em que o texto ({@code news.<KIND>.headline} / {@code .body}) os usa.
 */
public final class WeekNews {
  private WeekNews() {}

  public static final int BLOWOUT_MARGIN = 21, THRILLER_MARGIN = 3, STREAK = 4, USER_STREAK = 3;

  /** Jogo encerrado da semana. */
  public record Game(UUID fixtureId, UUID home, String homeName, UUID away, String awayName, int homeScore, int awayScore, boolean overtime) {
    UUID winner() { return homeScore >= awayScore ? home : away; }
    UUID loser() { return homeScore >= awayScore ? away : home; }
    String winnerName() { return homeScore >= awayScore ? homeName : awayName; }
    String loserName() { return homeScore >= awayScore ? awayName : homeName; }
    int margin() { return Math.abs(homeScore - awayScore); }
    boolean involves(UUID c) { return home.equals(c) || away.equals(c); }
  }

  /** Linha de box score de um jogador na semana. */
  public record Line(UUID playerId, String name, UUID clubId, UUID fixtureId, int passYards, int passTouchdowns, int rushYards, int rushTouchdowns,
                     int receivingYards, int receivingTouchdowns, int sacks, int interceptions) {}

  /** Uma notícia, antes do texto. */
  public record Item(String kind, String tone, Map<String, Object> params, UUID clubId, UUID playerId, UUID fixtureId) {}

  /**
   * Entrada: semana, se é de playoff e qual rodada (texto já traduzido), se é a final ou a última semana da temporada regular, o ano,
   * a franquia do usuário, os jogos, as linhas dos jogadores, nomes das franquias, a tabela depois da semana, a situação garantida
   * antes e depois e as sementes (só na última semana regular: conferência → sementes) com o nome da conferência.
   */
  public record Input(int week, boolean playoff, String roundLabel, boolean finalRound, boolean lastRegularWeek, int year, UUID userClub,
                      List<Game> games, List<Line> lines, Map<UUID, String> names, Standings.Table after, Map<UUID, Clinch> clinchBefore,
                      Map<UUID, Clinch> clinchAfter, Map<UUID, String> divisionNames, Map<String, String> conferenceNames) {}

  public static List<Item> build(Input in) {
    List<Item> out = new ArrayList<>();
    List<Game> games = in.games();
    Game mine = in.userClub() == null ? null : games.stream().filter(g -> g.involves(in.userClub())).findFirst().orElse(null);
    if (in.playoff()) {
      List<Game> ordered = new ArrayList<>(games);
      ordered.sort(Comparator.comparing((Game g) -> g == mine ? 0 : 1));
      for (Game g : ordered) {
        boolean user = g == mine;
        String tone = user ? (g.winner().equals(in.userClub()) ? "POSITIVE" : "NEGATIVE") : in.finalRound() ? "DRAMATIC" : "NEUTRAL";
        if (in.finalRound()) {
          out.add(item("CHAMPION", tone, g.winner(), null, g.fixtureId(), "team", g.winnerName(), "opponent", g.loserName(),
              "winnerScore", Math.max(g.homeScore(), g.awayScore()), "loserScore", Math.min(g.homeScore(), g.awayScore()), "year", in.year()));
        } else {
          out.add(item("PLAYOFF_RESULT", tone, g.winner(), null, g.fixtureId(), "winner", g.winnerName(), "loser", g.loserName(),
              "winnerScore", Math.max(g.homeScore(), g.awayScore()), "loserScore", Math.min(g.homeScore(), g.awayScore()), "round", in.roundLabel(),
              "overtime", g.overtime() ? 1 : 0));
        }
      }
      highlights(in, out);
      return List.copyOf(out);
    }
    if (mine != null) {
      boolean home = mine.home().equals(in.userClub());
      int pf = home ? mine.homeScore() : mine.awayScore(), pa = home ? mine.awayScore() : mine.homeScore();
      String kind = pf > pa ? "USER_WIN" : pf < pa ? "USER_LOSS" : "USER_TIE";
      String tone = pf > pa ? "POSITIVE" : pf < pa ? "NEGATIVE" : "NEUTRAL";
      Standings.Record r = in.after().line(in.userClub().toString()).overall();
      out.add(item(kind, tone, in.userClub(), null, mine.fixtureId(), "team", home ? mine.homeName() : mine.awayName(),
          "opponent", home ? mine.awayName() : mine.homeName(), "pointsFor", pf, "pointsAgainst", pa, "record", summary(r), "overtime", mine.overtime() ? 1 : 0));
    }
    List<Game> others = games.stream().filter(g -> g != mine).toList();
    Game blowout = others.stream().filter(g -> g.margin() >= BLOWOUT_MARGIN)
        .max(Comparator.comparingInt(Game::margin).thenComparing(Game::winnerName, Comparator.reverseOrder())).orElse(null);
    if (blowout != null) {
      out.add(item("BLOWOUT", "DRAMATIC", blowout.winner(), null, blowout.fixtureId(), "winner", blowout.winnerName(), "loser", blowout.loserName(),
          "winnerScore", Math.max(blowout.homeScore(), blowout.awayScore()), "loserScore", Math.min(blowout.homeScore(), blowout.awayScore()),
          "margin", blowout.margin()));
    }
    others.stream().filter(g -> g != blowout && (g.overtime() || g.margin() <= THRILLER_MARGIN))
        .min(Comparator.comparingInt((Game g) -> g.overtime() ? 0 : 1).thenComparingInt(Game::margin)
            .thenComparingInt(g -> -(g.homeScore() + g.awayScore())).thenComparing(Game::winnerName))
        .ifPresent(g -> out.add(item("THRILLER", "DRAMATIC", g.winner(), null, g.fixtureId(), "winner", g.winnerName(), "loser", g.loserName(),
            "winnerScore", Math.max(g.homeScore(), g.awayScore()), "loserScore", Math.min(g.homeScore(), g.awayScore()), "overtime", g.overtime() ? 1 : 0,
            "margin", g.margin())));
    highlights(in, out);
    streaks(in, out);
    if (in.lastRegularWeek()) seeds(in, out);
    else clinches(in, out);
    return List.copyOf(out);
  }

  private static void highlights(Input in, List<Item> out) {
    Map<UUID, Game> byFixture = new LinkedHashMap<>();
    in.games().forEach(g -> byFixture.put(g.fixtureId(), g));
    // empate: o nome (estável no mundo clonado), depois o id
    Comparator<Line> stable = Comparator.comparing(Line::name, Comparator.reverseOrder()).thenComparing(l -> l.playerId().toString());
    in.lines().stream().filter(l -> l.passYards() >= 300 || l.passTouchdowns() >= 4)
        .max(Comparator.comparingInt(Line::passYards).thenComparingInt(Line::passTouchdowns).thenComparing(stable))
        .ifPresent(l -> out.add(player("PASSING", in, l, byFixture, l.passYards(), l.passTouchdowns())));
    in.lines().stream().filter(l -> l.rushYards() >= 130).max(Comparator.comparingInt(Line::rushYards).thenComparing(stable))
        .ifPresent(l -> out.add(player("RUSHING", in, l, byFixture, l.rushYards(), l.rushTouchdowns())));
    in.lines().stream().filter(l -> l.receivingYards() >= 130).max(Comparator.comparingInt(Line::receivingYards).thenComparing(stable))
        .ifPresent(l -> out.add(player("RECEIVING", in, l, byFixture, l.receivingYards(), l.receivingTouchdowns())));
    in.lines().stream().filter(l -> l.sacks() >= 3 || l.interceptions() >= 2)
        .max(Comparator.comparingInt((Line l) -> l.sacks() * 2 + l.interceptions() * 3).thenComparing(stable))
        .ifPresent(l -> out.add(player("DEFENSE", in, l, byFixture, l.sacks(), l.interceptions())));
  }

  private static Item player(String kind, Input in, Line l, Map<UUID, Game> byFixture, int a, int b) {
    Game g = byFixture.get(l.fixtureId());
    UUID opp = g == null ? null : g.home().equals(l.clubId()) ? g.away() : g.home();
    String tone = Objects.equals(l.clubId(), in.userClub()) ? "POSITIVE" : "NEUTRAL";
    String second = kind.equals("DEFENSE") ? "interceptions" : "touchdowns";
    String first = kind.equals("DEFENSE") ? "sacks" : "yards";
    return item(kind, tone, l.clubId(), l.playerId(), l.fixtureId(), "player", l.name(), "team", in.names().getOrDefault(l.clubId(), ""),
        "opponent", opp == null ? "" : in.names().getOrDefault(opp, ""), first, a, second, b);
  }

  private static void streaks(Input in, List<Item> out) {
    record S(UUID club, String mark, int n) {}
    List<S> all = new ArrayList<>();
    for (Standings.Line l : in.after().lines().values()) {
      if (l.streak() == null || l.streak().isEmpty()) continue;
      all.add(new S(UUID.fromString(l.team().key()), l.streak().substring(0, 1), Integer.parseInt(l.streak().substring(1))));
    }
    Comparator<S> order = Comparator.comparingInt((S s) -> -s.n()).thenComparing(s -> in.names().getOrDefault(s.club(), "")).thenComparing(s -> s.club().toString());
    S win = all.stream().filter(s -> s.mark().equals("W") && s.n() >= STREAK).min(order).orElse(null);
    S loss = all.stream().filter(s -> s.mark().equals("L") && s.n() >= STREAK).min(order).orElse(null);
    if (win != null) out.add(item("WIN_STREAK", Objects.equals(win.club(), in.userClub()) ? "POSITIVE" : "NEUTRAL", win.club(), null, null,
        "team", in.names().get(win.club()), "games", win.n()));
    if (loss != null) out.add(item("LOSS_STREAK", Objects.equals(loss.club(), in.userClub()) ? "NEGATIVE" : "NEUTRAL", loss.club(), null, null,
        "team", in.names().get(loss.club()), "games", loss.n()));
    if (in.userClub() == null) return;
    all.stream().filter(s -> s.club().equals(in.userClub()) && s.n() >= USER_STREAK && !s.mark().equals("T")).findFirst()
        .filter(s -> !(win != null && win.club().equals(s.club())) && !(loss != null && loss.club().equals(s.club())))
        .ifPresent(s -> out.add(item(s.mark().equals("W") ? "WIN_STREAK" : "LOSS_STREAK", s.mark().equals("W") ? "POSITIVE" : "NEGATIVE", s.club(), null, null,
            "team", in.names().get(s.club()), "games", s.n())));
  }

  private static void clinches(Input in, List<Item> out) {
    List<UUID> clubs = new ArrayList<>(in.clinchAfter().keySet());
    clubs.sort(Comparator.comparing((UUID c) -> c.equals(in.userClub()) ? 0 : 1).thenComparing(c -> in.names().getOrDefault(c, "")).thenComparing(UUID::toString));
    for (UUID c : clubs) {
      Clinch now = in.clinchAfter().get(c), before = in.clinchBefore().get(c);
      if (now == before || (before != null && before != Clinch.ELIMINATED && now != Clinch.ELIMINATED && before.ordinal() <= now.ordinal())) continue;
      String team = in.names().get(c);
      switch (now) {
        case BYE -> out.add(item("CLINCH_BYE", "POSITIVE", c, null, null, "team", team));
        case DIVISION -> out.add(item("CLINCH_DIVISION", "POSITIVE", c, null, null, "team", team, "division", in.divisionNames().getOrDefault(c, "")));
        case PLAYOFFS -> out.add(item("CLINCH_PLAYOFFS", "POSITIVE", c, null, null, "team", team));
        case ELIMINATED -> out.add(item("ELIMINATED", c.equals(in.userClub()) ? "NEGATIVE" : "NEUTRAL", c, null, null, "team", team));
      }
    }
  }

  private static void seeds(Input in, List<Item> out) {
    if (in.userClub() != null) {
      Integer seed = in.after().seedOf(in.userClub().toString());
      String team = in.names().get(in.userClub());
      if (seed == null) out.add(item("USER_MISSED", "NEGATIVE", in.userClub(), null, null, "team", team));
      else out.add(item("USER_SEED", "POSITIVE", in.userClub(), null, null, "team", team, "seed", seed));
    }
    List<String> confs = new ArrayList<>(in.after().seeds().keySet());
    confs.sort(Comparator.comparing(k -> in.conferenceNames().getOrDefault(k, k)));
    for (String conf : confs) {
      List<Standings.Seed> s = in.after().seeds().get(conf);
      if (s == null || s.isEmpty()) continue;
      UUID top = UUID.fromString(s.get(0).key());
      out.add(item("TOP_SEED", top.equals(in.userClub()) ? "POSITIVE" : "NEUTRAL", top, null, null, "team", in.names().get(top),
          "conference", in.conferenceNames().getOrDefault(conf, "")));
    }
  }

  private static String summary(Standings.Record r) { return r.ties() > 0 ? r.wins() + "-" + r.losses() + "-" + r.ties() : r.wins() + "-" + r.losses(); }

  private static Item item(String kind, String tone, UUID club, UUID player, UUID fixture, Object... kv) {
    Map<String, Object> p = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) p.put((String) kv[i], kv[i + 1]);
    return new Item(kind, tone, p, club, player, fixture);
  }
}
