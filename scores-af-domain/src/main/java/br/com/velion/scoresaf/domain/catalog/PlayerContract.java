package br.com.velion.scoresaf.domain.catalog;

import br.com.velion.scoresaf.domain.common.converters.LongListConverter;
import br.com.velion.scoresaf.domain.shared.ContractType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contrato do jogador, embutido em {@code Player} (docs/01 §3.2; regras completas de teto no bloco 7). Valores em centavos de S$.
 *
 * <ul>
 *   <li>{@code baseSalaryByYear}: salário-base de cada ano, a partir de {@code startSeason}; o número de anos é o tamanho da lista.</li>
 *   <li>{@code signingBonus}: pago na assinatura e rateado no teto em partes iguais por {@code min(anos, rateio máximo)} anos
 *   (o resto da divisão entra no primeiro ano, para a soma fechar).</li>
 *   <li>{@code guaranteed}: parte garantida dos salários-base, consumida do primeiro ano em diante.</li>
 *   <li>cap hit do ano = salário-base + parcela do bônus; dead money se cortado antes do ano {@code y} = parcelas do bônus ainda não
 *   lançadas + garantia ainda não paga.</li>
 *   <li>{@code prorationByYear} (bloco 7a, D-131): parcelas do bônus explícitas por ano. Vazia, vale o rateio igual de
 *   {@code signingBonus}; preenchida (renovação, reestruturação e franchise tag, que somam bônus novos ao que já estava rateado), é ela
 *   que vale, e {@code signingBonus} guarda a soma.</li>
 * </ul>
 */
@Embeddable
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PlayerContract {
  /** Rateio padrão do bônus (o da liga fica em {@code LeagueRules.bonusProrationMaxYears}). */
  public static final int DEFAULT_PRORATION_MAX_YEARS = 5;

  @Column(name = "contract_start_season") private int startSeason;

  @Convert(converter = LongListConverter.class)
  @Column(name = "contract_base_salary_by_year", length = 255)
  @Builder.Default private List<Long> baseSalaryByYear = new ArrayList<>();

  /** Parcelas do bônus por ano, explícitas (vazia = rateio igual de {@link #signingBonus}; D-131). */
  @Convert(converter = LongListConverter.class)
  @Column(name = "contract_proration_by_year", length = 255)
  @Builder.Default private List<Long> prorationByYear = new ArrayList<>();

  @Column(name = "contract_signing_bonus") private long signingBonus;
  @Column(name = "contract_guaranteed") private long guaranteed;
  @Column(name = "contract_franchise_tag") private boolean franchiseTag;

  @Enumerated(EnumType.STRING)
  @Column(name = "contract_type", columnDefinition = "VARCHAR(32)")
  private ContractType type;

  public int years() { return baseSalaryByYear == null ? 0 : baseSalaryByYear.size(); }

  /** Última temporada coberta ({@code startSeason - 1} sem anos). */
  public int endSeason() { return startSeason + years() - 1; }

  public long baseSalary(int yearIndex) { return yearIndex < 0 || yearIndex >= years() ? 0 : baseSalaryByYear.get(yearIndex); }

  /** Parcela do bônus lançada no teto no ano {@code yearIndex} (0 = primeiro ano). */
  public long prorationFor(int yearIndex, int prorationMaxYears) {
    if (prorationByYear != null && !prorationByYear.isEmpty()) {
      return yearIndex < 0 || yearIndex >= prorationByYear.size() || yearIndex >= years() ? 0 : prorationByYear.get(yearIndex);
    }
    int spread = Math.min(years(), Math.max(1, prorationMaxYears));
    if (years() == 0 || yearIndex < 0 || yearIndex >= spread) return 0;
    long part = signingBonus / spread;
    return yearIndex == 0 ? part + signingBonus % spread : part;
  }

  public long capHit(int yearIndex, int prorationMaxYears) { return baseSalary(yearIndex) + prorationFor(yearIndex, prorationMaxYears); }

  public long capHit(int yearIndex) { return capHit(yearIndex, DEFAULT_PRORATION_MAX_YEARS); }

  public List<Long> capHitByYear(int prorationMaxYears) {
    List<Long> out = new ArrayList<>();
    for (int y = 0; y < years(); y++) out.add(capHit(y, prorationMaxYears));
    return out;
  }

  public List<Long> capHitByYear() { return capHitByYear(DEFAULT_PRORATION_MAX_YEARS); }

  /** Dead money se o jogador for cortado antes do ano {@code yearIndex}: bônus ainda não lançado + garantia ainda não paga. */
  public long deadMoneyIfCut(int yearIndex, int prorationMaxYears) {
    if (yearIndex >= years()) return 0;
    int from = Math.max(0, yearIndex);
    long bonusLeft = 0;
    for (int y = from; y < years(); y++) bonusLeft += prorationFor(y, prorationMaxYears);
    long paid = 0;
    for (int y = 0; y < from; y++) paid += baseSalary(y);
    long remainingSalary = 0;
    for (int y = from; y < years(); y++) remainingSalary += baseSalary(y);
    long guaranteeLeft = Math.min(Math.max(0, guaranteed - paid), remainingSalary);
    return bonusLeft + guaranteeLeft;
  }

  public long deadMoneyIfCut(int yearIndex) { return deadMoneyIfCut(yearIndex, DEFAULT_PRORATION_MAX_YEARS); }

  /** Parte garantida do salário-base do ano {@code yearIndex} (a garantia é consumida do primeiro ano em diante, D-27). */
  public long guaranteeFor(int yearIndex) {
    if (yearIndex < 0 || yearIndex >= years()) return 0;
    long before = 0;
    for (int y = 0; y < yearIndex; y++) before += baseSalary(y);
    return Math.min(baseSalary(yearIndex), Math.max(0, guaranteed - before));
  }

  /**
   * Dead money do corte no ano {@code yearIndex} dividido entre a temporada do corte e a seguinte (regra do 1º de junho, Q-24, D-79):
   * antes de 1º de junho, tudo (bônus ainda não lançado dos anos que faltam + garantias que faltam) entra na temporada do corte; a
   * partir de 1º de junho, só a parcela do ano (bônus rateado do ano + garantia do ano) entra nela e o resto vai para a seguinte. A
   * soma das duas é sempre {@link #deadMoneyIfCut(int, int)}.
   */
  public DeadMoney deadMoneySplit(int yearIndex, int prorationMaxYears, boolean fromJuneFirst) {
    long total = deadMoneyIfCut(yearIndex, prorationMaxYears);
    if (!fromJuneFirst || total == 0) return new DeadMoney(total, 0);
    int y = Math.max(0, yearIndex);
    long current = Math.min(total, prorationFor(y, prorationMaxYears) + guaranteeFor(y));
    return new DeadMoney(current, total - current);
  }

  /** Dead money de um corte: o que entra no teto da temporada do corte e o que vai para a temporada seguinte (centavos de S$). */
  public record DeadMoney(long currentSeason, long nextSeason) {
    public static final DeadMoney NONE = new DeadMoney(0, 0);
    public long total() { return currentSeason + nextSeason; }
  }

  /** Parcelas do bônus de todos os anos (a lista explícita ou o rateio igual), para somar bônus novos (D-131). */
  public List<Long> prorations(int prorationMaxYears) {
    List<Long> out = new ArrayList<>();
    for (int y = 0; y < years(); y++) out.add(prorationFor(y, prorationMaxYears));
    return out;
  }

  /** Valor total (salários + bônus). */
  public long totalValue() {
    long t = signingBonus;
    for (int y = 0; y < years(); y++) t += baseSalary(y);
    return t;
  }

  /** Índice do ano do contrato para a temporada {@code season} (negativo ou ≥ anos = fora do contrato). */
  public int yearIndexOf(int season) { return season - startSeason; }
}
