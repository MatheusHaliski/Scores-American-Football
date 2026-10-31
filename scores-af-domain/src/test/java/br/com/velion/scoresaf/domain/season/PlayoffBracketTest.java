package br.com.velion.scoresaf.domain.season;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.season.PlayoffBracket.Matchup;
import br.com.velion.scoresaf.domain.season.PlayoffBracket.Result;
import br.com.velion.scoresaf.domain.season.PlayoffBracket.Round;
import br.com.velion.scoresaf.domain.season.PlayoffBracket.Seed;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.shared.WeekKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Chave do RF66 (D-96): 7 sementes por conferência com folga só da 1ª na primeira rodada, mando da melhor semente, re-seed a cada
 * rodada (a melhor viva pega a pior), final entre as campeãs de conferência em campo neutro e as variantes pelas regras da liga
 * (college: 6 sementes e 2 folgas; uma conferência só).
 */
class PlayoffBracketTest {

  private static List<Seed> seeds(int perConference, String... conferences) {
    List<Seed> out = new ArrayList<>();
    for (String c : conferences) for (int s = 1; s <= perConference; s++) out.add(new Seed(c + s, c, s, s <= 4));
    return out;
  }

  private static List<String> pairs(Round r) { return r.games().stream().map(m -> m.home() + "x" + m.away()).toList(); }

  @Test
  void sete_por_conferencia_folga_da_semente_1_e_mando_da_melhor_semente() {
    List<Seed> s = seeds(7, "A", "N");
    assertThat(PlayoffBracket.rounds(Map.of("A", 7, "N", 7), 1)).isEqualTo(4);
    Round r = PlayoffBracket.next(s, 1, true, List.of());
    assertThat(r.index()).isZero();
    assertThat(r.kind()).isEqualTo(WeekKind.WILD_CARD);
    assertThat(pairs(r)).containsExactly("A2xA7", "A3xA6", "A4xA5", "N2xN7", "N3xN6", "N4xN5");
    assertThat(r.byes()).extracting(Seed::club).containsExactly("A1", "N1");
    assertThat(r.games()).noneMatch(Matchup::neutralSite);
  }

  @Test
  void re_seed_a_semente_1_pega_a_pior_semente_viva() {
    List<Seed> s = seeds(7, "A", "N");
    List<Result> wc = new ArrayList<>(List.of(
        new Result(0, "A2", "A7", "A7"), new Result(0, "A3", "A6", "A3"), new Result(0, "A4", "A5", "A5"),
        new Result(0, "N2", "N7", "N2"), new Result(0, "N3", "N6", "N6"), new Result(0, "N4", "N5", "N4")));
    Round div = PlayoffBracket.next(s, 1, true, wc);
    assertThat(div.kind()).isEqualTo(WeekKind.DIVISIONAL);
    // sem re-seed seria A1 × vencedor de 4×5; com re-seed, A1 pega a A7 (a pior viva)
    assertThat(pairs(div)).containsExactly("A1xA7", "A3xA5", "N1xN6", "N2xN4");
    assertThat(div.byes()).isEmpty();
    wc.addAll(List.of(new Result(1, "A1", "A7", "A7"), new Result(1, "A3", "A5", "A3"), new Result(1, "N1", "N6", "N1"), new Result(1, "N2", "N4", "N4")));
    Round conf = PlayoffBracket.next(s, 1, true, wc);
    assertThat(conf.kind()).isEqualTo(WeekKind.CONFERENCE);
    assertThat(pairs(conf)).containsExactly("A3xA7", "N1xN4");
    wc.addAll(List.of(new Result(2, "A3", "A7", "A7"), new Result(2, "N1", "N4", "N1")));
    Round fin = PlayoffBracket.next(s, 1, true, wc);
    assertThat(fin.kind()).isEqualTo(WeekKind.FINAL);
    // final entre as conferências: em campo neutro; "mandante" é a melhor semente
    assertThat(pairs(fin)).containsExactly("N1xA7");
    assertThat(fin.games()).singleElement().satisfies(m -> {
      assertThat(m.neutralSite()).isTrue();
      assertThat(m.conference()).isNull();
    });
    assertThat(PlayoffBracket.champion(s, 1, wc)).isNull();
    wc.add(new Result(3, "N1", "A7", "A7"));
    assertThat(PlayoffBracket.next(s, 1, true, wc)).isNull();
    assertThat(PlayoffBracket.champion(s, 1, wc)).isEqualTo("A7");
  }

  @Test
  void final_sem_campo_neutro_e_em_casa_da_melhor_semente() {
    List<Seed> s = seeds(1, "A", "N");
    Round fin = PlayoffBracket.next(s, 0, false, List.of());
    assertThat(fin.kind()).isEqualTo(WeekKind.FINAL);
    assertThat(fin.games()).singleElement().satisfies(m -> assertThat(m.neutralSite()).isFalse());
  }

  @Test
  void college_6_sementes_com_2_folgas() {
    List<Seed> s = seeds(6, "A", "N");
    assertThat(PlayoffBracket.rounds(Map.of("A", 6, "N", 6), 2)).isEqualTo(4);
    Round r = PlayoffBracket.next(s, 2, true, List.of());
    assertThat(pairs(r)).containsExactly("A3xA6", "A4xA5", "N3xN6", "N4xN5");
    assertThat(r.byes()).extracting(Seed::club).containsExactly("A1", "A2", "N1", "N2");
  }

  @Test
  void uma_conferencia_so_a_ultima_rodada_dela_e_a_final() {
    List<Seed> s = seeds(4, "A");
    assertThat(PlayoffBracket.rounds(Map.of("A", 4), 0)).isEqualTo(2);
    Round semi = PlayoffBracket.next(s, 0, true, List.of());
    assertThat(semi.kind()).isEqualTo(WeekKind.CONFERENCE);
    assertThat(pairs(semi)).containsExactly("A1xA4", "A2xA3");
    Round fin = PlayoffBracket.next(s, 0, true, List.of(new Result(0, "A1", "A4", "A1"), new Result(0, "A2", "A3", "A3")));
    assertThat(fin.kind()).isEqualTo(WeekKind.FINAL);
    assertThat(pairs(fin)).containsExactly("A1xA3");
    assertThat(fin.games().get(0).neutralSite()).isTrue();
  }

  @Test
  void numero_impar_de_vivas_da_folga_a_melhor_semente() {
    List<Seed> s = seeds(5, "A", "N");
    Round r = PlayoffBracket.next(s, 0, true, List.of());
    assertThat(pairs(r)).containsExactly("A2xA5", "A3xA4", "N2xN5", "N3xN4");
    assertThat(r.byes()).extracting(Seed::club).containsExactly("A1", "N1");
  }

  /** Toda combinação de sementes (1–8) e folgas (0..sementes) que o editor aceita, com uma e duas conferências (D-123). */
  static Stream<Arguments> regrasAceitas() {
    List<Arguments> out = new ArrayList<>();
    for (int n = 1; n <= 8; n++) {
      for (int b = 0; b <= n; b++) {
        LeagueRules rules = LeagueRules.pro().toBuilder().playoffSeedsPerConference(n).playoffByeSeeds(b).build();
        if (rules.violations().contains("playoffSeedsPerConference") || rules.violations().contains("playoffByeSeeds")) continue;
        out.add(Arguments.of(n, b, 1));
        out.add(Arguments.of(n, b, 2));
      }
    }
    return out.stream();
  }

  @ParameterizedTest(name = "{0} sementes, {1} folgas, {2} conferência(s)")
  @MethodSource("regrasAceitas")
  void toda_regra_aceita_pelo_editor_tem_nome_para_cada_rodada_e_chega_a_final(int perConference, int byes, int conferences) {
    String[] confs = conferences == 1 ? new String[] {"A"} : new String[] {"A", "N"};
    List<Seed> s = seeds(perConference, confs);
    Map<String, Integer> perConf = new LinkedHashMap<>();
    for (String c : confs) perConf.put(c, perConference);
    int total = PlayoffBracket.rounds(perConf, byes);
    List<WeekKind> kinds = PlayoffBracket.kinds(total);
    assertThat(kinds).hasSize(total).doesNotHaveDuplicates();
    if (total > 0) assertThat(kinds.get(total - 1)).isEqualTo(WeekKind.FINAL);
    // a melhor semente sempre vence e, de novo, a pior sempre vence: as duas chaves chegam à campeã sem lançar exceção
    for (boolean favorite : new boolean[] {true, false}) {
      List<Result> results = new ArrayList<>();
      int played = 0;
      for (Round r = PlayoffBracket.next(s, byes, true, results); r != null; r = PlayoffBracket.next(s, byes, true, results)) {
        assertThat(r.index()).isEqualTo(played);
        assertThat(r.kind()).isEqualTo(kinds.get(r.index()));
        assertThat(r.games()).isNotEmpty();
        for (Matchup m : r.games()) results.add(new Result(r.index(), m.home(), m.away(), favorite ? m.home() : m.away()));
        played++;
      }
      assertThat(played).isEqualTo(total);
      assertThat(PlayoffBracket.champion(s, byes, results)).isNotNull();
    }
  }

  @Test
  void oito_sementes_e_uma_folga_em_duas_conferencias_tem_cinco_rodadas_com_a_preliminar() {
    assertThat(PlayoffBracket.rounds(Map.of("A", 8, "N", 8), 1)).isEqualTo(5);
    assertThat(PlayoffBracket.kinds(5)).containsExactly(WeekKind.PRELIMINARY, WeekKind.WILD_CARD, WeekKind.DIVISIONAL, WeekKind.CONFERENCE, WeekKind.FINAL);
    Round r = PlayoffBracket.next(seeds(8, "A", "N"), 1, true, List.of());
    assertThat(r.kind()).isEqualTo(WeekKind.PRELIMINARY);
  }
}
