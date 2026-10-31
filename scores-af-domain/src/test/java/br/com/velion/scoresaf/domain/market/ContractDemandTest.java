package br.com.velion.scoresaf.domain.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pedido e aceite determinísticos (D-132): valor de mercado por overall (base), idade, moral e papel; a contraproposta é aceita sempre e
 * qualquer proposta que vale menos é recusada; garantia compensa salário; o desconto da free agency baixa o pedido.
 */
class ContractDemandTest {
  private static final long BASE = 10_000_000_00L;

  private static ContractDemand.Profile prof(int age, int overall, Integer morale, String role) {
    return new ContractDemand.Profile(BASE, age, overall, morale, role);
  }

  @Test
  void idade_moral_e_papel_mudam_o_pedido() {
    assertThat(ContractDemand.askingApy(prof(24, 78, null, null), 0)).isEqualTo(11_000_000_00L);
    assertThat(ContractDemand.askingApy(prof(28, 78, null, null), 0)).isEqualTo(10_000_000_00L);
    assertThat(ContractDemand.askingApy(prof(33, 78, null, null), 0)).isEqualTo(7_000_000_00L);
    assertThat(ContractDemand.askingApy(prof(28, 78, 80, null), 0)).as("moral alta: pede menos para ficar").isEqualTo(9_200_000_00L);
    assertThat(ContractDemand.askingApy(prof(28, 78, 20, null), 0)).as("moral baixa: pede mais").isEqualTo(12_500_000_00L);
    assertThat(ContractDemand.askingApy(prof(28, 78, 50, "RESERVE"), 0)).as("reserva pede mais para ficar no banco").isEqualTo(10_800_000_00L);
    assertThat(ContractDemand.askingApy(prof(28, 78, null, null), 30)).as("dia 3 da free agency").isEqualTo(7_000_000_00L);
    assertThat(ContractDemand.askingApy(new ContractDemand.Profile(1_00L, 28, 50, null, null), 0)).as("nunca abaixo do mínimo").isEqualTo(MarketRules.MIN_SALARY);
  }

  @Test
  void contraproposta_anos_pela_idade_luvas_e_garantias_pelo_overall() {
    ContractOffer young = ContractDemand.ask(prof(24, 82, null, null), 0);
    assertThat(young.years()).isEqualTo(4);
    assertThat(young.signingBonus()).isEqualTo(13_200_000_00L);
    assertThat(young.salaryByYear()).containsOnly(7_700_000_00L).hasSize(4);
    assertThat(young.guaranteed()).isEqualTo(7_700_000_00L);
    ContractOffer veteran = ContractDemand.ask(prof(33, 68, null, null), 0);
    assertThat(veteran.years()).isEqualTo(1);
    assertThat(veteran.signingBonus()).isZero();
    assertThat(veteran.guaranteed()).isZero();
  }

  @Test
  void aceita_a_contraproposta_e_recusa_o_que_vale_menos() {
    ContractDemand.Profile p = prof(27, 76, 55, "STARTER");
    ContractOffer counter = ContractDemand.ask(p, 0);
    assertThat(ContractDemand.evaluate(counter, p, 0).accepted()).isTrue();
    ContractOffer less = new ContractOffer(counter.years(), counter.salaryByYear().stream().map(s -> s - 1_000_00L).toList(), counter.signingBonus(),
        counter.guaranteed());
    ContractDemand.Decision d = ContractDemand.evaluate(less, p, 0);
    assertThat(d.accepted()).isFalse();
    assertThat(d.counter()).isEqualTo(counter);
    assertThat(d.offerScore()).isLessThan(d.askingScore());
  }

  @Test
  void garantia_compensa_salario_menor() {
    ContractDemand.Profile p = prof(28, 72, null, null);
    ContractOffer counter = ContractDemand.ask(p, 0);
    long apy = counter.apy();
    // um ano, 4 % abaixo da média pedida, mas todo garantido: vale mais para o jogador
    long salary = apy * 96 / 100;
    assertThat(ContractDemand.evaluate(new ContractOffer(1, List.of(salary), 0, salary), p, 0).accepted()).isTrue();
    assertThat(ContractDemand.evaluate(new ContractOffer(1, List.of(salary), 0, 0), p, 0).accepted()).isFalse();
  }

  @Test
  void proposta_invalida_aponta_os_campos() {
    assertThat(new ContractOffer(6, List.of(1L), -1, 5L).violations(MarketRules.MIN_SALARY, 255_000_000_00L))
        .contains("years", "salaryByYear", "salaryByYear[0]", "signingBonus", "guaranteed");
    assertThat(new ContractOffer(2, List.of(MarketRules.MIN_SALARY, MarketRules.MIN_SALARY), 0, MarketRules.MIN_SALARY)
        .violations(MarketRules.MIN_SALARY, 255_000_000_00L)).isEmpty();
  }
}
