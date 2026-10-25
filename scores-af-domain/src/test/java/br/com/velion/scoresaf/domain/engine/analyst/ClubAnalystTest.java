package br.com.velion.scoresaf.domain.engine.analyst;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.engine.analyst.ClubAnalyst.Candidate;
import br.com.velion.scoresaf.domain.engine.analyst.ClubAnalyst.Plan;
import br.com.velion.scoresaf.domain.engine.analyst.ClubAnalyst.Report;
import br.com.velion.scoresaf.domain.engine.analyst.ClubAnalyst.SlotInput;
import br.com.velion.scoresaf.domain.engine.analyst.ClubAnalyst.Units;
import br.com.velion.scoresaf.domain.engine.analyst.ClubAnalyst.Upcoming;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Analista por regras (D-104): pontos fortes e fracos contra a média da liga, chance de vitória pela força e pelo mando, trocas no
 * depth chart quando um disponível supera o titular em 3+, e ajustes do plano de jogo (passe × corrida, foco defensivo e
 * agressividade) só quando diferem do plano salvo.
 */
class ClubAnalystTest {
  private static final Units AVG = new Units(70, 70, 70, 70, 70, 70, 70, 70);
  private static final Plan PLAN = new Plan(55, "BALANCED", "NORMAL");

  @Test
  void fortes_e_fracos_contra_a_media_da_liga() {
    Units ours = new Units(80, 70, 70, 74, 62, 70, 69, 70);
    Report r = ClubAnalyst.analyze(ours, List.of(AVG, AVG), List.of(), List.of(), PLAN);
    assertThat(r.strengths()).extracting(ClubAnalyst.Finding::unit).containsExactly("QB", "RECEIVERS");
    assertThat(r.weaknesses()).extracting(ClubAnalyst.Finding::unit).containsExactly("DEFENSIVE_LINE");
    assertThat(r.summary()).isEqualTo("SEASON_OVERVIEW");
    assertThat(r.summaryParams()).containsEntry("bestUnit", "QB").containsEntry("worstUnit", "DEFENSIVE_LINE");
  }

  @Test
  void chance_de_vitoria_pela_forca_e_pelo_mando() {
    Units strong = new Units(80, 80, 80, 80, 80, 80, 80, 80);
    Report home = ClubAnalyst.analyze(strong, List.of(AVG), List.of(new Upcoming("f1", "opp", 3, true, false, AVG)), List.of(), PLAN);
    Report neutral = ClubAnalyst.analyze(AVG, List.of(AVG), List.of(new Upcoming("f2", "opp", 3, true, true, AVG)), List.of(), PLAN);
    Report away = ClubAnalyst.analyze(AVG, List.of(AVG), List.of(new Upcoming("f3", "opp", 3, false, false, AVG)), List.of(), PLAN);
    assertThat(home.upcoming().get(0).winProbability()).isGreaterThan(0.85);
    assertThat(home.upcoming().get(0).verdict()).isEqualTo("FAVORITE");
    assertThat(home.summary()).isEqualTo("NEXT_GAME_FAVORITE");
    assertThat(neutral.upcoming().get(0).winProbability()).isEqualTo(0.5);
    assertThat(away.upcoming().get(0).winProbability()).isLessThan(0.5);
  }

  @Test
  void depth_chart_recomenda_quem_supera_o_titular_em_3_ou_mais() {
    SlotInput qb = new SlotInput("QB", 1, List.of(new Candidate("QB", 1, "q1", "Titular", 70, false), new Candidate("QB", 2, "q2", "Reserva", 76, false)));
    SlotInput wr = new SlotInput("WR", 3, List.of(new Candidate("WR", 1, "w1", "W1", 80, false), new Candidate("WR", 2, "w2", "W2", 78, false),
        new Candidate("WR", 3, "w3", "W3", 70, false), new Candidate("WR", 0, "w4", "W4", 72, false), new Candidate("WR", 0, "rb", "RB", 90, true)));
    Report r = ClubAnalyst.analyze(AVG, List.of(AVG), List.of(), List.of(qb, wr), PLAN);
    assertThat(r.depthChart()).singleElement().satisfies(d -> {
      assertThat(d.slot()).isEqualTo("QB");
      assertThat(d.inPlayerId()).isEqualTo("q2");
      assertThat(d.outPlayerId()).isEqualTo("q1");
      assertThat(d.gain()).isEqualTo(6);
    });
  }

  @Test
  void plano_de_jogo_segue_a_vantagem_e_so_recomenda_o_que_muda() {
    Units passing = new Units(85, 65, 60, 85, 70, 70, 70, 70);
    Units weakSecondary = new Units(70, 70, 70, 70, 75, 75, 60, 70);
    Report r = ClubAnalyst.analyze(passing, List.of(AVG), List.of(new Upcoming("f", "opp", 2, true, false, weakSecondary)), List.of(), PLAN);
    assertThat(r.gamePlan()).anySatisfy(a -> {
      assertThat(a.field()).isEqualTo("passRatio");
      assertThat(Integer.parseInt(a.recommended())).isEqualTo(70);
      assertThat(a.reason()).isEqualTo("PASS_EDGE");
    });
    Report same = ClubAnalyst.analyze(AVG, List.of(AVG), List.of(new Upcoming("f", "opp", 2, true, true, AVG)), List.of(), new Plan(50, "BALANCED", "NORMAL"));
    assertThat(same.gamePlan()).isEmpty();
  }
}
