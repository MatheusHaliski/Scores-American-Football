package br.com.velion.scoresaf.domain.career;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.shared.ContractType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regra do 1º de junho (Q-24) pela data do jogo da carreira, no ano da temporada. */
class DeadMoneyRuleTest {
  private static final PlayerContract K = PlayerContract.builder().startSeason(2026).baseSalaryByYear(List.of(1_000L, 1_000L, 1_000L))
      .signingBonus(3_000L).guaranteed(0).type(ContractType.VETERAN).build();

  @Test
  void a_data_de_corte_e_1o_de_junho_do_ano_da_temporada() {
    assertThat(DeadMoneyRule.fromJuneFirst(LocalDate.of(2026, 5, 31), 2026)).isFalse();
    assertThat(DeadMoneyRule.fromJuneFirst(LocalDate.of(2026, 6, 1), 2026)).isTrue();
    assertThat(DeadMoneyRule.fromJuneFirst(LocalDate.of(2026, 9, 10), 2026)).isTrue();
    assertThat(DeadMoneyRule.fromJuneFirst(LocalDate.of(2027, 1, 7), 2026)).as("janeiro da temporada regular que começou em 2026").isTrue();
    assertThat(DeadMoneyRule.fromJuneFirst(LocalDate.of(2027, 3, 1), 2027)).as("offseason da temporada 2027").isFalse();
  }

  @Test
  void antes_tudo_na_temporada_depois_a_parcela_do_ano() {
    assertThat(DeadMoneyRule.ifCut(K, 2026, LocalDate.of(2026, 5, 31), 5)).isEqualTo(new PlayerContract.DeadMoney(3_000L, 0));
    assertThat(DeadMoneyRule.ifCut(K, 2026, LocalDate.of(2026, 6, 1), 5)).isEqualTo(new PlayerContract.DeadMoney(1_000L, 2_000L));
    assertThat(DeadMoneyRule.ifCut(K, 2027, LocalDate.of(2027, 9, 9), 5)).isEqualTo(new PlayerContract.DeadMoney(1_000L, 1_000L));
    assertThat(DeadMoneyRule.ifCut(null, 2026, LocalDate.of(2026, 9, 9), 5)).isEqualTo(PlayerContract.DeadMoney.NONE);
  }
}
