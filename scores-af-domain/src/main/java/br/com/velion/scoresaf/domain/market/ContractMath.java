package br.com.velion.scoresaf.domain.market;

import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.shared.ContractType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * As contas dos contratos do bloco 7a (RF49; D-131, D-133 a D-135), puras e em centavos de S$:
 *
 * <ul>
 *   <li>{@link #sign}: contrato novo de um agente livre, com o bônus rateado igual (D-27).</li>
 *   <li>{@link #extend}: renovação no último ano do contrato. O ano atual fica como está (salário, parcela do bônus e garantia dele) e
 *   os anos da proposta vêm depois; as luvas novas são rateadas a partir do ano atual (temporada em andamento) ou do seguinte (temporada
 *   encerrada), por até {@code bonusProrationMaxYears} anos. O salário do ano atual passa a contar como garantido (a garantia é
 *   consumida do primeiro ano em diante, D-27: só assim a garantia nova cai inteira nos anos novos).</li>
 *   <li>{@link #restructure}: converte parte do salário do ano em luvas rateadas pelos anos que restam (até o máximo do rateio). O cap
 *   hit do ano cai, os seguintes sobem e o dead money futuro aumenta; a garantia do ano convertida vira luvas.</li>
 *   <li>{@link #franchiseTag}: um ano a mais, pelo valor da tag, todo garantido.</li>
 * </ul>
 * O que muda no teto é {@link #capDelta}: a diferença do cap hit de cada temporada entre o contrato de antes e o de depois.
 */
public final class ContractMath {
  private ContractMath() {}

  /** Tipo do contrato pelo salário médio: mínimo até 10 % acima do salário mínimo, senão de veterano. */
  public static ContractType typeFor(ContractOffer o) {
    return o.totalSalary() / Math.max(1, o.years()) <= MarketRules.MIN_SALARY * 11 / 10 && o.signingBonus() == 0 ? ContractType.MINIMUM : ContractType.VETERAN;
  }

  /** Contrato novo a partir de {@code startSeason}. */
  public static PlayerContract sign(int startSeason, ContractOffer o) {
    return PlayerContract.builder().startSeason(startSeason).baseSalaryByYear(new ArrayList<>(o.salaryByYear())).signingBonus(o.signingBonus())
        .guaranteed(o.guaranteed()).type(typeFor(o)).build();
  }

  /** Contrato de practice squad de 1 ano a partir de {@code startSeason}. */
  public static PlayerContract practice(int startSeason) {
    return PlayerContract.builder().startSeason(startSeason).baseSalaryByYear(new ArrayList<>(List.of(MarketRules.PRACTICE_SALARY)))
        .type(ContractType.PRACTICE).build();
  }

  /**
   * Renovação de {@code old} no ano {@code yearIndex} (o último dele): o novo contrato começa no ano atual, com o salário, a parcela do
   * bônus e a garantia dele, e soma os anos da proposta.
   */
  public static PlayerContract extend(PlayerContract old, int yearIndex, boolean seasonCompleted, ContractOffer o, int prorationMaxYears) {
    int max = Math.max(1, prorationMaxYears);
    List<Long> salaries = new ArrayList<>();
    salaries.add(old.baseSalary(yearIndex));
    salaries.addAll(o.salaryByYear());
    List<Long> prorations = new ArrayList<>();
    for (int i = 0; i < salaries.size(); i++) prorations.add(0L);
    prorations.set(0, old.prorationFor(yearIndex, max));
    int from = seasonCompleted ? 1 : 0;
    spread(prorations, from, Math.min(salaries.size() - from, max), o.signingBonus());
    long bonus = prorations.stream().mapToLong(Long::longValue).sum();
    return PlayerContract.builder().startSeason(old.getStartSeason() + yearIndex).baseSalaryByYear(salaries).prorationByYear(prorations)
        .signingBonus(bonus).guaranteed(old.baseSalary(yearIndex) + o.guaranteed()).type(typeFor(o)).build();
  }

  /** Máximo convertível na reestruturação do ano {@code yearIndex} (o salário do ano menos o mínimo; 0 sem 2 anos pela frente). */
  public static long maxRestructure(PlayerContract c, int yearIndex) {
    if (c == null || yearIndex < 0 || c.years() - yearIndex < 2) return 0;
    return Math.max(0, c.baseSalary(yearIndex) - MarketRules.MIN_SALARY);
  }

  /** Reestruturação: {@code amount} do salário do ano vira luvas rateadas do ano atual em diante. */
  public static PlayerContract restructure(PlayerContract c, int yearIndex, long amount, int prorationMaxYears) {
    if (amount <= 0 || amount > maxRestructure(c, yearIndex)) throw new IllegalArgumentException("valor fora da faixa da reestruturação");
    int max = Math.max(1, prorationMaxYears);
    List<Long> prorations = c.prorations(max);
    spread(prorations, yearIndex, Math.min(c.years() - yearIndex, max), amount);
    List<Long> salaries = new ArrayList<>(c.getBaseSalaryByYear());
    long guaranteedYear = c.guaranteeFor(yearIndex);
    salaries.set(yearIndex, salaries.get(yearIndex) - amount);
    return c.toBuilder().baseSalaryByYear(salaries).prorationByYear(prorations).signingBonus(c.getSigningBonus() + amount)
        .guaranteed(c.getGuaranteed() - Math.min(amount, guaranteedYear)).build();
  }

  /** Valor da franchise tag: a média dos maiores cap hits da posição, nunca abaixo de 120 % do cap hit atual do jogador. */
  public static long tagValue(List<Long> capHitsAtPosition, long playerCapHit) {
    List<Long> top = capHitsAtPosition.stream().sorted(Comparator.reverseOrder()).limit(MarketRules.FRANCHISE_TAG_TOP).toList();
    long avg = top.isEmpty() ? 0 : top.stream().mapToLong(Long::longValue).sum() / top.size();
    long floor = playerCapHit * MarketRules.FRANCHISE_TAG_MIN_RAISE_PCT / 100;
    return Math.max(MarketRules.MIN_SALARY, ContractDemand.round(Math.max(avg, floor)));
  }

  /** Franchise tag no último ano ({@code yearIndex}): um ano a mais, pelo valor da tag, todo garantido. */
  public static PlayerContract franchiseTag(PlayerContract old, int yearIndex, long tagValue, int prorationMaxYears) {
    List<Long> salaries = new ArrayList<>(List.of(old.baseSalary(yearIndex), tagValue));
    List<Long> prorations = new ArrayList<>(List.of(old.prorationFor(yearIndex, prorationMaxYears), 0L));
    return PlayerContract.builder().startSeason(old.getStartSeason() + yearIndex).baseSalaryByYear(salaries).prorationByYear(prorations)
        .signingBonus(prorations.get(0)).guaranteed(old.baseSalary(yearIndex) + tagValue).franchiseTag(true).type(ContractType.TAG).build();
  }

  /** Diferença do cap hit por temporada (de {@code fromSeason} a {@code toSeason}) entre dois contratos; só as que mudam. */
  public static Map<Integer, Long> capDelta(PlayerContract before, PlayerContract after, int fromSeason, int toSeason, int prorationMaxYears) {
    Map<Integer, Long> out = new LinkedHashMap<>();
    for (int s = fromSeason; s <= toSeason; s++) {
      long b = before == null ? 0 : before.capHit(before.yearIndexOf(s), prorationMaxYears);
      long a = after == null ? 0 : after.capHit(after.yearIndexOf(s), prorationMaxYears);
      if (a != b) out.put(s, a - b);
    }
    return out;
  }

  /** Soma {@code amount} em {@code years} parcelas iguais a partir de {@code from} (o resto da divisão na primeira). */
  static void spread(List<Long> prorations, int from, int years, long amount) {
    if (amount <= 0 || years <= 0) return;
    long part = amount / years;
    for (int i = 0; i < years; i++) prorations.set(from + i, prorations.get(from + i) + part + (i == 0 ? amount % years : 0));
  }
}
