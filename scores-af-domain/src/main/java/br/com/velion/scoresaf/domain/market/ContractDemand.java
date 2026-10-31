package br.com.velion.scoresaf.domain.market;

import java.util.ArrayList;
import java.util.List;

/**
 * O que o jogador pede e se aceita uma proposta (bloco 7a, RF49 renovação e RF65 free agency; D-132). Regra pura e determinística, sem
 * sorteio:
 *
 * <ul>
 *   <li><b>Valor de mercado</b> por ano: o valor da posição pelo overall ({@code baseValue}, a fórmula salarial do mundo semente) ×
 *   idade (até 25: 110 %; 26–29: 100 %; 30–31: 85 %; 32–33: 70 %; 34+: 55 %).</li>
 *   <li><b>Pedido</b> = valor de mercado × moral (RF41: 75+ pede 8 % menos para ficar; 60–74, 3 % menos; 40–59, igual; 25–39, 10 %
 *   mais; abaixo de 25, 25 % mais) × papel (titular e especialista 100 %, rodízio 103 %, reserva 108 %) × (100 − desconto) %, nunca
 *   abaixo do mínimo. Moral e papel só contam na renovação (o jogador já é da franquia); o desconto só na free agency (o mercado cai a
 *   cada dia).</li>
 *   <li><b>Contraproposta</b>: anos pela idade (até 26: 4; 27–29: 3; 30–31: 2; 32+: 1), luvas de 30 % do total (overall 80+) ou 15 %
 *   (70–79), salário igual em todos os anos e garantia do 1º ano inteiro (80+) ou de metade dele (75–79).</li>
 *   <li><b>Aceite</b>: a proposta vale ao menos a contraproposta ({@link ContractOffer#score()}).</li>
 * </ul>
 */
public final class ContractDemand {
  private ContractDemand() {}

  /**
   * Quem pede: {@code baseValue} é o valor anual da posição pelo overall (centavos); {@code morale} e {@code role} são nulos fora da
   * renovação.
   */
  public record Profile(long baseValue, int age, int overall, Integer morale, String role) {}

  /** Resultado da avaliação: aceita ou não, a contraproposta e os dois valores comparados. */
  public record Decision(boolean accepted, ContractOffer counter, long offerScore, long askingScore) {}

  public static int ageFactorPct(int age) {
    if (age <= 25) return 110;
    if (age <= 29) return 100;
    if (age <= 31) return 85;
    if (age <= 33) return 70;
    return 55;
  }

  public static int moraleFactorPct(Integer morale) {
    if (morale == null) return 100;
    if (morale >= 75) return 92;
    if (morale >= 60) return 97;
    if (morale >= 40) return 100;
    if (morale >= 25) return 110;
    return 125;
  }

  public static int roleFactorPct(String role) {
    if (role == null) return 100;
    return switch (role) {
      case "ROTATION" -> 103;
      case "RESERVE" -> 108;
      default -> 100;
    };
  }

  public static int preferredYears(int age) {
    if (age <= 26) return 4;
    if (age <= 29) return 3;
    if (age <= 31) return 2;
    return 1;
  }

  /** Valor de mercado por ano (sem moral, papel nem desconto). */
  public static long marketValue(Profile p) {
    return Math.max(MarketRules.MIN_SALARY, round(p.baseValue() * ageFactorPct(p.age()) / 100));
  }

  /** Pedido anual com o desconto do mercado. */
  public static long askingApy(Profile p, int discountPct) {
    long v = p.baseValue() * ageFactorPct(p.age()) / 100;
    v = v * moraleFactorPct(p.morale()) / 100;
    v = v * roleFactorPct(p.role()) / 100;
    v = v * (100 - Math.max(0, Math.min(90, discountPct))) / 100;
    return Math.max(MarketRules.MIN_SALARY, round(v));
  }

  /** A contraproposta do jogador (o contrato que ele aceita). */
  public static ContractOffer ask(Profile p, int discountPct) {
    long apy = askingApy(p, discountPct);
    int years = preferredYears(p.age());
    long total = apy * years;
    int bonusPct = p.overall() >= 80 ? 30 : p.overall() >= 70 ? 15 : 0;
    long bonus = round(total * bonusPct / 100);
    long salary = Math.max(MarketRules.MIN_SALARY, floor((total - bonus) / years));
    List<Long> salaries = new ArrayList<>();
    for (int i = 0; i < years; i++) salaries.add(salary);
    long guaranteed = p.overall() >= 80 ? salary : p.overall() >= 75 ? floor(salary / 2) : 0;
    return new ContractOffer(years, salaries, bonus, guaranteed);
  }

  /** Avalia a proposta contra o pedido. */
  public static Decision evaluate(ContractOffer offer, Profile p, int discountPct) {
    ContractOffer counter = ask(p, discountPct);
    long offered = offer.score(), asking = counter.score();
    return new Decision(offered >= asking, counter, offered, asking);
  }

  static long round(long cents) { return Math.round(cents / (double) MarketRules.ROUNDING) * MarketRules.ROUNDING; }

  static long floor(long cents) { return cents / MarketRules.ROUNDING * MarketRules.ROUNDING; }
}
