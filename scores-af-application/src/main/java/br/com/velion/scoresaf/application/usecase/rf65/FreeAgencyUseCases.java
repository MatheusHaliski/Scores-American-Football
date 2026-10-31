package br.com.velion.scoresaf.application.usecase.rf65;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.common.exception.ScoresException;
import br.com.velion.scoresaf.application.dto.MarketDtos;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ContractEventRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FreeAgencyWindowRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.service.CapSheet;
import br.com.velion.scoresaf.application.service.market.FreeAgencyService;
import br.com.velion.scoresaf.application.service.market.MarketAssembler;
import br.com.velion.scoresaf.application.service.market.MarketService;
import br.com.velion.scoresaf.application.usecase.rf04.RosterUseCases;
import br.com.velion.scoresaf.application.usecase.rf05.EditorAssembler;
import br.com.velion.scoresaf.application.usecase.rf49.ContractUseCases;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.career.RosterRules;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.market.ContractDemand;
import br.com.velion.scoresaf.domain.market.ContractEvent;
import br.com.velion.scoresaf.domain.market.ContractMath;
import br.com.velion.scoresaf.domain.market.ContractOffer;
import br.com.velion.scoresaf.domain.market.FreeAgencyWindow;
import br.com.velion.scoresaf.domain.market.MarketRules;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.Unit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF65 — free agency (bloco 7a): a lista dos agentes livres com filtros e o pedido do dia ({@code GET /free-agency}), a proposta do
 * usuário ({@code POST /free-agency}: o jogador aceita ou recusa pela regra de {@link ContractDemand}, com o desconto do dia; 422
 * {@code OFFER_REFUSED} com a contraproposta) e os dias da janela ({@code POST /free-agency/advance}: um dia ou até fechar). Fora da
 * janela, os agentes livres que sobraram podem ser contratados a qualquer momento, pelo teto e pelos limites do elenco. As escritas
 * travam a carreira antes de tudo (D-91).
 */
@Service
public class FreeAgencyUseCases {
  /** Ordenações aceitas na lista. */
  public enum Sort { OVERALL, ASKING, AGE, POSITION }

  private final CareerSupport support; private final CareerSaveRepository saves; private final PlayerRepository players; private final CapSheet cap;
  private final MarketService market; private final FreeAgencyService freeAgency; private final FreeAgencyWindowRepository windows;
  private final ContractEventRepository events; private final RosterUseCases roster; private final AuditService audit;

  public FreeAgencyUseCases(CareerSupport support, CareerSaveRepository saves, PlayerRepository players, CapSheet cap, MarketService market,
                            FreeAgencyService freeAgency, FreeAgencyWindowRepository windows, ContractEventRepository events, RosterUseCases roster,
                            AuditService audit) {
    this.support = support; this.saves = saves; this.players = players; this.cap = cap; this.market = market; this.freeAgency = freeAgency;
    this.windows = windows; this.events = events; this.roster = roster; this.audit = audit;
  }

  /** Filtros da lista (todos opcionais). */
  public record Filter(String position, String unit, Integer minOverall, Integer maxAge, String sort, Integer limit) {
    public static Filter none() { return new Filter(null, null, null, null, null, null); }
  }

  /** Proposta a um agente livre: nos 53 (ACTIVE, com a proposta) ou no practice squad (1 ano, salário do practice squad). */
  public record SignCommand(UUID playerId, String rosterStatus, Integer years, List<Long> salaryByYear, Long signingBonus, Long guaranteed) {}

  // ================================================================ lista

  @Transactional(readOnly = true)
  public MarketDtos.FreeAgencyDto list(UUID careerId, UUID userId, Filter f) {
    CareerSave save = support.owned(careerId, userId);
    Club club = support.userClub(save);
    return view(save, club, f == null ? Filter.none() : f);
  }

  private MarketDtos.FreeAgencyDto view(CareerSave save, Club club, Filter f) {
    Position pos = f.position() == null || f.position().isBlank() ? null : parse(Position.class, f.position(), "INVALID_POSITION");
    Unit unit = f.unit() == null || f.unit().isBlank() ? null : parse(Unit.class, f.unit(), "INVALID_VALUE");
    Sort sort = f.sort() == null || f.sort().isBlank() ? Sort.OVERALL : parse(Sort.class, f.sort(), "INVALID_VALUE");
    int limit = f.limit() == null ? 50 : Math.max(1, Math.min(200, f.limit()));
    Season season = save.getSeason();
    LeagueRules rules = CareerSupport.rules(save);
    int discount = market.discountPct(save);
    int start = MarketService.contractStart(save);
    Map<UUID, RosterMove> exits = market.lastExits(save);
    List<Player> clubPlayers = clubPlayers(club);
    RosterRules.Counts counts = RosterRules.Counts.of(clubPlayers);
    List<MarketDtos.FreeAgentDto> rows = new ArrayList<>();
    for (Player p : market.freeAgents(save)) {
      if (pos != null && p.getPosition() != pos && p.getSecondaryPosition() != pos) continue;
      if (unit != null && p.getUnit() != unit) continue;
      if (f.minOverall() != null && p.getOverall() < f.minOverall()) continue;
      if (f.maxAge() != null && p.getAge() > f.maxAge()) continue;
      ContractOffer ask = ContractDemand.ask(MarketService.profile(p, null, null), discount);
      PlayerContract c = ContractMath.sign(start, ask);
      RosterMove exit = exits.get(p.getId());
      String source = exit == null ? "UNSIGNED" : exit.getKind() == RosterMoveKind.CONTRACT_EXPIRED ? "CONTRACT_EXPIRED" : "WAIVED";
      var r = p.ratingAt(p.getPosition());
      rows.add(new MarketDtos.FreeAgentDto(p.getId(), p.getName(), p.getPosition().name(), p.getSecondaryPosition() == null ? null : p.getSecondaryPosition().name(),
          p.getUnit().name(), p.getAge(), r.overall(), r.tier().name(), p.getExperienceYears(), p.getPotential(), source,
          exit == null || exit.getClub() == null ? null : CareerAssembler.club(exit.getClub()),
          RosterRules.checkSigning(p, RosterStatus.PRACTICE_SQUAD, counts, rules) == null, MarketService.acceptsPracticeSquad(p, discount),
          ContractDemand.marketValue(MarketService.profile(p, null, null)), MarketAssembler.offer(ask), c.capHit(0, rules.getBonusProrationMaxYears())));
    }
    Comparator<MarketDtos.FreeAgentDto> order = switch (sort) {
      case ASKING -> Comparator.comparingLong((MarketDtos.FreeAgentDto x) -> -x.asking().apy());
      case AGE -> Comparator.comparingInt(MarketDtos.FreeAgentDto::age);
      case POSITION -> Comparator.comparingInt((MarketDtos.FreeAgentDto x) -> Position.valueOf(x.position()).ordinal());
      default -> Comparator.comparingInt((MarketDtos.FreeAgentDto x) -> -x.overall());
    };
    rows.sort(order.thenComparingInt(x -> -x.overall()).thenComparing(MarketDtos.FreeAgentDto::name));
    int total = rows.size();
    List<MarketDtos.SigningDto> recent = season == null ? List.of() : events.findBySeasonIdOrderByCreatedAtAsc(season.getId()).stream()
        .filter(e -> e.getKind() == ContractEvent.Kind.SIGNING || e.getKind() == ContractEvent.Kind.WAIVER_CLAIM)
        .sorted(Comparator.comparing(ContractEvent::getCreatedAt).reversed().thenComparing(e -> e.getPlayer().getName())).limit(12)
        .map(e -> new MarketDtos.SigningDto(e.getPlayer().getId(), e.getPlayer().getName(), e.getPlayer().getPosition().name(), e.getPlayer().getOverall(),
            CareerAssembler.club(e.getClub()), e.getKind().name(), e.getSource().name(), e.getDay(), e.getYears(), e.getTotalValue(), e.getCreatedAt()))
        .toList();
    return new MarketDtos.FreeAgencyDto(season == null ? null : season.getId(), CareerSupport.seasonYear(save), season == null ? null : season.getPhase().name(),
        window(save, season), cap.of(save, club.getId(), clubPlayers), roster.counts(save, clubPlayers), rules.getRosterMax(), rules.getPracticeSquadMax(),
        total, rows.subList(0, Math.min(limit, rows.size())), recent);
  }

  private MarketDtos.WindowDto window(CareerSave save, Season season) {
    FreeAgencyWindow w = season == null ? null : windows.findBySeasonId(season.getId()).orElse(null);
    int discount = market.discountPct(save);
    if (w == null) return new MarketDtos.WindowDto("NONE", 0, MarketRules.FREE_AGENCY_DAYS, null, discount, false);
    String date = w.isOpen() ? MarketRules.FREE_AGENCY_OPENS.atYear(season.getYear()).plusDays(w.getDay()).toString() : null;
    return new MarketDtos.WindowDto(w.getStatus().name(), w.getDay(), w.getDays(), date, discount, w.isOpen());
  }

  // ================================================================ proposta

  @Transactional
  public MarketDtos.ContractResultDto sign(UUID careerId, UUID userId, SignCommand cmd) {
    saves.findByIdForUpdate(careerId);
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    if (cmd == null || cmd.playerId() == null) throw new InvalidInputException("INVALID_VALUE", "Jogador obrigatório", "playerId");
    Player p = support.worldPlayer(save, cmd.playerId());
    if (p.getRosterStatus() != RosterStatus.FREE_AGENT || p.getClub() != null) {
      throw new BusinessRuleException("NOT_A_FREE_AGENT", "O jogador não é agente livre", p.getName()).withDetail("rosterStatus", p.getRosterStatus().name());
    }
    RosterStatus to = cmd.rosterStatus() == null || cmd.rosterStatus().isBlank() ? RosterStatus.ACTIVE
        : parse(RosterStatus.class, cmd.rosterStatus(), "INVALID_ROSTER_STATUS");
    if (to != RosterStatus.ACTIVE && to != RosterStatus.PRACTICE_SQUAD) throw new InvalidInputException("INVALID_ROSTER_STATUS", "Situação inválida", to.name());
    LeagueRules rules = CareerSupport.rules(save);
    MarketService.Book book = market.book(save, club);
    RosterRules.Violation v = RosterRules.checkSigning(p, to, book.counts(), rules);
    if (v != null) throw new BusinessRuleException(v.code(), "Contratação não permitida: " + v.code(), v.args().toArray());
    int discount = market.discountPct(save);
    int start = MarketService.contractStart(save);
    ContractDemand.Profile profile = MarketService.profile(p, null, null);
    ContractOffer offer;
    PlayerContract contract;
    if (to == RosterStatus.PRACTICE_SQUAD) {
      if (!MarketService.acceptsPracticeSquad(p, discount)) {
        ContractDemand.Decision d = ContractDemand.evaluate(new ContractOffer(1, List.of(MarketRules.PRACTICE_SALARY), 0, 0), profile, discount);
        ScoresException e = ContractUseCases.refused(p, new ContractDemand.Decision(false, d.counter(), d.offerScore(), d.askingScore()));
        throw e.withDetail("reason", "PRACTICE_SQUAD");
      }
      contract = ContractMath.practice(start);
      offer = new ContractOffer(1, List.of(MarketRules.PRACTICE_SALARY), 0, 0);
    } else {
      offer = new ContractUseCases.OfferCommand(cmd.years(), cmd.salaryByYear(), cmd.signingBonus(), cmd.guaranteed()).toOffer();
      List<String> errors = offer.violations(MarketRules.MIN_SALARY, CapSheet.salaryCap(save));
      if (!errors.isEmpty()) {
        throw new BusinessRuleException("OFFER_INVALID", "Proposta inválida: " + String.join(", ", errors), String.join(", ", errors)).withDetail("errors", errors);
      }
      ContractDemand.Decision d = ContractDemand.evaluate(offer, profile, discount);
      if (!d.accepted()) throw ContractUseCases.refused(p, d);
      contract = ContractMath.sign(start, offer);
    }
    int year = CareerSupport.seasonYear(save);
    cap.requireRoom(save, club.getId(), book.players, ContractMath.capDelta(null, contract, year, contract.endSeason(), rules.getBonusProrationMaxYears()));
    market.sign(save, book, p, contract, to, RosterMoveKind.SIGNING, ContractEvent.Kind.SIGNING, ContractEvent.Source.USER);
    audit.ok(AuditActions.FREE_AGENT_SIGNED, "Player", p.getId(), to + ", " + contract.years() + " anos, total " + contract.totalValue());
    List<Player> after = clubPlayers(club);
    return new MarketDtos.ContractResultDto("SIGNING", p.getId(), p.getName(), p.getPosition().name(), p.getRosterStatus().name(),
        EditorAssembler.contract(p.getContract(), rules), CapSheet.capHit(p, year, rules), MarketAssembler.offer(offer), cap.of(save, club.getId(), after),
        roster.counts(save, after));
  }

  // ================================================================ dias da janela

  @Transactional
  public MarketDtos.FreeAgencyDto advance(UUID careerId, UUID userId, String mode) {
    saves.findByIdForUpdate(careerId);
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    String m = mode == null || mode.isBlank() ? "DAY" : mode.trim().toUpperCase(Locale.ROOT);
    if (!m.equals("DAY") && !m.equals("CLOSE")) throw new InvalidInputException("INVALID_ADVANCE_MODE", "Modo inválido: " + mode, mode);
    FreeAgencyWindow w = market.openWindow(save).orElseThrow(() -> new BusinessRuleException("FREE_AGENCY_CLOSED", "A free agency não está aberta"));
    do {
      freeAgency.advanceDay(save, w);
    } while (m.equals("CLOSE") && w.isOpen());
    return view(save, club, Filter.none());
  }

  // ================================================================ apoio

  private List<Player> clubPlayers(Club club) {
    return players.findByClubIdOrderByOverallDesc(club.getId()).stream().filter(x -> x.getRosterStatus().requiresClub()).toList();
  }

  static <E extends Enum<E>> E parse(Class<E> type, String raw, String code) {
    try {
      return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InvalidInputException(code, "Valor inválido: " + raw, raw);
    }
  }
}
