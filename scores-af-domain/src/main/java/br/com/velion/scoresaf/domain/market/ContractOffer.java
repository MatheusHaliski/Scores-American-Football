package br.com.velion.scoresaf.domain.market;

import java.util.ArrayList;
import java.util.List;

/**
 * Proposta de contrato (renovação ou agente livre, bloco 7a): anos, salário-base de cada ano, luvas (bônus de assinatura) e garantias
 * (a parte garantida dos salários, consumida do primeiro ano em diante, D-27). Centavos de S$.
 *
 * <p>Valor da proposta para o jogador ({@link #score()}): média anual do dinheiro (salários + luvas) mais {@value MarketRules#GUARANTEE_WEIGHT_PCT}
 * % da média anual do dinheiro garantido (luvas + garantias). Assim, mais garantia compensa salário menor.
 */
public record ContractOffer(int years, List<Long> salaryByYear, long signingBonus, long guaranteed) {

  public ContractOffer {
    salaryByYear = salaryByYear == null ? List.of() : List.copyOf(salaryByYear);
  }

  public long totalSalary() { return salaryByYear.stream().mapToLong(Long::longValue).sum(); }

  public long totalValue() { return totalSalary() + signingBonus; }

  /** Média anual (salários + luvas). */
  public long apy() { return years <= 0 ? 0 : totalValue() / years; }

  /** Valor da proposta para o jogador. */
  public long score() {
    if (years <= 0) return 0;
    return apy() + (signingBonus + guaranteed) * MarketRules.GUARANTEE_WEIGHT_PCT / 100 / years;
  }

  /**
   * Campos inválidos pelo nome (vazio = proposta válida): anos de 1 a {@value MarketRules#MAX_CONTRACT_YEARS}, um salário por ano, cada
   * um entre o mínimo da liga e o teto, luvas e garantias não negativas e garantia até a soma dos salários.
   */
  public List<String> violations(long minSalary, long salaryCap) {
    List<String> v = new ArrayList<>();
    if (years < 1 || years > MarketRules.MAX_CONTRACT_YEARS) v.add("years");
    if (salaryByYear.size() != Math.max(0, years)) v.add("salaryByYear");
    for (int i = 0; i < salaryByYear.size(); i++) {
      Long s = salaryByYear.get(i);
      if (s == null || s < minSalary || s > salaryCap) v.add("salaryByYear[" + i + "]");
    }
    if (signingBonus < 0 || signingBonus > salaryCap) v.add("signingBonus");
    if (guaranteed < 0 || guaranteed > totalSalary()) v.add("guaranteed");
    return v;
  }
}
