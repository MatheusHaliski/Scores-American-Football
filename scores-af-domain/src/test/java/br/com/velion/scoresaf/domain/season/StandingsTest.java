package br.com.velion.scoresaf.domain.season;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.season.Standings.Game;
import br.com.velion.scoresaf.domain.season.Standings.Ranked;
import br.com.velion.scoresaf.domain.season.Standings.Seed;
import br.com.velion.scoresaf.domain.season.Standings.Table;
import br.com.velion.scoresaf.domain.season.Standings.Team;
import br.com.velion.scoresaf.domain.season.Standings.Tiebreak;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Classificação do RF16 (D-95): cada critério da cadeia de desempate da liga profissional decide sozinho um empate montado para ele
 * (confronto direto, divisão, jogos em comum, conferência, força das vitórias, força da tabela, saldo, projeção sem jogos e sorteio),
 * o grupo de três recomeça do primeiro critério, os wild cards levam só a mais bem colocada de cada divisão, as 7 sementes por
 * conferência têm os 4 campeões de divisão nas primeiras, e as linhas trazem casa/fora, divisão, conferência e sequência.
 */
class StandingsTest {

  /** Conferências A e B, divisões A1, A2, B1, B2; as franquias "f*" completam a liga só como adversárias. */
  private static final class League {
    final List<Team> teams = new ArrayList<>();
    final List<Game> games = new ArrayList<>();
    final Map<String, Team> byKey = new HashMap<>();
    int week = 1;

    League team(String key, String division) { return team(key, division, 50); }

    League team(String key, String division, int prestige) {
      Team t = new Team(key, division.substring(0, 1), division, prestige, key.toUpperCase());
      teams.add(t);
      byKey.put(key, t);
      return this;
    }

    /** Jogo encerrado (adversária desconhecida entra na divisão B2). */
    League game(String home, int hs, String away, int as) {
      for (String k : List.of(home, away)) if (!byKey.containsKey(k)) team(k, "B2");
      games.add(new Game(week++, home, away, hs, as));
      return this;
    }

    League beat(String winner, String loser) { return game(winner, 20, loser, 10); }

    Table table(int seeds) { return Standings.compute(teams, games, seeds, 7L); }
  }

  private static List<String> keys(List<Ranked> r) { return r.stream().map(x -> x.line().team().key()).toList(); }

  private static Ranked first(Table t, String division) { return t.divisions().get(division).get(0); }

  @Test
  void confronto_direto_decide_o_empate_na_divisao() {
    League l = new League().team("a", "A1").team("b", "A1").team("c", "A2").team("d", "A2");
    l.beat("a", "b").beat("b", "c").beat("d", "a");
    Table t = l.table(7);
    assertThat(keys(t.divisions().get("A1"))).containsExactly("a", "b");
    assertThat(first(t, "A1").tiebreak()).isEqualTo(Tiebreak.HEAD_TO_HEAD);
  }

  @Test
  void campanha_na_divisao_decide_quando_o_confronto_direto_empata() {
    League l = new League().team("a", "A1").team("b", "A1").team("x", "A1").team("y", "A2").team("z", "A2");
    l.beat("a", "b").beat("b", "a").beat("a", "x").beat("x", "b").beat("b", "y").beat("y", "a").beat("z", "x");
    Table t = l.table(7);
    assertThat(keys(t.divisions().get("A1"))).startsWith("a", "b");
    assertThat(first(t, "A1").tiebreak()).isEqualTo(Tiebreak.DIVISION);
  }

  @Test
  void jogos_em_comum_decidem_sem_confronto_direto_nem_jogo_de_divisao() {
    League l = new League().team("a", "A1").team("b", "A1");
    l.beat("a", "c").beat("a", "d").beat("e", "a").beat("f", "a");
    l.beat("b", "c").beat("d", "b").beat("b", "g").beat("h", "b");
    Table t = l.table(7);
    assertThat(keys(t.divisions().get("A1"))).containsExactly("a", "b");
    assertThat(first(t, "A1").tiebreak()).isEqualTo(Tiebreak.COMMON_GAMES);
  }

  @Test
  void campanha_na_conferencia_decide_sem_jogos_em_comum() {
    League l = new League().team("a", "A1").team("b", "A1").team("c", "A2").team("d", "A2");
    l.beat("a", "c").beat("x", "a");
    l.beat("b", "y").beat("d", "b");
    Table t = l.table(7);
    assertThat(keys(t.divisions().get("A1"))).containsExactly("a", "b");
    assertThat(first(t, "A1").tiebreak()).isEqualTo(Tiebreak.CONFERENCE);
  }

  @Test
  void forca_das_vitorias_decide_com_conferencia_empatada() {
    League l = new League().team("a", "A1").team("b", "A1");
    l.beat("a", "c").beat("e", "a").beat("b", "d").beat("f", "b");
    l.beat("c", "g").beat("c", "h").beat("g2", "d");
    Table t = l.table(7);
    assertThat(keys(t.divisions().get("A1"))).containsExactly("a", "b");
    assertThat(first(t, "A1").tiebreak()).isEqualTo(Tiebreak.STRENGTH_OF_VICTORY);
  }

  @Test
  void forca_da_tabela_decide_com_as_vitorias_empatadas() {
    League l = new League().team("a", "A1").team("b", "A1");
    l.beat("a", "c").beat("e", "a").beat("b", "d").beat("f", "b");
    l.beat("c", "g").beat("d", "h");
    l.beat("e", "i").beat("j", "f");
    Table t = l.table(7);
    assertThat(keys(t.divisions().get("A1"))).containsExactly("a", "b");
    assertThat(first(t, "A1").tiebreak()).isEqualTo(Tiebreak.STRENGTH_OF_SCHEDULE);
  }

  @Test
  void saldo_de_pontos_decide_com_vitorias_e_tabela_empatadas() {
    League l = new League().team("a", "A1").team("b", "A1");
    l.game("a", 30, "c", 0).game("e", 3, "a", 0).game("b", 7, "d", 6).game("f", 3, "b", 0);
    l.beat("c", "g").beat("d", "h").beat("k", "e").beat("l", "f");
    Table t = l.table(7);
    assertThat(keys(t.divisions().get("A1"))).containsExactly("a", "b");
    assertThat(first(t, "A1").tiebreak()).isEqualTo(Tiebreak.POINT_DIFFERENTIAL);
    assertThat(t.line("a").pointDiff()).isEqualTo(27);
  }

  @Test
  void sem_jogos_a_projecao_usa_prestigio_e_depois_nome() {
    League l = new League().team("m", "A1", 60).team("n", "A1", 80).team("o", "A1", 60);
    Table t = l.table(7);
    assertThat(keys(t.divisions().get("A1"))).containsExactly("n", "m", "o");
    assertThat(t.divisions().get("A1")).extracting(Ranked::tiebreak).containsExactly(Tiebreak.PROJECTION, Tiebreak.PROJECTION, null);
  }

  @Test
  void empate_total_vai_ao_sorteio_pela_semente_deterministico() {
    League l = new League().team("a", "A1").team("b", "A1");
    l.game("a", 10, "c", 0).game("b", 10, "d", 0);
    Table t1 = l.table(7), t2 = l.table(7);
    assertThat(first(t1, "A1").tiebreak()).isEqualTo(Tiebreak.DRAW);
    assertThat(keys(t1.divisions().get("A1"))).isEqualTo(keys(t2.divisions().get("A1")));
  }

  @Test
  void tres_empatados_na_divisao_o_grupo_recomeca_depois_de_cada_colocacao() {
    League l = new League().team("a", "A1").team("b", "A1").team("c", "A1");
    l.beat("a", "b").beat("a", "c").beat("b", "c");
    l.beat("x", "a").beat("y", "a");
    l.beat("b", "z").beat("w", "b");
    l.beat("c", "p").beat("c", "q");
    Table t = l.table(7);
    assertThat(keys(t.divisions().get("A1"))).containsExactly("a", "b", "c");
    assertThat(t.divisions().get("A1")).extracting(Ranked::tiebreak).containsExactly(Tiebreak.HEAD_TO_HEAD, Tiebreak.HEAD_TO_HEAD, null);
  }

  @Test
  void empate_de_tres_que_vira_empate_de_dois_recomeca_no_confronto_direto() {
    League l = new League().team("a", "A1").team("b", "A1").team("c", "A1").team("d", "A1");
    // confronto direto circular (a > b > c > a), a divisão tira c, e a × b recomeça do confronto direto: a venceu b
    l.beat("a", "b").beat("b", "c").beat("c", "a");
    l.beat("a", "d").beat("b", "d").beat("d", "c");
    l.beat("y", "a").beat("z", "b").beat("c", "x");
    Standings.Table t = l.table(7);
    assertThat(keys(t.divisions().get("A1"))).startsWith("a", "b", "c");
    assertThat(first(t, "A1").tiebreak()).isEqualTo(Tiebreak.HEAD_TO_HEAD);
  }

  @Test
  void wild_card_so_a_mais_bem_colocada_de_cada_divisao_disputa() {
    League l = new League().team("a1", "A1").team("a2", "A1").team("a3", "A1").team("b1", "A2").team("b2", "A2");
    for (int i = 1; i <= 11; i++) l.team("c" + i, i <= 6 ? "B1" : "B2");
    l.beat("a1", "a2").beat("a1", "a3").beat("a1", "c1");
    l.beat("a2", "a3").beat("a2", "c2").beat("a2", "c3");
    l.beat("a3", "c4").beat("a3", "c5").beat("a3", "c6");
    l.beat("b2", "a2").beat("b2", "c7").beat("b2", "c8").beat("c9", "b2");
    l.beat("b1", "b2").beat("b1", "c10").beat("b1", "c11");
    Table t = l.table(3);
    List<Seed> seeds = t.seeds().get("A");
    assertThat(seeds).extracting(Seed::key).containsExactly("a1", "b1", "b2");
    assertThat(seeds).extracting(Seed::divisionChampion).containsExactly(true, true, false);
    assertThat(seeds.get(0).tiebreak()).isEqualTo(Tiebreak.STRENGTH_OF_VICTORY);
    // a3 tem a mesma campanha de b2 e de a2, mas fica atrás de a2 na divisão: não disputa a vaga enquanto a2 estiver no grupo
    assertThat(seeds.get(2).tiebreak()).isEqualTo(Tiebreak.HEAD_TO_HEAD);
    assertThat(keys(t.conferences().get("A"))).containsExactly("a1", "b1", "b2", "a2", "a3");
    assertThat(t.seedOf("a2")).isNull();
  }

  @Test
  void liga_padrao_tem_7_sementes_por_conferencia_com_os_4_campeoes_de_divisao_nas_primeiras() {
    List<Team> teams = new ArrayList<>();
    List<ScheduleGenerator.Team> gen = new ArrayList<>();
    for (int c = 0; c < 2; c++) for (int d = 0; d < 4; d++) for (int p = 0; p < 4; p++) {
      String key = c + "-" + d + "-" + p;
      teams.add(new Team(key, "C" + c, "C" + c + "D" + d, 50, key));
      gen.add(new ScheduleGenerator.Team(key, c, d, p));
    }
    ScheduleGenerator.Schedule s = ScheduleGenerator.regularSeason(gen, 18, 17, 99L);
    List<Game> games = new ArrayList<>();
    for (ScheduleGenerator.Game g : s.games()) {
      long h = br.com.velion.scoresaf.domain.career.SeedPolicy.derive(5L, "score", g.week(), g.homeKey().hashCode(), g.awayKey().hashCode());
      games.add(new Game(g.week(), g.homeKey(), g.awayKey(), (int) Math.floorMod(h, 35L), (int) Math.floorMod(h >> 8, 35L)));
    }
    Table t = Standings.compute(teams, games, 7, 3L);
    for (String conf : List.of("C0", "C1")) {
      List<Seed> seeds = t.seeds().get(conf);
      assertThat(seeds).hasSize(7);
      assertThat(seeds).extracting(Seed::seed).containsExactly(1, 2, 3, 4, 5, 6, 7);
      assertThat(seeds.subList(0, 4)).allMatch(Seed::divisionChampion);
      assertThat(seeds.subList(4, 7)).noneMatch(Seed::divisionChampion);
      assertThat(seeds.subList(0, 4).stream().map(x -> t.line(x.key()).team().division()).distinct()).hasSize(4);
      for (Seed x : seeds.subList(0, 4)) assertThat(t.divisions().get(t.line(x.key()).team().division()).get(0).line().team().key()).isEqualTo(x.key());
      // wild cards: ninguém de fora com percentual maior que o pior wild card
      double worstWildCard = t.line(seeds.get(6).key()).overall().pct();
      List<String> seeded = seeds.stream().map(Seed::key).toList();
      t.conferences().get(conf).stream().filter(r -> !seeded.contains(r.line().team().key()))
          .forEach(r -> assertThat(r.line().overall().pct()).isLessThanOrEqualTo(worstWildCard));
      assertThat(t.conferences().get(conf)).hasSize(16);
    }
  }

  @Test
  void linha_traz_casa_fora_divisao_conferencia_pontos_sequencia_e_ultimos_cinco() {
    League l = new League().team("a", "A1").team("b", "A1").team("c", "A2");
    l.game("a", 21, "b", 7).game("c", 3, "a", 10).game("a", 14, "x", 17).game("y", 0, "a", 24).game("a", 7, "b", 7).game("z", 3, "a", 13);
    Standings.Line a = l.table(7).line("a");
    assertThat(a.overall()).isEqualTo(new Standings.Record(4, 1, 1));
    assertThat(a.overall().pct()).isEqualTo(0.75);
    assertThat(a.home()).isEqualTo(new Standings.Record(1, 1, 1));
    assertThat(a.away()).isEqualTo(new Standings.Record(3, 0, 0));
    assertThat(a.division()).isEqualTo(new Standings.Record(1, 0, 1));
    assertThat(a.conference()).isEqualTo(new Standings.Record(2, 0, 1));
    assertThat(a.pointsFor()).isEqualTo(89);
    assertThat(a.pointsAgainst()).isEqualTo(37);
    assertThat(a.streak()).isEqualTo("W1");
    assertThat(a.lastFive()).containsExactly("W", "L", "W", "T", "W");
  }
}
