package br.com.velion.scoresaf.domain.engine.sim;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.engine.model.PlaybackPlan;
import org.junit.jupiter.api.Test;

/** RNF2 (base): o ritmo de exibição cabe no tempo por quarto que o manager escolheu, entre 400 ms e 6 s por jogada. */
class MatchTimeBudgetPolicyTest {

  @Test
  void ritmo_por_jogada_pelo_tempo_por_quarto_e_jogadas_estimadas() {
    assertThat(MatchTimeBudgetPolicy.plan(1, 150)).isEqualTo(new PlaybackPlan(240, 150, 1600, false));
    assertThat(MatchTimeBudgetPolicy.plan(2, 160)).isEqualTo(new PlaybackPlan(480, 160, 3000, false));
    // teto de 6 s: com 15 minutos por quarto o jogo termina antes do orçamento (Q-15)
    assertThat(MatchTimeBudgetPolicy.plan(15, 150).msPerPlay()).isEqualTo(6000);
    assertThat(MatchTimeBudgetPolicy.plan(15, 150).timeBudgetSeconds()).isEqualTo(3600);
    for (int q = 1; q <= 15; q++) {
      PlaybackPlan p = MatchTimeBudgetPolicy.plan(q, 150);
      assertThat(p.msPerPlay()).isBetween(400, 6000);
      assertThat((long) p.msPerPlay() * p.playsEstimate()).as("cabe no orçamento").isLessThanOrEqualTo(p.timeBudgetSeconds() * 1000L);
    }
  }

  @Test
  void instantaneo_padrao_e_limites() {
    assertThat(MatchTimeBudgetPolicy.plan(0, 150)).isEqualTo(new PlaybackPlan(0, 150, 0, true));
    assertThat(MatchTimeBudgetPolicy.plan(3, 0).playsEstimate()).isEqualTo(150);
    assertThat(MatchTimeBudgetPolicy.budgetSeconds(99)).isEqualTo(4 * 3 * 60);
    assertThat(MatchTimeBudgetPolicy.msPerPlay(10_000, 1000)).isEqualTo(400);
    assertThat(MatchTimeBudgetPolicy.msPerPlay(0, 10)).isEqualTo(400);
    // depois de uma pausa de decisão, o ritmo das jogadas restantes acelera
    assertThat(MatchTimeBudgetPolicy.msPerPlay(120_000, 100)).isEqualTo(1200);
  }
}
