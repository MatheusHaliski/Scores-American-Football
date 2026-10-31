package br.com.velion.scoresaf.domain.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.shared.ContractType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Renovação, reestruturação, franchise tag e a diferença no teto (D-131, D-133 a D-135). */
class ContractMathTest {
  private static final long M = 1_000_000_00L;

  /** 3 anos (2026–2028) de 10 mi, 9 mi de luvas (3 por ano) e 10 mi garantidos (o 1º ano). */
  private static PlayerContract three() {
    return PlayerContract.builder().startSeason(2026).baseSalaryByYear(new ArrayList<>(List.of(10 * M, 10 * M, 10 * M))).signingBonus(9 * M)
        .guaranteed(10 * M).type(ContractType.VETERAN).build();
  }

  @Test
  void reestruturar_baixa_o_cap_hit_do_ano_e_sobe_os_seguintes_e_o_dead_money() {
    PlayerContract before = three();
    PlayerContract after = ContractMath.restructure(before, 0, 6 * M, 5);
    assertThat(after.capHitByYear(5)).containsExactly(4 * M + 3 * M + 2 * M, 10 * M + 3 * M + 2 * M, 10 * M + 3 * M + 2 * M);
    assertThat(after.totalValue()).as("o dinheiro é o mesmo").isEqualTo(before.totalValue());
    assertThat(after.deadMoneyIfCut(1, 5)).as("dead money se cortado no 2º ano sobe as luvas novas").isEqualTo(before.deadMoneyIfCut(1, 5) + 4 * M);
    assertThat(after.guaranteeFor(0)).as("a garantia do ano convertida virou luvas").isEqualTo(4 * M);
    assertThat(ContractMath.capDelta(before, after, 2026, 2028, 5)).containsExactly(Map.entry(2026, -4 * M), Map.entry(2027, 2 * M), Map.entry(2028, 2 * M));
    assertThat(ContractMath.maxRestructure(before, 0)).isEqualTo(10 * M - MarketRules.MIN_SALARY);
    assertThat(ContractMath.maxRestructure(before, 2)).as("último ano: nada a ratear").isZero();
    assertThatThrownBy(() -> ContractMath.restructure(before, 0, 10 * M, 5)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renovar_mantem_o_ano_atual_e_rateia_as_luvas_novas() {
    PlayerContract old = three();
    ContractOffer offer = new ContractOffer(2, List.of(12 * M, 12 * M), 6 * M, 12 * M);
    // temporada em andamento (2028, último ano): luvas novas rateadas em 3 anos a partir de 2028
    PlayerContract during = ContractMath.extend(old, 2, false, offer, 5);
    assertThat(during.getStartSeason()).isEqualTo(2028);
    assertThat(during.getBaseSalaryByYear()).containsExactly(10 * M, 12 * M, 12 * M);
    assertThat(during.getProrationByYear()).containsExactly(3 * M + 2 * M, 2 * M, 2 * M);
    assertThat(during.getSigningBonus()).isEqualTo(9 * M);
    assertThat(during.endSeason()).isEqualTo(2030);
    // temporada encerrada: as luvas novas só a partir de 2029, o cap hit de 2028 não muda
    PlayerContract after = ContractMath.extend(old, 2, true, offer, 5);
    assertThat(after.capHit(0, 5)).isEqualTo(old.capHit(2, 5));
    assertThat(after.getProrationByYear()).containsExactly(3 * M, 3 * M, 3 * M);
    assertThat(ContractMath.capDelta(old, after, 2028, 2030, 5)).containsExactly(Map.entry(2029, 15 * M), Map.entry(2030, 15 * M));
    assertThat(after.getGuaranteed()).as("o salário do ano atual + a garantia nova").isEqualTo(22 * M);
    assertThat(after.guaranteeFor(1)).as("a garantia nova cai inteira no 1º ano novo").isEqualTo(12 * M);
  }

  @Test
  void franchise_tag_um_ano_pela_media_dos_maiores_todo_garantido() {
    long tag = ContractMath.tagValue(List.of(30 * M, 25 * M, 20 * M, 15 * M, 10 * M, 5 * M), 8 * M);
    assertThat(tag).as("média dos 5 maiores").isEqualTo(20 * M);
    assertThat(ContractMath.tagValue(List.of(10 * M), 20 * M)).as("mínimo de 120 % do atual").isEqualTo(24 * M);
    PlayerContract tagged = ContractMath.franchiseTag(three(), 2, tag, 5);
    assertThat(tagged.isFranchiseTag()).isTrue();
    assertThat(tagged.getType()).isEqualTo(ContractType.TAG);
    assertThat(tagged.endSeason()).isEqualTo(2029);
    assertThat(tagged.capHit(1, 5)).isEqualTo(20 * M);
    assertThat(tagged.guaranteeFor(1)).isEqualTo(20 * M);
    assertThat(ContractMath.capDelta(three(), tagged, 2028, 2029, 5)).containsExactly(Map.entry(2029, 20 * M));
  }

  @Test
  void contrato_novo_pela_proposta() {
    PlayerContract c = ContractMath.sign(2027, new ContractOffer(2, List.of(5 * M, 5 * M), 2 * M, 5 * M));
    assertThat(c.capHitByYear(5)).containsExactly(6 * M, 6 * M);
    assertThat(c.getType()).isEqualTo(ContractType.VETERAN);
    assertThat(ContractMath.sign(2027, new ContractOffer(1, List.of(MarketRules.MIN_SALARY), 0, 0)).getType()).isEqualTo(ContractType.MINIMUM);
    assertThat(ContractMath.practice(2027).capHit(0, 5)).isEqualTo(MarketRules.PRACTICE_SALARY);
  }
}
