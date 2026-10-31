package br.com.velion.scoresaf.application.usecase.rf49;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.common.exception.ScoresException;
import br.com.velion.scoresaf.application.dto.MarketDtos;
import br.com.velion.scoresaf.application.dto.RosterDtos;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ContractEventRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.service.CapSheet;
import br.com.velion.scoresaf.application.service.market.ContractActions;
import br.com.velion.scoresaf.application.service.market.MarketAssembler;
import br.com.velion.scoresaf.application.service.market.MarketService;
import br.com.velion.scoresaf.application.usecase.rf04.RosterUseCases;
import br.com.velion.scoresaf.application.usecase.rf05.EditorAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterRules;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.market.ContractDemand;
import br.com.velion.scoresaf.domain.market.ContractEvent;
import br.com.velion.scoresaf.domain.market.ContractMath;
import br.com.velion.scoresaf.domain.market.ContractOffer;
import br.com.velion.scoresaf.domain.market.MarketRules;
import br.com.velion.scoresaf.domain.season.Season;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF49 — teto salarial e contratos da franquia do usuário (bloco 7a): a folha ({@code GET /cap}: teto, comprometido, espaço, dead
 * money da temporada e da seguinte, projeção das próximas 3 temporadas e cada contrato ano a ano), a renovação no último ano
 * ({@code POST /players/{pid}/renew}: o jogador aceita ou recusa pela regra de {@link ContractDemand}; a recusa é 422
 * {@code OFFER_REFUSED} com a contraproposta em {@code details}), a reestruturação ({@code /restructure}: salário do ano vira luvas
 * rateadas), a franchise tag ({@code /franchise-tag}, uma por temporada na entressafra) e o trade block ({@code /trade-block} e
 * {@code /trade-block/remove}: só grava e mostra; as trocas são do bloco 7b). Teto rígido: toda operação que aumenta o comprometido de
 * alguma temporada para acima do teto responde 422 {@code CAP_EXCEEDED} ({@link CapSheet#requireRoom}). As escritas travam a carreira
 * antes de tudo (D-91).
 */
@Service
public class ContractUseCases {
  private final CareerSupport support; private final CareerSaveRepository saves; private final PlayerRepository players; private final CapSheet cap;
  private final MarketService market; private final ContractActions actions; private final ContractEventRepository events; private final AuditService audit;
  private final RosterUseCases rosterUseCases;

  public ContractUseCases(CareerSupport support, CareerSaveRepository saves, PlayerRepository players, CapSheet cap, MarketService market,
                          ContractActions actions, ContractEventRepository events, AuditService audit, RosterUseCases rosterUseCases) {
    this.support = support; this.saves = saves; this.players = players; this.cap = cap; this.market = market; this.actions = actions;
    this.events = events; this.audit = audit; this.rosterUseCases = rosterUseCases;
  }

  /** Proposta do usuário (renovação ou agente livre). */
  public record OfferCommand(Integer years, List<Long> salaryByYear, Long signingBonus, Long guaranteed) {
    public ContractOffer toOffer() {
      return new ContractOffer(years == null ? 0 : years, salaryByYear == null ? List.of() : salaryByYear, signingBonus == null ? 0 : signingBonus,
          guaranteed == null ? 0 : guaranteed);
    }
  }

  // ================================================================ folha

  @Transactional(readOnly = true)
  public MarketDtos.CapSheetDto cap(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    Club club = support.userClub(save);
    LeagueRules rules = CareerSupport.rules(save);
    int year = CareerSupport.seasonYear(save);
    int prorate = rules.getBonusProrationMaxYears();
    List<Player> roster = clubPlayers(club);
    var now = cap.of(save, club.getId(), roster);
    List<MarketDtos.CapYearDto> projections = new ArrayList<>();
    for (int y = year + 1; y <= year + 3; y++) {
      int season = y;
      long committed = cap.committed(save, club.getId(), roster, y);
      int contracts = (int) roster.stream().filter(p -> CapSheet.capHit(p, season, rules) > 0).count();
      projections.add(new MarketDtos.CapYearDto(y, committed, cap.deadMoney(save, club.getId(), y), now.salaryCap() - committed, contracts));
    }
    ContractActions.Context ctx = actions.context(save);
    List<MarketDtos.CapContractDto> rows = new ArrayList<>();
    for (Player p : roster) {
      PlayerContract c = p.getContract();
      if (c == null || c.years() == 0) continue;
      List<MarketDtos.ContractYearDto> years = new ArrayList<>();
      for (int i = Math.max(0, c.yearIndexOf(year)); i < c.years(); i++) {
        years.add(new MarketDtos.ContractYearDto(c.getStartSeason() + i, c.baseSalary(i), c.prorationFor(i, prorate), c.guaranteeFor(i), c.capHit(i, prorate),
            c.deadMoneyIfCut(i, prorate)));
      }
      rows.add(new MarketDtos.CapContractDto(p.getId(), p.getName(), p.getPosition().name(), p.getJerseyNumber(), p.getRosterStatus().name(), p.getAge(),
          p.getOverall(), c.getType() == null ? null : c.getType().name(), c.isFranchiseTag(), c.getStartSeason(), c.endSeason(),
          Math.max(0, c.endSeason() - year + 1), CapSheet.capHit(p, year, rules), c.totalValue(), c.getSigningBonus(), c.getGuaranteed(), years,
          actions.actions(ctx, p)));
    }
    rows.sort(Comparator.comparingLong(MarketDtos.CapContractDto::capHit).reversed().thenComparing(MarketDtos.CapContractDto::name));
    List<MarketDtos.DeadMoneyDto> dead = new ArrayList<>();
    Season season = save.getSeason();
    if (season != null) {
      for (RosterMove m : cap.releases(save, season, club.getId())) {
        dead.add(new MarketDtos.DeadMoneyDto(m.getPlayer().getId(), m.getPlayer().getName(), season.getYear(), m.getDeadMoney(), m.getDeadMoneyNextSeason()));
      }
    }
    MarketDtos.FranchiseTagDto tag = franchiseTag(save, club, ctx);
    RosterRules.Counts counts = RosterRules.Counts.of(roster);
    return new MarketDtos.CapSheetDto(club.getId(), club.getName(), year, season == null ? null : season.getPhase().name(), rules.isHardCap(),
        now.salaryCap(), save.getLeague() == null ? 0 : save.getLeague().getSalaryFloor(), now.capUsed(), now.capSpace(), now.deadMoney(),
        now.deadMoneyNextSeason(), counts.roster(), counts.practiceSquad(), projections, rows, dead, tag);
  }

  private MarketDtos.FranchiseTagDto franchiseTag(CareerSave save, Club club, ContractActions.Context ctx) {
    Season season = save.getSeason();
    List<ContractEvent> tags = season == null ? List.of() : events.findBySeasonIdAndClubIdAndKind(season.getId(), club.getId(), ContractEvent.Kind.FRANCHISE_TAG);
    ContractEvent t = tags.isEmpty() ? null : tags.get(0);
    return new MarketDtos.FranchiseTagDto(ctx.offseason(), t != null, t == null ? null : t.getPlayer().getId(), t == null ? null : t.getPlayer().getName(),
        t == null ? null : t.getAmount());
  }

  // ================================================================ renovação

  @Transactional
  public MarketDtos.ContractResultDto renew(UUID careerId, UUID userId, UUID playerId, OfferCommand cmd) {
    saves.findByIdForUpdate(careerId);
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    Player p = support.clubPlayer(save, playerId);
    ContractActions.Context ctx = actions.context(save);
    String blocked = ContractActions.renewBlocked(ctx, p);
    if (blocked != null) {
      ScoresException e = new BusinessRuleException("RENEWAL_NOT_ALLOWED", "Renovação fora da janela: " + blocked, blocked);
      throw e.withDetail("reason", blocked).withDetail("contractEndSeason", p.getContract() == null ? null : p.getContract().endSeason())
          .withDetail("seasonYear", ctx.year());
    }
    ContractOffer offer = validOffer(cmd == null ? null : cmd.toOffer(), save);
    ContractDemand.Decision d = ContractDemand.evaluate(offer, ContractActions.renewalProfile(p, actions.morale(save).get(p.getId())), 0);
    if (!d.accepted()) throw refused(p, d);
    LeagueRules rules = ctx.rules();
    PlayerContract before = p.getContract();
    PlayerContract after = ContractMath.extend(before, before.yearIndexOf(ctx.year()), ctx.completed(), offer, rules.getBonusProrationMaxYears());
    List<Player> roster = clubPlayers(club);
    cap.requireRoom(save, club.getId(), roster, ContractMath.capDelta(before, after, ctx.year(), after.endSeason(), rules.getBonusProrationMaxYears()));
    p.setContract(after);
    players.save(p);
    market.record(save, club, p, ContractEvent.Kind.RENEWAL, ContractEvent.Source.USER, after, offer.apy());
    audit.ok(AuditActions.CONTRACT_RENEWED, "Player", p.getId(), offer.years() + " anos, média " + offer.apy());
    return result("RENEWAL", save, club, p, offer);
  }

  /** Proposta válida (anos, salários, luvas e garantias) ou 422 {@code OFFER_INVALID} com {@code errors[]}. */
  static ContractOffer validOffer(ContractOffer offer, CareerSave save) {
    List<String> errors = offer == null ? List.of("years") : offer.violations(MarketRules.MIN_SALARY, CapSheet.salaryCap(save));
    if (!errors.isEmpty()) {
      ScoresException e = new BusinessRuleException("OFFER_INVALID", "Proposta inválida: " + String.join(", ", errors), String.join(", ", errors));
      throw e.withDetail("errors", errors);
    }
    return offer;
  }

  /** 422 {@code OFFER_REFUSED}: a contraproposta do jogador e os dois valores comparados. */
  public static ScoresException refused(Player p, ContractDemand.Decision d) {
    MarketDtos.OfferDto counter = MarketAssembler.offer(d.counter());
    ScoresException e = new BusinessRuleException("OFFER_REFUSED", p.getName() + " recusou a proposta", p.getName(), BigDecimalCents.of(d.counter().apy()));
    return e.withDetail("playerId", p.getId()).withDetail("counterOffer", counter).withDetail("offerValue", d.offerScore())
        .withDetail("askingValue", d.askingScore());
  }

  // ================================================================ reestruturação

  @Transactional
  public MarketDtos.ContractResultDto restructure(UUID careerId, UUID userId, UUID playerId, Long amount) {
    saves.findByIdForUpdate(careerId);
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    Player p = support.clubPlayer(save, playerId);
    ContractActions.Context ctx = actions.context(save);
    String blocked = ContractActions.restructureBlocked(ctx, p);
    if (blocked != null) {
      throw new BusinessRuleException("RESTRUCTURE_NOT_ALLOWED", "Reestruturação não permitida: " + blocked, blocked).withDetail("reason", blocked);
    }
    PlayerContract before = p.getContract();
    int y = before.yearIndexOf(ctx.year());
    long max = ContractMath.maxRestructure(before, y);
    long value = amount == null ? max : amount;
    if (value <= 0 || value > max) {
      throw new BusinessRuleException("RESTRUCTURE_INVALID", "Valor fora da faixa da reestruturação", BigDecimalCents.of(max)).withDetail("max", max);
    }
    LeagueRules rules = ctx.rules();
    PlayerContract after = ContractMath.restructure(before, y, value, rules.getBonusProrationMaxYears());
    cap.requireRoom(save, club.getId(), clubPlayers(club), ContractMath.capDelta(before, after, ctx.year(), after.endSeason(), rules.getBonusProrationMaxYears()));
    p.setContract(after);
    players.save(p);
    market.record(save, club, p, ContractEvent.Kind.RESTRUCTURE, ContractEvent.Source.USER, after, value);
    audit.ok(AuditActions.CONTRACT_RESTRUCTURED, "Player", p.getId(), "convertido " + value);
    return result("RESTRUCTURE", save, club, p, null);
  }

  // ================================================================ franchise tag

  @Transactional
  public MarketDtos.ContractResultDto franchiseTag(UUID careerId, UUID userId, UUID playerId) {
    saves.findByIdForUpdate(careerId);
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    Player p = support.clubPlayer(save, playerId);
    ContractActions.Context ctx = actions.context(save);
    String blocked = ContractActions.tagBlocked(ctx, p);
    if ("TAG_USED".equals(blocked)) {
      ContractEvent used = events.findBySeasonIdAndClubIdAndKind(ctx.season().getId(), club.getId(), ContractEvent.Kind.FRANCHISE_TAG).get(0);
      throw new ConflictException("FRANCHISE_TAG_USED", "A franchise tag desta temporada já foi usada", used.getPlayer().getName())
          .withDetail("playerId", used.getPlayer().getId());
    }
    if (blocked != null) {
      throw new BusinessRuleException("FRANCHISE_TAG_NOT_ALLOWED", "Franchise tag não permitida: " + blocked, blocked).withDetail("reason", blocked);
    }
    LeagueRules rules = ctx.rules();
    PlayerContract before = p.getContract();
    long value = actions.tagValue(save, p);
    PlayerContract after = ContractMath.franchiseTag(before, before.yearIndexOf(ctx.year()), value, rules.getBonusProrationMaxYears());
    cap.requireRoom(save, club.getId(), clubPlayers(club), ContractMath.capDelta(before, after, ctx.year(), after.endSeason(), rules.getBonusProrationMaxYears()));
    p.setContract(after);
    players.save(p);
    market.record(save, club, p, ContractEvent.Kind.FRANCHISE_TAG, ContractEvent.Source.USER, after, value);
    audit.ok(AuditActions.FRANCHISE_TAG, "Player", p.getId(), "valor " + value);
    return result("FRANCHISE_TAG", save, club, p, null);
  }

  // ================================================================ trade block

  @Transactional
  public RosterDtos.PlayerCardDto tradeBlock(UUID careerId, UUID userId, UUID playerId, boolean on) {
    saves.findByIdForUpdate(careerId);
    CareerSave save = support.writable(careerId, userId);
    support.userClub(save);
    Player p = support.clubPlayer(save, playerId);
    if (p.isTradeBlock() != on) {
      p.setTradeBlock(on);
      players.save(p);
      audit.ok(AuditActions.TRADE_BLOCK, "Player", p.getId(), on ? "no trade block" : "fora do trade block");
    }
    return RosterUseCases.card(p, CareerSupport.seasonYear(save), CareerSupport.rules(save), List.of());
  }

  // ================================================================ apoio

  private List<Player> clubPlayers(Club club) {
    return players.findByClubIdOrderByOverallDesc(club.getId()).stream().filter(x -> x.getRosterStatus().requiresClub()).toList();
  }

  private MarketDtos.ContractResultDto result(String kind, CareerSave save, Club club, Player p, ContractOffer offer) {
    LeagueRules rules = CareerSupport.rules(save);
    int year = CareerSupport.seasonYear(save);
    List<Player> roster = clubPlayers(club);
    return new MarketDtos.ContractResultDto(kind, p.getId(), p.getName(), p.getPosition().name(), p.getRosterStatus().name(),
        EditorAssembler.contract(p.getContract(), rules), CapSheet.capHit(p, year, rules), MarketAssembler.offer(offer), cap.of(save, club.getId(), roster),
        rosterUseCases.counts(save, roster));
  }

  /** Centavos → S$ para as mensagens ({@code {n,number,#,##0.00}}). */
  static final class BigDecimalCents {
    private BigDecimalCents() {}
    static java.math.BigDecimal of(long cents) { return java.math.BigDecimal.valueOf(cents, 2); }
  }
}
