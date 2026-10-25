package br.com.velion.scoresaf.application.seed;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Division;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.Stadium;
import br.com.velion.scoresaf.domain.catalog.StadiumSector;
import br.com.velion.scoresaf.domain.engine.ratings.OverallCalculator;
import br.com.velion.scoresaf.domain.game.Ruleset;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.PositionFamily;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.StadiumSectorType;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Mundo semente fictício (docs/01 §3.2, Q-3, Q-4): 32 franquias em 2 × 4 × 4, 53 + 16 por franquia, determinístico pela semente. */
class DefaultWorldFactoryTest {
  static DefaultWorld world;

  @BeforeAll
  static void build() { world = new DefaultWorldFactory().build(); }

  private static List<Player> squad(Club c) { return world.players().stream().filter(p -> p.getClub() == c).toList(); }

  @Test
  void uma_liga_pro_de_32_franquias_em_2_conferencias_de_4_divisoes_de_4() {
    assertThat(world.leagues()).hasSize(1);
    League l = world.leagues().get(0);
    assertThat(l.getRuleset()).isEqualTo(Ruleset.PRO);
    assertThat(l.getRules().violations()).isEmpty();
    assertThat(l.getTeamCount()).isEqualTo(32);
    assertThat(l.getConferenceCount()).isEqualTo(2);
    assertThat(l.getDivisionsPerConference()).isEqualTo(4);
    assertThat(world.conferences()).hasSize(2);
    assertThat(world.divisions()).hasSize(8);
    assertThat(world.clubs()).hasSize(32);
    Map<Division, Long> perDivision = world.clubs().stream().collect(Collectors.groupingBy(Club::getDivision, Collectors.counting()));
    assertThat(perDivision).hasSize(8).allSatisfy((d, n) -> assertThat(n).isEqualTo(4));
    for (Club c : world.clubs()) assertThat(c.getConference()).isSameAs(c.getDivision().getConference());
    assertThat(world.divisions().stream().collect(Collectors.groupingBy(Division::getConference, Collectors.counting())).values()).containsOnly(4L);
  }

  @Test
  void cada_franquia_tem_53_no_elenco_e_16_no_practice_squad_com_a_distribuicao_de_posicoes() {
    Map<Position, Long> expectedRoster = DefaultWorldFactory.ROSTER.stream().collect(Collectors.groupingBy(Function.identity(), () -> new EnumMap<>(Position.class), Collectors.counting()));
    assertThat(DefaultWorldFactory.ROSTER).hasSize(53);
    assertThat(DefaultWorldFactory.PRACTICE_SQUAD).hasSize(16);
    assertThat(expectedRoster).containsEntry(Position.QB, 3L).containsEntry(Position.WR, 6L).containsEntry(Position.CB, 6L)
        .containsEntry(Position.K, 1L).containsEntry(Position.P, 1L).containsEntry(Position.LS, 1L);
    long ol = DefaultWorldFactory.ROSTER.stream().filter(p -> p.family() == PositionFamily.OFFENSIVE_LINE && p != Position.LS).count();
    assertThat(ol).isEqualTo(9);
    for (Club c : world.clubs()) {
      List<Player> squad = squad(c);
      List<Player> active = squad.stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE).toList();
      assertThat(active).as(c.getName()).hasSize(53);
      assertThat(squad.stream().filter(p -> p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD)).as(c.getName()).hasSize(16);
      assertThat(squad).hasSize(69);
      Map<Position, Long> got = active.stream().collect(Collectors.groupingBy(Player::getPosition, () -> new EnumMap<>(Position.class), Collectors.counting()));
      assertThat(got).as(c.getName()).isEqualTo(expectedRoster);
      assertThat(active.size()).isLessThanOrEqualTo(world.leagues().get(0).getRules().getRosterMax());
    }
    assertThat(world.players()).hasSize(32 * 69);
  }

  @Test
  void nenhum_nome_se_repete_na_franquia_nem_no_mundo_e_numeros_de_camisa_sao_unicos() {
    for (Club c : world.clubs()) {
      List<Player> squad = squad(c);
      assertThat(squad.stream().map(Player::getName).distinct().count()).as(c.getName()).isEqualTo(squad.size());
      assertThat(squad.stream().map(Player::getJerseyNumber).distinct().count()).as(c.getName()).isEqualTo(squad.size());
      assertThat(squad).allSatisfy(p -> assertThat(p.getJerseyNumber()).isBetween(0, 99));
    }
    Set<String> all = new HashSet<>();
    world.players().forEach(p -> all.add(p.getName()));
    world.managers().forEach(m -> all.add(m.getName()));
    assertThat(all).hasSize(world.players().size() + world.managers().size());
    assertThat(world.clubs().stream().map(Club::getName).distinct()).hasSize(32);
    assertThat(world.clubs().stream().map(Club::getShortName).distinct()).hasSize(32);
  }

  @Test
  void mesma_semente_mesmo_mundo_e_semente_diferente_outro_mundo() {
    DefaultWorld again = new DefaultWorldFactory().build(DefaultWorldFactory.DEFAULT_SEED);
    assertThat(again.world().getId()).isEqualTo(world.world().getId());
    assertThat(again.players()).hasSameSizeAs(world.players());
    for (int i = 0; i < world.players().size(); i++) {
      Player a = world.players().get(i), b = again.players().get(i);
      assertThat(b.getId()).isEqualTo(a.getId());
      assertThat(b.getName()).isEqualTo(a.getName());
      assertThat(b.getAttributes().asMap()).isEqualTo(a.getAttributes().asMap());
      assertThat(b.getContract().getBaseSalaryByYear()).isEqualTo(a.getContract().getBaseSalaryByYear());
      assertThat(b.getHeightCm()).isEqualTo(a.getHeightCm());
    }
    assertThat(again.stadiums().stream().map(Stadium::getCapacity).toList()).isEqualTo(world.stadiums().stream().map(Stadium::getCapacity).toList());
    DefaultWorld other = new DefaultWorldFactory().build(42L);
    assertThat(other.world().getId()).isNotEqualTo(world.world().getId());
    assertThat(other.players().get(0).getName() + other.players().get(1).getName()).isNotEqualTo(world.players().get(0).getName() + world.players().get(1).getName());
    assertThat(other.world().getSeed()).isEqualTo(42L);
  }

  @Test
  void altura_peso_e_atributos_coerentes_com_a_posicao() {
    for (Player p : world.players()) {
      assertThat(p.getPosition().acceptsHeight(p.getHeightCm())).as("%s %s %d cm", p.getName(), p.getPosition(), p.getHeightCm()).isTrue();
      assertThat(p.getPosition().acceptsWeight(p.getWeightKg())).as("%s %s %d kg", p.getName(), p.getPosition(), p.getWeightKg()).isTrue();
      assertThat(p.getOverall()).isEqualTo(OverallCalculator.overall(p.getAttributes(), p.getPosition())).isBetween(40, 99);
      assertThat(p.getPotential()).isGreaterThanOrEqualTo(p.getOverall());
      // o melhor overall do jogador nunca está numa família de posição diferente da dele
      for (Position other : Position.values()) {
        if (other.family() == p.getPosition().family()) continue;
        assertThat(OverallCalculator.overall(p.getAttributes(), other)).as("%s (%s) jogando de %s", p.getName(), p.getPosition(), other)
            .isLessThan(p.getOverall());
      }
    }
    // titulares são mais fortes que o practice squad, em média
    double active = world.players().stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE).mapToInt(Player::getOverall).average().orElse(0);
    double practice = world.players().stream().filter(p -> p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD).mapToInt(Player::getOverall).average().orElse(0);
    assertThat(active).isGreaterThan(practice + 5);
  }

  @Test
  void estadio_de_60_a_80_mil_com_os_5_setores_e_cores_validas() {
    assertThat(world.stadiums()).hasSize(32);
    for (Stadium s : world.stadiums()) {
      assertThat(Stadium.acceptsCapacity(s.getCapacity())).as(s.getName() + " " + s.getCapacity()).isTrue();
      List<StadiumSector> secs = world.sectors().stream().filter(x -> x.getStadium() == s).toList();
      assertThat(secs.stream().map(StadiumSector::getType)).containsExactlyInAnyOrder(StadiumSectorType.values());
      assertThat(secs.stream().mapToInt(StadiumSector::getSeats).sum()).isEqualTo(s.getCapacity());
    }
    for (Club c : world.clubs()) {
      assertThat(c.getStadium()).isNotNull();
      assertThat(c.getManager()).isNotNull();
      assertThat(List.of(c.getPrimaryColor(), c.getSecondaryColor(), c.getAccentColor())).allMatch(x -> x.matches("#[0-9A-F]{6}"));
      assertThat(c.getExpectedWins()).isBetween(3, 13);
    }
    assertThat(world.uniforms()).hasSize(96);
    assertThat(world.helmets()).hasSize(32);
  }

  @Test
  void folha_de_cada_franquia_respeita_o_teto_rigido() {
    League l = world.leagues().get(0);
    int prorate = l.getRules().getBonusProrationMaxYears();
    for (Club c : world.clubs()) {
      long hits = squad(c).stream().mapToLong(p -> p.getContract().capHit(0, prorate)).sum();
      assertThat(hits).as(c.getName()).isLessThanOrEqualTo(l.getSalaryCap()).isGreaterThanOrEqualTo(l.getSalaryFloor());
      assertThat(squad(c)).allSatisfy(p -> {
        assertThat(p.getContract().years()).isBetween(1, 5);
        assertThat(p.getContract().getStartSeason()).isEqualTo(DefaultWorldFactory.BASE_YEAR);
        assertThat(p.getContract().deadMoneyIfCut(0, prorate)).isLessThanOrEqualTo(p.getContract().totalValue());
      });
    }
  }
}
