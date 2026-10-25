package br.com.velion.scoresaf.application.usecase.rf42;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.TeamBoxScore;
import br.com.velion.scoresaf.domain.engine.sim.DefaultMatchSimulator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Plausibilidade do motor (docs/01 §3.5, RF42) com as franquias do mundo semente: 310 jogos entre vizinhas no ranking de força
 * (semente fixa: o teste é estável), médias por equipe e por jogo dentro das faixas aceitas. Marcado {@code slow}, mas roda no
 * {@code mvn verify} padrão (só o grupo {@code it} fica de fora).
 */
@Tag("slow")
class MatchPlausibilityTest {
  private final DefaultMatchSimulator sim = new DefaultMatchSimulator();

  static final class Totals {
    int games;
    double points, yards, drives, cmp, att, sacks, ints, fumLost, thirdConv, thirdAtt, fgMade, fgAtt, plays;
    int homeWins, overtimes, ties;

    void add(MatchResult r) {
      games++;
      for (TeamBoxScore t : List.of(r.homeTeam(), r.awayTeam())) {
        points += t.points(); yards += t.totalYards(); drives += t.drives(); cmp += t.passCompletions(); att += t.passAttempts();
        sacks += t.sacksAllowed(); ints += t.interceptionsThrown(); fumLost += t.fumblesLost(); thirdConv += t.thirdDownConversions();
        thirdAtt += t.thirdDownAttempts(); fgMade += t.fieldGoalsMade(); fgAtt += t.fieldGoalAttempts();
      }
      plays += r.playCount();
      if (r.homeScore() > r.awayScore()) homeWins++;
      if (r.overtime()) overtimes++;
      if (r.winnerClubId() == null) ties++;
    }

    double team(double v) { return v / (2.0 * games); }
  }

  @Test
  void medias_por_equipe_e_por_jogo_ficam_nas_faixas_da_liga_profissional() {
    List<Club> ranked = SeedMatches.byStrength();
    Totals t = new Totals();
    long t0 = System.nanoTime();
    long seed = 1;
    for (int i = 0; i + 1 < ranked.size(); i++) {
      for (int k = 0; k < 10; k++, seed++) {
        Club a = ranked.get(i), b = ranked.get(i + 1);
        MatchSetup s = k % 2 == 0 ? SeedMatches.setup(a, b, 900_000 + seed) : SeedMatches.setup(b, a, 900_000 + seed);
        t.add(sim.simulate(s));
      }
    }
    long ms = (System.nanoTime() - t0) / 1_000_000;
    System.out.printf(Locale.ROOT, "PLAUSIBILIDADE %d jogos em %d ms | pts %.2f | jardas %.1f | drives %.2f | passe %.1f%% | sacks %.2f | INT %.2f | "
            + "fumble perdido %.2f | 3ª descida %.1f%% | FG %.1f%% | jogadas/partida %.1f | mandante vence %.1f%% | prorrogação %d | empates %d%n",
        t.games, ms, t.team(t.points), t.team(t.yards), t.team(t.drives), 100 * t.cmp / t.att, t.team(t.sacks), t.team(t.ints), t.team(t.fumLost),
        100 * t.thirdConv / t.thirdAtt, 100 * t.fgMade / t.fgAtt, t.plays / t.games, 100.0 * t.homeWins / t.games, t.overtimes, t.ties);

    assertThat(t.games).isGreaterThanOrEqualTo(300);
    assertThat(t.team(t.points)).as("pontos (alvo 21–24)").isBetween(18.0, 27.0);
    assertThat(t.team(t.yards)).as("jardas (alvo 320–360)").isBetween(290.0, 390.0);
    assertThat(t.team(t.drives)).as("drives (alvo 11–12)").isBetween(9.5, 13.5);
    assertThat(t.cmp / t.att).as("passes completos (alvo 63–66 %)").isBetween(0.59, 0.70);
    assertThat(t.team(t.sacks)).as("sacks (alvo 2–3)").isBetween(1.8, 3.2);
    assertThat(t.team(t.ints)).as("interceptações (alvo 0,7–1,0)").isBetween(0.6, 1.1);
    assertThat(t.team(t.fumLost)).as("fumbles perdidos (alvo 0,4–0,6)").isBetween(0.3, 0.7);
    assertThat(t.thirdConv / t.thirdAtt).as("3ª descida (alvo 38–42 %)").isBetween(0.33, 0.47);
    assertThat(t.fgMade / t.fgAtt).as("FG (alvo ~84 %)").isBetween(0.76, 0.90);
    assertThat(t.plays / t.games).as("jogadas por partida (alvo ~150)").isBetween(120.0, 180.0);
    assertThat(ms).as("310 jogos em menos de 30 s").isLessThan(30_000);
  }

  @Test
  void equipe_muito_mais_forte_vence_a_grande_maioria() {
    List<Club> ranked = SeedMatches.byStrength();
    Club strong = ranked.get(0), weak = ranked.get(ranked.size() - 1);
    int strongWins = 0, games = 200;
    for (int i = 0; i < games; i++) {
      MatchSetup s = i % 2 == 0 ? SeedMatches.setup(strong, weak, 700_000 + i) : SeedMatches.setup(weak, strong, 700_000 + i);
      // a mais fraca do mundo semente, ainda enfraquecida em 10 pontos em todos os atributos: "muito mais forte"
      s = i % 2 == 0 ? withAway(s, SeedMatches.shifted(s.away(), -10)) : withHome(s, SeedMatches.shifted(s.home(), -10));
      MatchResult r = sim.simulate(s);
      if (strong.getId().equals(r.winnerClubId())) strongWins++;
    }
    int plain = 0;
    for (int i = 0; i < games; i++) {
      MatchResult r = sim.simulate(i % 2 == 0 ? SeedMatches.setup(strong, weak, 710_000 + i) : SeedMatches.setup(weak, strong, 710_000 + i));
      if (strong.getId().equals(r.winnerClubId())) plain++;
    }
    System.out.printf(Locale.ROOT, "FORCA %s × %s: %d/%d com a fraca −10; %d/%d sem ajuste (%.1f × %.1f de overall nos 22 melhores)%n", strong.getShortName(),
        weak.getShortName(), strongWins, games, plain, games, SeedMatches.strength(strong), SeedMatches.strength(weak));
    assertThat(strongWins / (double) games).isGreaterThanOrEqualTo(0.80);
    assertThat(plain / (double) games).as("a mais forte do mundo semente vence a mais fraca na maioria").isGreaterThan(0.6);
  }

  private static MatchSetup withHome(MatchSetup s, br.com.velion.scoresaf.domain.engine.model.TeamSnapshot t) {
    return new MatchSetup(s.fixtureId(), t, s.away(), s.rules(), s.weather(), s.seed(), s.playCallingLevel(), s.userClubId(), s.neutralSite(), s.knockout());
  }

  private static MatchSetup withAway(MatchSetup s, br.com.velion.scoresaf.domain.engine.model.TeamSnapshot t) {
    return new MatchSetup(s.fixtureId(), s.home(), t, s.rules(), s.weather(), s.seed(), s.playCallingLevel(), s.userClubId(), s.neutralSite(), s.knockout());
  }
}
