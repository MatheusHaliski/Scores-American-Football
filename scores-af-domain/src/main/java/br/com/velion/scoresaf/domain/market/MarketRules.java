package br.com.velion.scoresaf.domain.market;

import java.time.MonthDay;

/**
 * Números fixos do mercado (bloco 7a, RF49/RF65; D-130). Dinheiro em centavos de S$. Ficam aqui, e não em {@code LeagueRules}, como a
 * data do 1º de junho ({@code DeadMoneyRule}): o editor de ligas ainda não os expõe (Q-52).
 */
public final class MarketRules {
  /** Salário mínimo de um contrato dos 53 (o mesmo do mundo semente). */
  public static final long MIN_SALARY = 900_000_00L;
  /** Salário do practice squad (o mesmo do mundo semente). */
  public static final long PRACTICE_SALARY = 216_000_00L;
  /** Anos de um contrato novo ou de uma renovação. */
  public static final int MAX_CONTRACT_YEARS = 5;
  /** Dias da free agency depois da abertura (dia 0): no último, a janela fecha. */
  public static final int FREE_AGENCY_DAYS = 4;
  /** Desconto do pedido do agente livre por dia de free agency que passou. */
  public static final int DISCOUNT_PER_DAY_PCT = 10;
  /** Desconto de quem sobrou no mercado, fora da janela (temporada, playoffs, entressafra). */
  public static final int LATE_MARKET_DISCOUNT_PCT = 40;
  /** Peso do dinheiro garantido (luvas + garantias) no valor de uma proposta, em %. */
  public static final int GUARANTEE_WEIGHT_PCT = 25;
  /** Franchise tag: média dos N maiores cap hits da posição... */
  public static final int FRANCHISE_TAG_TOP = 5;
  /** ...e nunca menos que este % do cap hit atual do jogador. */
  public static final int FRANCHISE_TAG_MIN_RAISE_PCT = 120;
  /** Contratações de cada franquia da IA por dia de free agency (no fechamento, completa o elenco). */
  public static final int AI_SIGNINGS_PER_DAY = 3;
  /** Data da abertura da free agency (o "novo ano da liga"): a data do jogo da carreira durante a janela, um dia por dia de mercado. */
  public static final MonthDay FREE_AGENCY_OPENS = MonthDay.of(3, 12);
  /** Arredondamento dos valores pedidos (S$ 1.000). */
  public static final long ROUNDING = 1_000_00L;

  private MarketRules() {}
}
