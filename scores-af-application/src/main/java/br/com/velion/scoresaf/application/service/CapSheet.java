package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.DeadMoneyRule;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Week;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Teto salarial da temporada da carreira, provisório até o CapEngine do bloco 7 (RF49): comprometido = cap hit da temporada de
 * cada jogador da franquia (53, practice squad, waivers rumo ao practice squad e IR; contrato do bloco 4) + dead money da temporada.
 * Espaço = teto da liga − comprometido.
 *
 * <p>Dead money da temporada (Q-24, D-79): a parcela da temporada das dispensas feitas nela + a parte que as dispensas da temporada
 * anterior jogaram para esta (regra do 1º de junho, {@link DeadMoneyRule}, pela data do jogo da carreira: a quinta-feira da semana
 * atual). A dispensa que faz o comprometido passar do teto é recusada com 422 {@code CAP_EXCEEDED} ({@link #requireRoomForRelease};
 * substitui a parte da D-46 que deixava o bloqueio para o bloco 7).
 */
@Component
public class CapSheet {
  private final RosterMoveRepository moves;
  private final SeasonRepository seasons;
  private final WeekRepository weeks;

  public CapSheet(RosterMoveRepository moves, SeasonRepository seasons, WeekRepository weeks) {
    this.moves = moves; this.seasons = seasons; this.weeks = weeks;
  }

  public static long capHit(Player p, int seasonYear, LeagueRules rules) {
    PlayerContract c = p.getContract();
    return c == null ? 0 : c.capHit(c.yearIndexOf(seasonYear), rules.getBonusProrationMaxYears());
  }

  /** Dead money se {@code p} for dispensado na data {@code gameDate} da temporada {@code seasonYear} (regra do 1º de junho). */
  public static PlayerContract.DeadMoney deadMoneyIfCut(Player p, int seasonYear, LocalDate gameDate, LeagueRules rules) {
    return DeadMoneyRule.ifCut(p.getContract(), seasonYear, gameDate, rules.getBonusProrationMaxYears());
  }

  /**
   * Data do jogo da carreira: a quinta-feira da semana atual da temporada; depois da última semana, uma semana depois da última
   * quinta-feira do calendário. {@code null} sem temporada.
   */
  public LocalDate gameDate(CareerSave save) {
    Season season = save.getSeason();
    if (season == null) return null;
    return weeks.findBySeasonIdAndNumber(season.getId(), season.getCurrentWeek()).map(Week::getStartsOn)
        .orElseGet(() -> weeks.findBySeasonIdOrderByNumberAsc(season.getId()).stream().map(Week::getStartsOn).max(Comparator.naturalOrder())
            .map(d -> d.plusWeeks(1)).orElse(null));
  }

  /** Dead money da temporada atual: parcela das dispensas dela + o que a temporada anterior jogou para esta. */
  public long deadMoney(CareerSave save) {
    Season season = save.getSeason();
    if (season == null) return 0;
    long current = releases(save, season).stream().mapToLong(RosterMove::getDeadMoney).sum();
    long carried = seasons.findByCareerIdOrderByNumberAsc(save.getId()).stream().filter(s -> s.getNumber() == season.getNumber() - 1).findFirst()
        .map(prev -> releases(save, prev).stream().mapToLong(RosterMove::getDeadMoneyNextSeason).sum()).orElse(0L);
    return current + carried;
  }

  /** Dead money das dispensas desta temporada que já está lançado na temporada seguinte. */
  public long deadMoneyNextSeason(CareerSave save) {
    Season season = save.getSeason();
    return season == null ? 0 : releases(save, season).stream().mapToLong(RosterMove::getDeadMoneyNextSeason).sum();
  }

  private List<RosterMove> releases(CareerSave save, Season season) {
    return moves.findByCareerIdAndSeasonIdOrderByCreatedAtAsc(save.getId(), season.getId()).stream().filter(m -> m.getKind() == RosterMoveKind.RELEASE)
        .toList();
  }

  /** {@code roster}: jogadores da franquia (todas as situações com clube). */
  public CareerDtos.CapDto of(CareerSave save, List<Player> roster) {
    LeagueRules rules = CareerSupport.rules(save);
    int year = CareerSupport.seasonYear(save);
    long hits = roster.stream().filter(p -> p.getRosterStatus().requiresClub()).mapToLong(p -> capHit(p, year, rules)).sum();
    long dead = deadMoney(save);
    long capValue = save.getLeague() == null ? 0 : save.getLeague().getSalaryCap();
    long used = hits + dead;
    return new CareerDtos.CapDto(capValue, used, dead, capValue - used, deadMoneyNextSeason(save));
  }

  /**
   * Recusa a dispensa (422 {@code CAP_EXCEEDED}) quando o dead money que ela gera na temporada atual estoura o teto: o comprometido
   * depois da dispensa ({@code antes − cap hit liberado + dead money da temporada}) passa do teto e é maior que antes. A dispensa
   * que não aumenta o comprometido nunca é recusada, nem com a folha já acima do teto (é o caminho para voltar a caber nele).
   * {@code details}: {@code salaryCap}, {@code capUsed} (comprometido depois da dispensa) e {@code excess} (centavos de S$).
   */
  public static void requireRoomForRelease(CareerDtos.CapDto before, long capHitRemoved, long deadMoneyCurrentSeason) {
    long after = before.capUsed() - capHitRemoved + deadMoneyCurrentSeason;
    if (after <= before.capUsed() || after <= before.salaryCap()) return;
    long excess = after - before.salaryCap();
    throw (BusinessRuleException) new BusinessRuleException("CAP_EXCEEDED", "A dispensa estoura o teto salarial da temporada em " + excess + " centavos",
        reais(excess), reais(before.salaryCap()), reais(after))
        .withDetail("salaryCap", before.salaryCap()).withDetail("capUsed", after).withDetail("excess", excess);
  }

  /** Centavos → S$ para a mensagem ({@code {0,number,#,##0.00}} formata no idioma do usuário). */
  private static BigDecimal reais(long cents) { return BigDecimal.valueOf(cents, 2); }
}
