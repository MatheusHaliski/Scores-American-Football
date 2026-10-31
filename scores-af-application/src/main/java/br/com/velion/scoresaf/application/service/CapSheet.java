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
import br.com.velion.scoresaf.domain.market.FreeAgencyWindow;
import br.com.velion.scoresaf.domain.market.MarketRules;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
import br.com.velion.scoresaf.application.port.out.persistence.FreeAgencyWindowRepository;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Teto salarial (RF49): comprometido = cap hit da temporada de cada jogador da franquia (53, practice squad, waivers rumo ao practice
 * squad e IR) + dead money da temporada. Espaço = teto da liga − comprometido. Vale para qualquer franquia (a do usuário e as da IA,
 * bloco 7a) e para as temporadas seguintes (projeção: os contratos em vigor naquele ano + o dead money que já foi para ele).
 *
 * <p>Dead money da temporada (Q-24, D-79): a parcela da temporada das dispensas da franquia feitas nela + a parte que as dispensas da
 * temporada anterior jogaram para esta (regra do 1º de junho, {@link DeadMoneyRule}, pela data do jogo da carreira: a quinta-feira da
 * semana atual ou, durante a free agency, o dia do mercado a partir de 12 de março, D-136).
 *
 * <p>Teto rígido ({@code LeagueRules.hardCap}, D-130): a operação que aumenta o comprometido de uma temporada para acima do teto é
 * recusada com 422 {@code CAP_EXCEEDED} ({@link #requireRoom}; a dispensa, {@link #requireRoomForRelease}, D-80). Com o teto flexível
 * (COLLEGE), nada é recusado.
 */
@Component
public class CapSheet {
  private final RosterMoveRepository moves;
  private final SeasonRepository seasons;
  private final WeekRepository weeks;
  private final FreeAgencyWindowRepository windows;

  public CapSheet(RosterMoveRepository moves, SeasonRepository seasons, WeekRepository weeks, FreeAgencyWindowRepository windows) {
    this.moves = moves; this.seasons = seasons; this.weeks = weeks; this.windows = windows;
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
    if (season.getPhase() == SeasonPhase.FREE_AGENCY) {
      var w = windows.findBySeasonId(season.getId()).filter(FreeAgencyWindow::isOpen);
      if (w.isPresent()) return MarketRules.FREE_AGENCY_OPENS.atYear(season.getYear()).plusDays(w.get().getDay());
    }
    return weeks.findBySeasonIdAndNumber(season.getId(), season.getCurrentWeek()).map(Week::getStartsOn)
        .orElseGet(() -> weeks.findBySeasonIdOrderByNumberAsc(season.getId()).stream().map(Week::getStartsOn).max(Comparator.naturalOrder())
            .map(d -> d.plusWeeks(1)).orElse(null));
  }

  /** Dead money da temporada atual da franquia do usuário: parcela das dispensas dela + o que a temporada anterior jogou para esta. */
  public long deadMoney(CareerSave save) {
    return save.getClub() == null || save.getSeason() == null ? 0 : deadMoney(save, save.getClub().getId(), save.getSeason().getYear());
  }

  /** Dead money das dispensas desta temporada (franquia do usuário) que já está lançado na temporada seguinte. */
  public long deadMoneyNextSeason(CareerSave save) {
    return save.getClub() == null || save.getSeason() == null ? 0 : deadMoney(save, save.getClub().getId(), save.getSeason().getYear() + 1);
  }

  /**
   * Dead money da franquia {@code clubId} na temporada {@code seasonYear}: na atual, a parcela das dispensas dela + o que a anterior
   * jogou para ela; na seguinte, o que as dispensas da atual jogaram para ela; nas outras, nada (a regra do 1º de junho só empurra um ano).
   */
  public long deadMoney(CareerSave save, UUID clubId, int seasonYear) {
    Season season = save.getSeason();
    if (season == null || clubId == null) return 0;
    if (seasonYear == season.getYear()) {
      long current = releases(save, season, clubId).stream().mapToLong(RosterMove::getDeadMoney).sum();
      long carried = seasons.findByCareerIdOrderByNumberAsc(save.getId()).stream().filter(s -> s.getNumber() == season.getNumber() - 1).findFirst()
          .map(prev -> releases(save, prev, clubId).stream().mapToLong(RosterMove::getDeadMoneyNextSeason).sum()).orElse(0L);
      return current + carried;
    }
    if (seasonYear == season.getYear() + 1) return releases(save, season, clubId).stream().mapToLong(RosterMove::getDeadMoneyNextSeason).sum();
    return 0;
  }

  /** Dispensas da franquia na temporada (com o dead money de cada uma). */
  public List<RosterMove> releases(CareerSave save, Season season, UUID clubId) {
    return moves.findByCareerIdAndSeasonIdOrderByCreatedAtAsc(save.getId(), season.getId()).stream()
        .filter(m -> m.getKind() == RosterMoveKind.RELEASE && m.getClub() != null && m.getClub().getId().equals(clubId)).toList();
  }

  /** {@code roster}: jogadores da franquia do usuário (todas as situações com clube). */
  public CareerDtos.CapDto of(CareerSave save, List<Player> roster) {
    return of(save, save.getClub() == null ? null : save.getClub().getId(), roster);
  }

  /** Teto da temporada atual da franquia {@code clubId}; {@code roster}: os jogadores dela (todas as situações com clube). */
  public CareerDtos.CapDto of(CareerSave save, UUID clubId, List<Player> roster) {
    int year = CareerSupport.seasonYear(save);
    long dead = deadMoney(save, clubId, year);
    long capValue = salaryCap(save);
    long used = committed(save, clubId, roster, year);
    return new CareerDtos.CapDto(capValue, used, dead, capValue - used, deadMoney(save, clubId, year + 1));
  }

  public static long salaryCap(CareerSave save) { return save.getLeague() == null ? 0 : save.getLeague().getSalaryCap(); }

  /** Comprometido da franquia na temporada {@code seasonYear}: cap hits dos contratos em vigor nela + dead money dela. */
  public long committed(CareerSave save, UUID clubId, List<Player> roster, int seasonYear) {
    LeagueRules rules = CareerSupport.rules(save);
    long hits = roster.stream().filter(p -> p.getRosterStatus().requiresClub()).mapToLong(p -> capHit(p, seasonYear, rules)).sum();
    return hits + deadMoney(save, clubId, seasonYear);
  }

  /**
   * Teto rígido (D-130): recusa com 422 {@code CAP_EXCEEDED} a operação que aumenta o comprometido de alguma temporada
   * ({@code deltaBySeason}: ano → variação do comprometido) para acima do teto. Variação negativa ou nula nunca é recusada, nem com a
   * folha já acima do teto. {@code details} no formato da D-80 ({@code salaryCap}, {@code capUsed} depois da operação e {@code excess})
   * mais a temporada ({@code seasonYear}).
   */
  public void requireRoom(CareerSave save, UUID clubId, List<Player> roster, Map<Integer, Long> deltaBySeason) {
    if (!CareerSupport.rules(save).isHardCap()) return;
    long capValue = salaryCap(save);
    for (Map.Entry<Integer, Long> e : new java.util.TreeMap<>(deltaBySeason).entrySet()) {
      if (e.getValue() <= 0) continue;
      long after = committed(save, clubId, roster, e.getKey()) + e.getValue();
      if (after > capValue) throw capExceeded(capValue, after, e.getKey());
    }
  }

  /** Cabe {@code extra} no teto da temporada {@code seasonYear} (para a IA escolher sem exceção). */
  public static boolean fits(long committed, long extra, long salaryCap) { return committed + extra <= salaryCap; }

  static BusinessRuleException capExceeded(long salaryCap, long after, int seasonYear) {
    long excess = after - salaryCap;
    return (BusinessRuleException) new BusinessRuleException("CAP_EXCEEDED", "A operação estoura o teto salarial da temporada " + seasonYear + " em "
        + excess + " centavos", reais(excess), reais(salaryCap), reais(after), seasonYear)
        .withDetail("salaryCap", salaryCap).withDetail("capUsed", after).withDetail("excess", excess).withDetail("seasonYear", seasonYear);
  }

  /**
   * Recusa a dispensa (422 {@code CAP_EXCEEDED}) quando o dead money que ela gera na temporada atual estoura o teto: o comprometido
   * depois da dispensa ({@code antes − cap hit liberado + dead money da temporada}) passa do teto e é maior que antes. A dispensa
   * que não aumenta o comprometido nunca é recusada, nem com a folha já acima do teto (é o caminho para voltar a caber nele).
   * {@code details}: {@code salaryCap}, {@code capUsed} (comprometido depois da dispensa) e {@code excess} (centavos de S$).
   */
  public static void requireRoomForRelease(CareerDtos.CapDto before, long capHitRemoved, long deadMoneyCurrentSeason, int seasonYear, boolean hardCap) {
    long after = before.capUsed() - capHitRemoved + deadMoneyCurrentSeason;
    if (!hardCap || after <= before.capUsed() || after <= before.salaryCap()) return;
    throw capExceeded(before.salaryCap(), after, seasonYear);
  }

  /** Centavos → S$ para a mensagem ({@code {0,number,#,##0.00}} formata no idioma do usuário). */
  private static BigDecimal reais(long cents) { return BigDecimal.valueOf(cents, 2); }
}
