package br.com.velion.scoresaf.domain.engine.sim;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.engine.model.DepthChart;
import br.com.velion.scoresaf.domain.engine.model.DepthSlot;
import br.com.velion.scoresaf.domain.engine.model.PlayerSnapshot;
import br.com.velion.scoresaf.domain.engine.model.TeamSnapshot;
import br.com.velion.scoresaf.domain.shared.Position;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Depth chart automático pelo overall da posição; o explícito (RF60, bloco 6) vale vaga a vaga. */
class DepthChartTest {
  private final TeamSnapshot team = TestTeams.team("Lobos", 70, 4321);

  @Test
  void titular_de_cada_posicao_e_o_melhor_overall_na_posicao() {
    DepthChart chart = DepthChart.derive(team.players());
    for (DepthSlot slot : DepthSlot.values()) {
      if (slot.position() == null) continue;
      Position pos = slot.position();
      PlayerSnapshot best = team.players().stream().filter(p -> p.position() == pos).max(Comparator.comparingInt(p -> p.overallAt(pos))).orElseThrow();
      assertThat(chart.get(slot).get(0)).as(slot.name()).isEqualTo(best.id());
      assertThat(chart.get(slot)).doesNotHaveDuplicates();
    }
    assertThat(chart.get(DepthSlot.KR)).isNotEmpty();
    assertThat(chart.get(DepthSlot.PR)).isNotEmpty();
    assertThat(chart.get(DepthSlot.H).get(0)).isEqualTo(chart.get(DepthSlot.P).get(0));
  }

  @Test
  void depth_chart_explicito_vale_e_completa_com_o_automatico() {
    List<UUID> qbs = DepthChart.derive(team.players()).get(DepthSlot.QB);
    UUID backup = qbs.get(1);
    DepthChart explicit = new DepthChart(Map.of(DepthSlot.QB, List.of(backup, UUID.randomUUID())));
    DepthChart resolved = team.withDepthChart(explicit).resolvedDepthChart();
    assertThat(resolved.get(DepthSlot.QB)).containsExactly(backup);
    assertThat(resolved.get(DepthSlot.RB)).isEqualTo(DepthChart.derive(team.players()).get(DepthSlot.RB));
  }

  @Test
  void motor_escala_o_qb_do_depth_chart_explicito() {
    UUID backup = DepthChart.derive(team.players()).get(DepthSlot.QB).get(1);
    TeamSnapshot custom = team.withDepthChart(new DepthChart(Map.of(DepthSlot.QB, List.of(backup))));
    var r = new DefaultMatchSimulator().simulate(TestTeams.setup(custom, TestTeams.team("Ursos", 70, 99), br.com.velion.scoresaf.domain.engine.model.RulesSnapshot.pro(), 3));
    assertThat(r.homePlayers()).filteredOn(p -> p.passing() != null && p.passing().attempts() > 5).extracting(p -> p.playerId()).containsExactly(backup);
  }
}
