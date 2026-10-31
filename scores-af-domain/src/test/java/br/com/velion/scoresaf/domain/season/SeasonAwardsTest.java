package br.com.velion.scoresaf.domain.season;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.season.SeasonAwards.Award;
import br.com.velion.scoresaf.domain.season.SeasonAwards.PlayerLine;
import br.com.velion.scoresaf.domain.season.SeasonAwards.TeamLine;
import br.com.velion.scoresaf.domain.shared.AwardType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Prêmios do RF35 (D-98): o MVP pesa o sucesso da franquia, OPOY e DPOY pela produção, calouros só entre calouros, mínimo de 2/3
 * dos jogos, head coach do ano pela campanha acima da projeção e líderes por categoria.
 */
class SeasonAwardsTest {

  private static PlayerLine qb(String id, String club, boolean rookie, int games, int yards, int td) {
    return new PlayerLine(id, club, id, "QB", rookie, games, 500, 330, yards, td, 8, 20, 30, 100, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0);
  }

  private static PlayerLine de(String id, String club, boolean rookie, int games, int sacks) {
    return new PlayerLine(id, club, id, "DE", rookie, games, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 40, 10, sacks, 0, 2, 2, 1,
        0, 0, 0, 0, 0, 0);
  }

  private static Map<AwardType, Award> byType(List<Award> l) { return l.stream().collect(Collectors.toMap(Award::type, Function.identity())); }

  @Test
  void mvp_pesa_o_sucesso_da_franquia_e_opoy_e_a_maior_producao() {
    List<PlayerLine> players = List.of(qb("estrela", "ruim", false, 17, 4600, 34), qb("lider", "boa", false, 17, 4300, 31), de("rocha", "boa", false, 17, 15),
        qb("calouro", "media", true, 16, 3200, 20), de("novato", "media", true, 17, 9), qb("pouco", "boa", false, 5, 1500, 12));
    List<TeamLine> teams = List.of(new TeamLine("ruim", "HC Ruim", 4, 17, 0.235, 50), new TeamLine("boa", "HC Boa", 14, 17, 0.824, 60),
        new TeamLine("media", "HC Media", 9, 17, 0.529, 30));
    Map<AwardType, Award> a = byType(SeasonAwards.compute(players, teams, 17));
    assertThat(a.get(AwardType.MVP).playerId()).isEqualTo("lider");
    assertThat(a.get(AwardType.OPOY).playerId()).isEqualTo("estrela");
    assertThat(a.get(AwardType.DPOY).playerId()).isEqualTo("rocha");
    assertThat(a.get(AwardType.OROY).playerId()).isEqualTo("calouro");
    assertThat(a.get(AwardType.DROY).playerId()).isEqualTo("novato");
    // HC Media: 9 vitórias com projeção de 17 × 0,30 = 5,1 (+3,9); HC Boa: 14 − 10,2 = +3,8
    assertThat(a.get(AwardType.COACH).clubId()).isEqualTo("media");
    assertThat(a.get(AwardType.COACH).playerId()).isNull();
    assertThat(a.get(AwardType.MVP).statLine()).containsEntry("passYards", 4300).containsEntry("games", 17);
  }

  @Test
  void quem_jogou_menos_de_dois_tercos_dos_jogos_nao_concorre() {
    assertThat(SeasonAwards.minGames(17)).isEqualTo(12);
    assertThat(SeasonAwards.minGames(1)).isEqualTo(1);
    List<Award> a = SeasonAwards.compute(List.of(qb("pouco", "x", false, 11, 6000, 60)), List.of(new TeamLine("x", "HC", 1, 17, 0.1, 50)), 17);
    assertThat(a).extracting(Award::type).containsExactly(AwardType.COACH);
  }

  @Test
  void lideres_por_categoria_em_ordem_e_so_quem_registrou_algo() {
    var leaders = SeasonAwards.leaders(List.of(qb("a", "x", false, 17, 4000, 30), qb("b", "y", false, 17, 4200, 25), de("c", "z", false, 17, 12)), 5);
    assertThat(leaders.get("PASS_YARDS")).extracting(SeasonAwards.Leader::playerId).containsExactly("b", "a");
    assertThat(leaders.get("PASS_TOUCHDOWNS")).extracting(SeasonAwards.Leader::value).containsExactly(30, 25);
    assertThat(leaders.get("SACKS")).extracting(SeasonAwards.Leader::playerId).containsExactly("c");
    assertThat(leaders.get("INTERCEPTIONS")).isEmpty();
    assertThat(leaders.keySet()).containsExactlyElementsOf(SeasonAwards.LEADER_CATEGORIES);
  }
}
