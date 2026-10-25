package br.com.velion.scoresaf.domain.season;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.season.ScheduleGenerator.Game;
import br.com.velion.scoresaf.domain.season.ScheduleGenerator.Schedule;
import br.com.velion.scoresaf.domain.season.ScheduleGenerator.Team;
import br.com.velion.scoresaf.domain.shared.KickoffSlot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * Calendário da temporada regular (RF21/RF34): 17 jogos e 1 bye por franquia em 18 semanas, 6 jogos de divisão (um em casa e um fora
 * contra cada rival), ninguém joga duas vezes na mesma semana, mando equilibrado, horários de quinta/domingo/segunda e determinismo
 * pela semente. Também ligas fora do padrão do editor (8–32 franquias, número ímpar, divisões desiguais).
 */
class ScheduleGeneratorTest {

  /** Liga padrão: 2 conferências × 4 divisões × 4 franquias (chaves "c-d-p"). */
  static List<Team> standard() {
    List<Team> teams = new ArrayList<>();
    for (int c = 0; c < 2; c++) for (int d = 0; d < 4; d++) for (int p = 0; p < 4; p++) teams.add(new Team(c + "-" + d + "-" + p, c, d, p));
    return teams;
  }

  /** Liga com {@code n} franquias em {@code conferences} conferências × {@code divisions} divisões, tamanhos diferindo em no máximo 1. */
  static List<Team> league(int n, int conferences, int divisions) {
    List<Team> teams = new ArrayList<>();
    int groups = conferences * divisions;
    int[] place = new int[groups];
    for (int i = 0; i < n; i++) {
      int g = i % groups;
      teams.add(new Team("t" + i, g / divisions, g % divisions, place[g]++));
    }
    return teams;
  }

  @ParameterizedTest(name = "semente {0}")
  @ValueSource(longs = {1L, 7L, 42L, 20261025L, -99L, 123456789L})
  void liga_padrao_17_jogos_1_bye_6_de_divisao_sem_jogo_duplo_na_semana(long seed) {
    List<Team> teams = standard();
    Schedule s = ScheduleGenerator.regularSeason(teams, 18, 17, seed);
    assertThat(s.gamesPerTeam()).isEqualTo(17);
    assertThat(s.games()).hasSize(32 * 17 / 2);
    assertInvariants(teams, s, 18, 17);
    Map<String, Team> byKey = new HashMap<>();
    teams.forEach(t -> byKey.put(t.key(), t));
    for (Team t : teams) {
      List<Game> mine = s.games().stream().filter(g -> g.homeKey().equals(t.key()) || g.awayKey().equals(t.key())).toList();
      List<Game> div = mine.stream().filter(Game::divisional).toList();
      assertThat(div).as("jogos de divisão de " + t.key()).hasSize(6);
      for (Team r : teams) {
        if (r == t || r.conference() != t.conference() || r.division() != t.division()) continue;
        assertThat(div.stream().filter(g -> g.homeKey().equals(t.key()) && g.awayKey().equals(r.key())).count()).as("em casa contra " + r.key()).isEqualTo(1);
        assertThat(div.stream().filter(g -> g.awayKey().equals(t.key()) && g.homeKey().equals(r.key())).count()).as("fora contra " + r.key()).isEqualTo(1);
      }
      // fórmula da liga: 4 contra uma divisão da conferência + 2 de mesma colocação = 6 de conferência fora da divisão; 5 contra a outra
      long conf = mine.stream().filter(g -> g.conference() && !g.divisional()).count();
      assertThat(conf).isEqualTo(6);
      assertThat(mine.stream().filter(g -> !g.conference()).count()).isEqualTo(5);
      assertThat(mine.stream().map(g -> g.homeKey().equals(t.key()) ? g.awayKey() : g.homeKey()).filter(o -> !byKey.get(o).equals(t))
          .filter(o -> byKey.get(o).conference() != t.conference() || byKey.get(o).division() != t.division()).distinct().count())
          .as("adversários fora da divisão não se repetem").isEqualTo(11);
    }
  }

  @Test
  void mesma_semente_mesmo_calendario_e_semente_diferente_muda() {
    Schedule a = ScheduleGenerator.regularSeason(standard(), 18, 17, 42L);
    List<Team> shuffled = new ArrayList<>(standard());
    Collections.shuffle(shuffled, new java.util.Random(3));
    Schedule b = ScheduleGenerator.regularSeason(shuffled, 18, 17, 42L);
    assertThat(b.games()).as("a ordem de entrada não muda o calendário").isEqualTo(a.games());
    assertThat(ScheduleGenerator.regularSeason(standard(), 18, 17, 43L).games()).isNotEqualTo(a.games());
  }

  /** O mundo da carreira é um clone (ids novos): a mesma estrutura com chaves diferentes gera o mesmo calendário. */
  @Test
  void chaves_diferentes_mesma_estrutura_mesmo_calendario() {
    List<Team> renamed = standard().stream().map(t -> new Team("x" + (31 - t.conference() * 16 - t.division() * 4 - t.place()) + "-" + t.key().hashCode(),
        t.conference(), t.division(), t.place())).toList();
    Map<String, String> back = new HashMap<>();
    for (int i = 0; i < renamed.size(); i++) back.put(renamed.get(i).key(), standard().get(i).key());
    List<Game> a = ScheduleGenerator.regularSeason(standard(), 18, 17, 9L).games();
    List<Game> b = ScheduleGenerator.regularSeason(renamed, 18, 17, 9L).games().stream()
        .map(g -> new Game(g.week(), back.get(g.homeKey()), back.get(g.awayKey()), g.slot(), g.divisional(), g.conference())).toList();
    assertThat(b).isEqualTo(a);
  }

  @ParameterizedTest(name = "{0} franquias, {1} conf × {2} div")
  @CsvSource({"8,1,2", "8,2,1", "18,1,2", "26,1,3", "9,1,3", "10,2,1", "12,2,2", "14,2,2", "16,2,2", "17,2,2", "20,2,4", "24,2,3", "28,2,4", "31,2,4", "30,1,4", "32,1,4", "8,1,1"})
  void ligas_do_editor_fecham_com_bye_e_sem_jogo_duplo(int n, int conferences, int divisions) {
    List<Team> teams = league(n, conferences, divisions);
    Schedule s = ScheduleGenerator.regularSeason(teams, 18, 17, 11L * n);
    int g = s.gamesPerTeam();
    if (n % 2 == 0) assertThat(g).isEqualTo(17);
    else assertThat(g).isEqualTo(16);
    assertInvariants(teams, s, 18, g);
  }

  static void assertInvariants(List<Team> teams, Schedule s, int weeks, int games) {
    Map<String, Integer> played = new HashMap<>();
    Map<String, Integer> home = new HashMap<>();
    Map<String, Set<Integer>> weeksOf = new HashMap<>();
    for (Game g : s.games()) {
      assertThat(g.week()).isBetween(1, weeks);
      assertThat(g.homeKey()).isNotEqualTo(g.awayKey());
      for (String k : List.of(g.homeKey(), g.awayKey())) {
        played.merge(k, 1, Integer::sum);
        assertThat(weeksOf.computeIfAbsent(k, x -> new HashSet<>()).add(g.week())).as(k + " joga duas vezes na semana " + g.week()).isTrue();
      }
      home.merge(g.homeKey(), 1, Integer::sum);
    }
    for (Team t : teams) {
      assertThat(played.get(t.key())).as("jogos de " + t.key()).isEqualTo(games);
      assertThat(weeks - weeksOf.get(t.key()).size()).as("folgas de " + t.key()).isEqualTo(weeks - games);
      int h = home.getOrDefault(t.key(), 0);
      assertThat(Math.abs(2 * h - games)).as("mando de " + t.key()).isLessThanOrEqualTo(1);
    }
    for (int w = 1; w <= weeks; w++) {
      List<Game> week = s.week(w);
      if (week.size() >= 3) {
        assertThat(week.stream().filter(g -> g.slot() == KickoffSlot.THU_NIGHT).count()).isEqualTo(1);
        assertThat(week.stream().filter(g -> g.slot() == KickoffSlot.MON_NIGHT).count()).isEqualTo(1);
      }
    }
  }

  @Test
  void byes_ficam_no_miolo_da_temporada_na_liga_padrao() {
    Schedule s = ScheduleGenerator.regularSeason(standard(), 18, 17, 5L);
    for (int w = 1; w <= 18; w++) {
      int teamsPlaying = s.week(w).size() * 2;
      if (w < 5 || w > 14) assertThat(teamsPlaying).as("semana " + w).isEqualTo(32);
    }
  }
}
