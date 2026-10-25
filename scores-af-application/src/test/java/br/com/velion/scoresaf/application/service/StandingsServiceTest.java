package br.com.velion.scoresaf.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.application.service.StandingsService.Clinch;
import br.com.velion.scoresaf.application.usecase.rf21.WeekNews;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.catalog.Division;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Standings;
import br.com.velion.scoresaf.domain.shared.FixtureStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Situação garantida da classificação (RF16, D-95) com as correções da revisão do bloco 6c (D-124, D-125): só está eliminado quem
 * também não pode mais ganhar a divisão; a vaga garantida conta os campeões de divisões fracas, que entram à frente com campanha pior;
 * a folga garantida exige o título da divisão (ou vaga folgada mesmo contando esses campeões). As notícias do RF21 seguem a mesma conta.
 */
class StandingsServiceTest {
  private final StandingsService service = new StandingsService(null, null, null);

  /**
   * Liga do teste: a conferência "A" com as divisões e campanhas de cada cenário e a conferência "Z" com uma franquia de fachada, a
   * adversária de todos os jogos (fora da conferência, então não mexe nos desempates de conferência).
   */
  private static final class World {
    final List<Club> clubs = new ArrayList<>();
    final List<Conference> conferences = new ArrayList<>();
    final List<Division> divisions = new ArrayList<>();
    final List<Fixture> fixtures = new ArrayList<>();
    final Map<UUID, String> names = new HashMap<>();
    final Season season = new Season();
    final Conference main = conference("A", 0);
    final Club dummy;
    final int played;

    World(int regularSeasonWeeks, int played) {
      season.setRegularSeasonWeeks(regularSeasonWeeks);
      season.setScheduleSeed(7L);
      this.played = played;
      Conference z = conference("Z", 1);
      dummy = club("Fachada", z, division(z, "Z1"));
    }

    Conference conference(String name, int order) {
      Conference c = new Conference();
      c.setName(name); c.setShortName(name); c.setSortOrder(order);
      conferences.add(c);
      return c;
    }

    Division division(Conference c, String name) {
      Division d = new Division();
      d.setConference(c); d.setName(name); d.setSortOrder(divisions.size());
      divisions.add(d);
      return d;
    }

    Division division(String name) { return division(main, name); }

    Club club(String name, Conference c, Division d) {
      Club x = new Club();
      x.setName(name); x.setShortName(name); x.setConference(c); x.setDivision(d);
      clubs.add(x);
      names.put(x.getId(), name);
      return x;
    }

    /** Franquia da conferência "A" com {@code w} vitórias e {@code l} derrotas encerradas e um jogo por fazer na semana seguinte. */
    Club team(String name, Division d, int w, int l) {
      assertThat(w + l).isEqualTo(played);
      Club c = club(name, main, d);
      for (int i = 0; i < w + l; i++) {
        Fixture f = fixture(c, i + 1);
        f.setStatus(FixtureStatus.FINISHED); f.setHomeScore(i < w ? 21 : 10); f.setAwayScore(i < w ? 10 : 21);
      }
      fixture(c, played + 1);
      return c;
    }

    private Fixture fixture(Club home, int week) {
      Fixture f = new Fixture();
      f.setSeason(season); f.setWeekNumber(week); f.setHomeClub(home); f.setAwayClub(dummy);
      fixtures.add(f);
      return f;
    }

    StandingsService.League league() {
      Map<UUID, Club> byId = new LinkedHashMap<>();
      clubs.forEach(c -> byId.put(c.getId(), c));
      return new StandingsService.League(List.copyOf(clubs), byId, List.copyOf(conferences), List.copyOf(divisions));
    }
  }

  private Map<UUID, Clinch> clinches(World w, LeagueRules rules) {
    StandingsService.League league = w.league();
    Standings.Table table = service.table(w.season, league, w.fixtures, w.played, rules);
    return service.clinches(w.season, league, w.fixtures, w.played, table, rules);
  }

  /** Notícias de classificação da semana para a franquia (a situação antes da semana vazia: toda mudança vira notícia). */
  private List<String> news(World w, LeagueRules rules, Club club) {
    StandingsService.League league = w.league();
    Standings.Table table = service.table(w.season, league, w.fixtures, w.played, rules);
    Map<UUID, Clinch> after = service.clinches(w.season, league, w.fixtures, w.played, table, rules);
    WeekNews.Input in = new WeekNews.Input(w.played, false, null, false, false, 2026, null, List.of(), List.of(), w.names, table, Map.of(), after,
        Map.of(), Map.of());
    return WeekNews.build(in).stream().filter(i -> club.getId().equals(i.clubId())).map(WeekNews.Item::kind).toList();
  }

  /** Três divisões de quatro com campanhas dadas (vitórias; derrotas = jogados − vitórias). */
  private static void others(World w, int[]... wins) {
    for (int d = 0; d < wins.length; d++) {
      Division div = w.division("A" + (d + 2));
      for (int i = 0; i < wins[d].length; i++) w.team("A" + (d + 2) + "-" + i, div, wins[d][i], w.played - wins[d][i]);
    }
  }

  @Test
  void lider_de_divisao_fraca_nao_esta_eliminado_mesmo_com_sete_campanhas_melhores_na_conferencia() {
    World w = new World(18, 16);
    Division a1 = w.division("A1");
    Club x = w.team("X", a1, 6, 10);
    w.team("R1", a1, 5, 11); w.team("R2", a1, 5, 11);
    Club r3 = w.team("R3", a1, 4, 12);
    // 7 franquias da conferência com 8+ vitórias: com piso acima do teto de X (7 vitórias), mas X ainda pode ganhar a divisão
    others(w, new int[] {10, 9, 8, 3}, new int[] {11, 9, 3, 3}, new int[] {12, 8, 3, 3});
    Map<UUID, Clinch> c = clinches(w, LeagueRules.pro());
    assertThat(c.get(x.getId())).isNotEqualTo(Clinch.ELIMINATED);
    assertThat(news(w, LeagueRules.pro(), x)).doesNotContain("ELIMINATED");
    // quem não alcança mais nem o líder da divisão (R3: teto de 5 vitórias contra o piso de 6 de X) continua eliminado
    assertThat(c.get(r3.getId())).isEqualTo(Clinch.ELIMINATED);
    assertThat(news(w, LeagueRules.pro(), r3)).contains("ELIMINATED");
  }

  @Test
  void vaga_nao_esta_garantida_quando_seis_alcancam_e_o_campeao_de_uma_divisao_fraca_entra_a_frente() {
    World w = new World(18, 16);
    Division a1 = w.division("A1");
    Club x = w.team("X", a1, 11, 5);
    w.team("R1", a1, 10, 6); w.team("R2", a1, 4, 12); w.team("R3", a1, 3, 13);
    // 6 alcançam o piso de X (10+ vitórias, teto de 11): R1, mais 5 nas divisões A2 e A3; a A4 é fraca (ninguém passa de 8 vitórias)
    others(w, new int[] {12, 10, 10, 2}, new int[] {13, 10, 2, 2}, new int[] {8, 7, 6, 5});
    Map<UUID, Clinch> c = clinches(w, LeagueRules.pro());
    // pior caso: R1 ganha a A1, os campeões de A2, A3 e A4 entram nas sementes 1–4 e os outros 3 que alcançam X levam os wild cards
    assertThat(c.get(x.getId())).isNull();
    assertThat(news(w, LeagueRules.pro(), x)).doesNotContain("CLINCH_PLAYOFFS");
  }

  @Test
  void vaga_garantida_quando_mesmo_contando_os_campeoes_das_divisoes_fracas_sobra_lugar() {
    World w = new World(18, 16);
    Division a1 = w.division("A1");
    Club x = w.team("X", a1, 11, 5);
    w.team("R1", a1, 10, 6); w.team("R2", a1, 4, 12); w.team("R3", a1, 3, 13);
    // 5 alcançam X (R1 e 4 em A2/A3) + o campeão da A4 fraca = 6 à frente no pior caso, com 7 vagas
    others(w, new int[] {12, 10, 9, 2}, new int[] {13, 10, 2, 2}, new int[] {8, 7, 6, 5});
    assertThat(clinches(w, LeagueRules.pro()).get(x.getId())).isEqualTo(Clinch.PLAYOFFS);
    assertThat(news(w, LeagueRules.pro(), x)).containsExactly("CLINCH_PLAYOFFS");
  }

  @Test
  void college_com_duas_folgas_nao_garante_folga_quando_so_o_rival_da_divisao_alcanca() {
    World w = new World(14, 11);
    Division b1 = w.division("B1"), b2 = w.division("B2");
    Club x = w.team("X", b1, 10, 1);
    w.team("R", b1, 9, 2); w.team("B1-2", b1, 5, 6); w.team("B1-3", b1, 4, 7);
    w.team("B2-0", b2, 8, 3); w.team("B2-1", b2, 7, 4); w.team("B2-2", b2, 6, 5); w.team("B2-3", b2, 2, 9);
    // R pode ganhar a B1 e o campeão da B2 leva a outra folga: as duas folgas são dos campeões de divisão
    Map<UUID, Clinch> c = clinches(w, LeagueRules.college());
    assertThat(c.get(x.getId())).isEqualTo(Clinch.PLAYOFFS);
    assertThat(news(w, LeagueRules.college(), x)).doesNotContain("CLINCH_BYE").contains("CLINCH_PLAYOFFS");
  }

  @Test
  void college_garante_folga_com_a_divisao_garantida_e_menos_rivais_que_as_folgas() {
    World w = new World(14, 11);
    Division b1 = w.division("B1"), b2 = w.division("B2");
    Club x = w.team("X", b1, 10, 1);
    w.team("R", b1, 8, 3); w.team("B1-2", b1, 5, 6); w.team("B1-3", b1, 4, 7);
    w.team("B2-0", b2, 9, 2); w.team("B2-1", b2, 7, 4); w.team("B2-2", b2, 6, 5); w.team("B2-3", b2, 2, 9);
    assertThat(clinches(w, LeagueRules.college()).get(x.getId())).isEqualTo(Clinch.BYE);
    assertThat(news(w, LeagueRules.college(), x)).containsExactly("CLINCH_BYE");
  }
}
