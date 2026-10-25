package br.com.velion.scoresaf.domain.engine.morale;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.engine.morale.PlayerMoraleEngine.Profile;
import br.com.velion.scoresaf.domain.engine.morale.PlayerMoraleEngine.Role;
import br.com.velion.scoresaf.domain.engine.morale.PlayerMoraleEngine.State;
import br.com.velion.scoresaf.domain.engine.morale.PlayerMoraleEngine.Week;
import br.com.velion.scoresaf.domain.match.ChartSlot;
import br.com.velion.scoresaf.domain.shared.Position;
import org.junit.jupiter.api.Test;

/**
 * Moral do RF41 (D-102): vitória sobe e derrota desce; titular sem snaps cai; snaps acima do esperado sobem; último ano de contrato
 * e papel de reserva puxam o alvo para baixo; sequência do time pesa; teto por semana; pedido de troca depois de 3 semanas abaixo de 35.
 */
class PlayerMoraleEngineTest {
  private static final Profile STARTER = new Profile(Role.STARTER, 0.95, 27, false);

  private static Week game(boolean won, int snaps, int unit, int streak) {
    return new Week(true, true, snaps, unit, won, !won, won ? 10 : -10, streak);
  }

  @Test
  void vitoria_sobe_e_derrota_desce() {
    State s = PlayerMoraleEngine.start(60);
    State win = PlayerMoraleEngine.week(s, STARTER, game(true, 60, 62, 1));
    State loss = PlayerMoraleEngine.week(s, STARTER, game(false, 60, 62, -1));
    assertThat(win.morale()).isGreaterThan(s.morale());
    assertThat(loss.morale()).isLessThan(win.morale());
    assertThat(win.factors().get(PlayerMoraleEngine.F_RESULT)).isPositive();
    assertThat(loss.factors().get(PlayerMoraleEngine.F_RESULT)).isNegative();
  }

  @Test
  void titular_sem_snaps_perde_e_snaps_acima_do_esperado_ajudam() {
    State s = PlayerMoraleEngine.start(60);
    State benched = PlayerMoraleEngine.week(s, STARTER, game(true, 0, 62, 1));
    assertThat(benched.factors().get(PlayerMoraleEngine.F_SNAPS)).isEqualTo(-3.0);
    Profile backup = new Profile(Role.BACKUP, 0.10, 27, false);
    State bonus = PlayerMoraleEngine.week(s, backup, game(true, 40, 62, 1));
    assertThat(bonus.factors().get(PlayerMoraleEngine.F_SNAPS)).isPositive();
    // especialista e reserva fora do depth chart não contam snaps
    assertThat(PlayerMoraleEngine.week(s, new Profile(Role.SPECIALIST, 0, 27, false), game(true, 0, 62, 1)).factors().get(PlayerMoraleEngine.F_SNAPS)).isZero();
    // indisponível não é cobrado pelos snaps
    assertThat(PlayerMoraleEngine.week(s, STARTER, new Week(true, false, 0, 62, true, false, 7, 1)).factors().get(PlayerMoraleEngine.F_SNAPS)).isZero();
  }

  @Test
  void ultimo_ano_de_contrato_e_papel_de_reserva_puxam_o_alvo_para_baixo() {
    State s = PlayerMoraleEngine.start(50);
    State calm = s, contract = s, reserve = s;
    for (int i = 0; i < 12; i++) {
      calm = PlayerMoraleEngine.week(calm, STARTER, Week.bye());
      contract = PlayerMoraleEngine.week(contract, new Profile(Role.STARTER, 0.95, 27, true), Week.bye());
      reserve = PlayerMoraleEngine.week(reserve, new Profile(Role.RESERVE, 0, 27, false), Week.bye());
    }
    assertThat(calm.rounded()).isGreaterThan(contract.rounded());
    assertThat(contract.rounded()).isGreaterThan(reserve.rounded());
    assertThat(reserve.rounded()).isLessThan(50);
  }

  @Test
  void sequencia_do_time_pesa_e_a_semana_tem_teto() {
    State s = PlayerMoraleEngine.start(60);
    State streak = PlayerMoraleEngine.week(s, STARTER, game(true, 60, 62, 5));
    assertThat(streak.factors().get(PlayerMoraleEngine.F_STREAK)).isEqualTo(1.5);
    State crash = PlayerMoraleEngine.week(PlayerMoraleEngine.start(95), new Profile(Role.RESERVE, 0, 33, true), new Week(true, true, 0, 60, false, true, -40, -8));
    assertThat(crash.delta()).isEqualTo(PlayerMoraleEngine.MAX_LOSS_PER_WEEK);
    assertThat(crash.factors().get(PlayerMoraleEngine.F_WEEK_LIMIT)).isPositive();
  }

  @Test
  void pedido_de_troca_depois_de_tres_semanas_abaixo_de_35() {
    Profile unhappy = new Profile(Role.RESERVE, 0, 30, true);
    State s = PlayerMoraleEngine.start(36);
    Week loss = new Week(true, true, 0, 60, false, true, -21, -4);
    s = PlayerMoraleEngine.week(s, unhappy, loss);
    assertThat(s.rounded()).isLessThan(35);
    assertThat(s.tradeRequest()).isFalse();
    s = PlayerMoraleEngine.week(s, unhappy, loss);
    s = PlayerMoraleEngine.week(s, unhappy, loss);
    assertThat(s.lowWeeks()).isEqualTo(3);
    assertThat(s.tradeRequest()).isTrue();
  }

  @Test
  void fatia_esperada_e_papel_pela_vaga() {
    assertThat(PlayerMoraleEngine.expectedShare(ChartSlot.QB, 1)).isEqualTo(0.95);
    assertThat(PlayerMoraleEngine.expectedShare(ChartSlot.LT, 1)).isEqualTo(0.95);
    assertThat(PlayerMoraleEngine.expectedShare(ChartSlot.DE, 2)).isEqualTo(0.70);
    assertThat(PlayerMoraleEngine.expectedShare(ChartSlot.RB, 1)).isEqualTo(0.55);
    assertThat(PlayerMoraleEngine.expectedShare(ChartSlot.WR, 3)).isEqualTo(0.45);
    assertThat(PlayerMoraleEngine.expectedShare(ChartSlot.K, 1)).isZero();
    assertThat(PlayerMoraleEngine.role(Position.QB, 0.95, true)).isEqualTo(Role.STARTER);
    assertThat(PlayerMoraleEngine.role(Position.WR, 0.45, true)).isEqualTo(Role.ROTATION);
    assertThat(PlayerMoraleEngine.role(Position.QB, 0.02, true)).isEqualTo(Role.BACKUP);
    assertThat(PlayerMoraleEngine.role(Position.CB, 0, false)).isEqualTo(Role.RESERVE);
    assertThat(PlayerMoraleEngine.role(Position.K, 0, true)).isEqualTo(Role.SPECIALIST);
    assertThat(PlayerMoraleEngine.label(72)).isEqualTo("HIGH");
    assertThat(PlayerMoraleEngine.label(29)).isEqualTo("CRITICAL");
    assertThat(PlayerMoraleEngine.performanceModifier(100)).isEqualTo(0.05);
  }
}
