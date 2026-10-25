package br.com.velion.scoresaf.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.shared.ContractType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cap hit com bônus rateado e dead money (docs/01 §3.2). */
class PlayerContractTest {
  private static PlayerContract c(long bonus, long guaranteed, Long... salaries) {
    return PlayerContract.builder().startSeason(2026).baseSalaryByYear(List.of(salaries)).signingBonus(bonus).guaranteed(guaranteed)
        .type(ContractType.VETERAN).build();
  }

  @Test
  void bonus_e_rateado_e_cap_hit_soma_salario_e_parcela() {
    PlayerContract k = c(10_000_000L, 0, 1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L);
    assertThat(k.years()).isEqualTo(4);
    assertThat(k.endSeason()).isEqualTo(2029);
    assertThat(k.capHitByYear()).containsExactly(3_500_000L, 4_500_000L, 5_500_000L, 6_500_000L);
    assertThat(k.capHitByYear().stream().mapToLong(Long::longValue).sum()).isEqualTo(k.totalValue());
  }

  @Test
  void rateio_para_no_maximo_de_anos_e_o_resto_da_divisao_entra_no_primeiro() {
    PlayerContract k = c(700L, 0, 100L, 100L, 100L, 100L, 100L, 100L, 100L);
    assertThat(k.capHitByYear(5)).containsExactly(240L, 240L, 240L, 240L, 240L, 100L, 100L);
    PlayerContract odd = c(10L, 0, 0L, 0L, 0L);
    assertThat(odd.capHitByYear()).containsExactly(4L, 3L, 3L);
  }

  @Test
  void dead_money_e_bonus_nao_lancado_mais_garantia_nao_paga() {
    PlayerContract k = c(8_000_000L, 3_000_000L, 2_000_000L, 2_000_000L, 2_000_000L, 2_000_000L);
    // corte antes do 1º ano: 8 M de bônus + 3 M garantidos
    assertThat(k.deadMoneyIfCut(0)).isEqualTo(11_000_000L);
    // antes do 2º ano: 6 M de bônus + 1 M de garantia restante (2 M já pagos)
    assertThat(k.deadMoneyIfCut(1)).isEqualTo(7_000_000L);
    // antes do 3º ano: 4 M de bônus, garantia esgotada
    assertThat(k.deadMoneyIfCut(2)).isEqualTo(4_000_000L);
    assertThat(k.deadMoneyIfCut(4)).isZero();
    assertThat(k.yearIndexOf(2027)).isEqualTo(1);
  }

  @Test
  void sem_anos_nao_ha_cap_hit() {
    PlayerContract empty = PlayerContract.builder().startSeason(2026).build();
    assertThat(empty.years()).isZero();
    assertThat(empty.capHit(0)).isZero();
    assertThat(empty.deadMoneyIfCut(0)).isZero();
  }

  /** Q-24: regra do 1º de junho. Antes, tudo na temporada do corte; a partir dele, a parcela do ano agora e o resto na seguinte. */
  @Test
  void dead_money_pela_regra_do_1o_de_junho() {
    PlayerContract k = c(8_000_000L, 3_000_000L, 2_000_000L, 2_000_000L, 2_000_000L, 2_000_000L);
    // corte no 1º ano antes de 1º de junho: os 11 M (8 M de bônus + 3 M garantidos) na temporada
    assertThat(k.deadMoneySplit(0, 5, false)).isEqualTo(new PlayerContract.DeadMoney(11_000_000L, 0));
    // a partir de 1º de junho: parcela do ano (2 M de bônus + 2 M de garantia do ano) agora; 6 M de bônus + 1 M de garantia depois
    assertThat(k.deadMoneySplit(0, 5, true)).isEqualTo(new PlayerContract.DeadMoney(4_000_000L, 7_000_000L));
    // 2º ano: 2 M de bônus + 1 M de garantia restante agora; 4 M de bônus depois
    assertThat(k.deadMoneySplit(1, 5, true)).isEqualTo(new PlayerContract.DeadMoney(3_000_000L, 4_000_000L));
    assertThat(k.deadMoneySplit(1, 5, false).total()).isEqualTo(k.deadMoneyIfCut(1));
    // último ano: nada a jogar para a temporada seguinte
    assertThat(k.deadMoneySplit(3, 5, true)).isEqualTo(new PlayerContract.DeadMoney(2_000_000L, 0));
    assertThat(k.deadMoneySplit(4, 5, true)).isEqualTo(PlayerContract.DeadMoney.NONE);
    assertThat(k.guaranteeFor(0)).isEqualTo(2_000_000L);
    assertThat(k.guaranteeFor(1)).isEqualTo(1_000_000L);
    assertThat(k.guaranteeFor(2)).isZero();
  }
}
