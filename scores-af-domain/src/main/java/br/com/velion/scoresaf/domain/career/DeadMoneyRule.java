package br.com.velion.scoresaf.domain.career;

import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import java.time.LocalDate;
import java.time.MonthDay;

/**
 * Regra do 1º de junho para o dead money de uma dispensa (Q-24, D-79), pela data do jogo da carreira (a quinta-feira da semana atual
 * do calendário). Dispensa antes de 1º de junho do ano da temporada: todo o dead money restante entra na temporada atual; a partir de
 * 1º de junho: só a parcela do ano entra nela e o resto vai para a temporada seguinte ({@link PlayerContract#deadMoneySplit}). A data
 * é fixa (resposta do PO), como a elegibilidade do practice squad em {@link RosterRules}; não é um campo de {@code LeagueRules}.
 */
public final class DeadMoneyRule {
  public static final MonthDay JUNE_FIRST = MonthDay.of(6, 1);

  private DeadMoneyRule() {}

  /** {@code true} se a data do jogo é 1º de junho do ano da temporada ou depois (sem data: depois, como na temporada regular). */
  public static boolean fromJuneFirst(LocalDate gameDate, int seasonYear) {
    return gameDate == null || !gameDate.isBefore(JUNE_FIRST.atYear(seasonYear));
  }

  /** Dead money do corte de {@code contract} na temporada {@code seasonYear}, na data {@code gameDate}. */
  public static PlayerContract.DeadMoney ifCut(PlayerContract contract, int seasonYear, LocalDate gameDate, int prorationMaxYears) {
    if (contract == null) return PlayerContract.DeadMoney.NONE;
    return contract.deadMoneySplit(Math.max(0, contract.yearIndexOf(seasonYear)), prorationMaxYears, fromJuneFirst(gameDate, seasonYear));
  }
}
