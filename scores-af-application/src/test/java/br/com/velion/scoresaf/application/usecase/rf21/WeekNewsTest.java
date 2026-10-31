package br.com.velion.scoresaf.application.usecase.rf21;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.application.service.StandingsService.Clinch;
import br.com.velion.scoresaf.domain.season.Standings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Notícias por regras do RF21 (D-103): o jogo do usuário vem primeiro, o atropelo e o jogo emocionante saem dos outros jogos, os
 * destaques respeitam os limites, quem garante vaga ou cai entra na semana em que muda, e na semana de playoff cada jogo vira notícia,
 * com a campeã na final.
 */
class WeekNewsTest {
  private static final UUID A = id(1), B = id(2), C = id(3), D = id(4), E = id(5), F = id(6), QB = id(10), RB = id(11);

  private static UUID id(int n) { return new UUID(0, n); }

  private static Standings.Table table(List<Standings.Game> games) {
    List<Standings.Team> teams = new ArrayList<>();
    for (UUID t : List.of(A, B, C, D, E, F)) teams.add(new Standings.Team(t.toString(), "X", t.equals(A) || t.equals(B) || t.equals(C) ? "X1" : "X2", 50, "T" + t.getLeastSignificantBits()));
    return Standings.compute(teams, games, 2, 1L);
  }

  private static Map<UUID, String> names() {
    Map<UUID, String> m = new HashMap<>();
    for (UUID t : List.of(A, B, C, D, E, F)) m.put(t, "T" + t.getLeastSignificantBits());
    return m;
  }

  @Test
  void semana_regular_usuario_primeiro_atropelo_emocao_destaques_e_quem_garantiu_vaga() {
    List<WeekNews.Game> games = List.of(new WeekNews.Game(id(100), B, "T2", A, "T1", 17, 20, false), new WeekNews.Game(id(101), C, "T3", D, "T4", 42, 7, false),
        new WeekNews.Game(id(102), E, "T5", F, "T6", 23, 20, true));
    List<WeekNews.Line> lines = List.of(new WeekNews.Line(QB, "Quarterback Bom", C, id(101), 350, 4, 0, 0, 0, 0, 0, 0),
        new WeekNews.Line(RB, "Corredor Fraco", E, id(102), 0, 0, 90, 1, 0, 0, 0, 0));
    Standings.Table after = table(List.of(new Standings.Game(1, B.toString(), A.toString(), 17, 20), new Standings.Game(1, C.toString(), D.toString(), 42, 7),
        new Standings.Game(1, E.toString(), F.toString(), 23, 20)));
    WeekNews.Input in = new WeekNews.Input(1, false, null, false, false, 2026, A, games, lines, names(), after, Map.of(), Map.of(A, Clinch.PLAYOFFS),
        Map.of(), Map.of());
    List<WeekNews.Item> items = WeekNews.build(in);
    assertThat(items).extracting(WeekNews.Item::kind).containsExactly("USER_WIN", "BLOWOUT", "THRILLER", "PASSING", "CLINCH_PLAYOFFS");
    assertThat(items.get(0).params()).containsEntry("pointsFor", 20).containsEntry("pointsAgainst", 17).containsEntry("record", "1-0");
    assertThat(items.get(1).params()).containsEntry("margin", 35);
    assertThat(items.get(2).params()).containsEntry("overtime", 1);
    assertThat(items.get(3).playerId()).isEqualTo(QB);
  }

  @Test
  void semana_de_playoff_vira_um_resultado_por_jogo_e_a_final_traz_a_campea() {
    Standings.Table t = table(List.of());
    List<WeekNews.Game> games = List.of(new WeekNews.Game(id(200), C, "T3", D, "T4", 10, 13, true), new WeekNews.Game(id(201), A, "T1", B, "T2", 31, 3, false));
    List<WeekNews.Item> wc = WeekNews.build(new WeekNews.Input(19, true, "Wild Card", false, false, 2026, A, games, List.of(), names(), t, Map.of(), Map.of(),
        Map.of(), Map.of()));
    assertThat(wc).extracting(WeekNews.Item::kind).containsExactly("PLAYOFF_RESULT", "PLAYOFF_RESULT");
    assertThat(wc.get(0).clubId()).isEqualTo(A);
    assertThat(wc.get(0).tone()).isEqualTo("POSITIVE");
    assertThat(wc.get(1).params()).containsEntry("winner", "T4").containsEntry("overtime", 1);
    List<WeekNews.Item> fin = WeekNews.build(new WeekNews.Input(22, true, "final", true, false, 2026, E, List.of(games.get(1)), List.of(), names(), t,
        Map.of(), Map.of(), Map.of(), Map.of()));
    assertThat(fin).singleElement().satisfies(i -> {
      assertThat(i.kind()).isEqualTo("CHAMPION");
      assertThat(i.params()).containsEntry("team", "T1").containsEntry("year", 2026);
    });
  }
}
