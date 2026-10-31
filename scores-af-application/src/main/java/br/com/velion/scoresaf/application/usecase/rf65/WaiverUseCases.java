package br.com.velion.scoresaf.application.usecase.rf65;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.dto.MarketDtos;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ContractEventRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WaiverClaimRepository;
import br.com.velion.scoresaf.application.service.CapSheet;
import br.com.velion.scoresaf.application.service.market.MarketService;
import br.com.velion.scoresaf.application.usecase.rf04.RosterUseCases;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.market.ContractEvent;
import br.com.velion.scoresaf.domain.market.ContractMath;
import br.com.velion.scoresaf.domain.market.WaiverClaim;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF65 — waivers (bloco 7a, D-137): os expostos (dispensados e quem saiu dos 53 rumo ao practice squad), a prioridade do usuário na fila
 * (ordem inversa da classificação) e quando o período fecha ({@code GET /waivers}); a reivindicação ({@code POST /waivers/{pid}/claim},
 * conferida na hora — vaga nos 53 e teto para o contrato que assume — e de novo no fim do período) e o cancelamento
 * ({@code DELETE /waivers/{pid}/claim}). Reivindicação repetida responde 409 {@code WAIVER_CLAIM_EXISTS} (chave única).
 */
@Service
public class WaiverUseCases {
  private final CareerSupport support; private final CareerSaveRepository saves; private final PlayerRepository players; private final CapSheet cap;
  private final MarketService market; private final WaiverClaimRepository claims; private final RosterMoveRepository moves; private final ContractEventRepository events;
  private final RosterUseCases roster; private final AuditService audit;

  public WaiverUseCases(CareerSupport support, CareerSaveRepository saves, PlayerRepository players, CapSheet cap, MarketService market,
                        WaiverClaimRepository claims, RosterMoveRepository moves, ContractEventRepository events, RosterUseCases roster, AuditService audit) {
    this.support = support; this.saves = saves; this.players = players; this.cap = cap; this.market = market; this.claims = claims; this.moves = moves;
    this.events = events; this.roster = roster; this.audit = audit;
  }

  @Transactional(readOnly = true)
  public MarketDtos.WaiversDto list(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    return view(save, support.userClub(save));
  }

  @Transactional
  public MarketDtos.WaiversDto claim(UUID careerId, UUID userId, UUID playerId) {
    saves.findByIdForUpdate(careerId);
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    Player p = support.worldPlayer(save, playerId);
    Exposed x = exposed(save, p);
    if (x == null) throw new BusinessRuleException("NOT_ON_WAIVERS", "O jogador não está em waivers", p.getName());
    if (claims.findByCareerIdAndPlayerIdAndClubId(save.getId(), p.getId(), club.getId()).isPresent()) {
      throw new ConflictException("WAIVER_CLAIM_EXISTS", "Você já reivindicou este jogador", p.getName());
    }
    String blocked = blocked(save, club, x, clubPlayers(club));
    if ("OWN_PLAYER".equals(blocked)) throw new BusinessRuleException("WAIVER_OWN_PLAYER", "O jogador foi exposto pela sua franquia", p.getName());
    if ("ROSTER_FULL".equals(blocked)) throw new BusinessRuleException("ROSTER_FULL", "Elenco completo", CareerSupport.rules(save).getRosterMax());
    List<Player> mine = clubPlayers(club);
    int year = MarketService.contractStart(save);
    cap.requireRoom(save, club.getId(), mine, ContractMath.capDelta(null, x.contract(), year, x.contract().endSeason(),
        CareerSupport.rules(save).getBonusProrationMaxYears()));
    WaiverClaim c = new WaiverClaim();
    c.setCareer(save); c.setPlayer(p); c.setClub(club);
    claims.save(c);
    audit.ok(AuditActions.WAIVER_CLAIM, "Player", p.getId(), "reivindicação pedida");
    return view(save, club);
  }

  @Transactional
  public MarketDtos.WaiversDto cancel(UUID careerId, UUID userId, UUID playerId) {
    saves.findByIdForUpdate(careerId);
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    Player p = support.worldPlayer(save, playerId);
    WaiverClaim c = claims.findByCareerIdAndPlayerIdAndClubId(save.getId(), p.getId(), club.getId())
        .orElseThrow(() -> new NotFoundException("Reivindicação", playerId));
    claims.delete(c);
    audit.ok(AuditActions.WAIVER_CLAIM, "Player", p.getId(), "reivindicação cancelada");
    return view(save, club);
  }

  /** Exposto a waivers: de onde veio e o contrato que quem reivindicar assume. */
  record Exposed(Player player, String kind, Club from, PlayerContract contract) {}

  private Exposed exposed(CareerSave save, Player p) {
    if (p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD_WAIVERS) return new Exposed(p, "PRACTICE_SQUAD", p.getClub(), p.getContract());
    if (p.getRosterStatus() != RosterStatus.WAIVED) return null;
    Club from = null;
    for (RosterMove m : moves.findByCareerIdAndPlayerIdOrderByCreatedAtAsc(save.getId(), p.getId())) if (m.getKind() == RosterMoveKind.RELEASE) from = m.getClub();
    return new Exposed(p, "RELEASED", from, MarketService.claimContract(p.getContract()));
  }

  /** Por que o usuário não pode reivindicar (nulo = pode). */
  private String blocked(CareerSave save, Club club, Exposed x, List<Player> mine) {
    if (x.from() != null && x.from().getId().equals(club.getId())) return "OWN_PLAYER";
    if (x.contract() == null) return "NO_CONTRACT";
    LeagueRules rules = CareerSupport.rules(save);
    if (mine.stream().filter(p -> p.getRosterStatus().countsTowardRoster()).count() >= rules.getRosterMax()) return "ROSTER_FULL";
    int year = MarketService.contractStart(save);
    long hit = x.contract().capHit(x.contract().yearIndexOf(year), rules.getBonusProrationMaxYears());
    if (rules.isHardCap() && cap.committed(save, club.getId(), mine, year) + hit > CapSheet.salaryCap(save)) return "CAP_EXCEEDED";
    return null;
  }

  private MarketDtos.WaiversDto view(CareerSave save, Club club) {
    Season season = save.getSeason();
    LeagueRules rules = CareerSupport.rules(save);
    int year = MarketService.contractStart(save);
    List<Club> order = market.priorityOrder(save);
    int priority = 0;
    for (int i = 0; i < order.size(); i++) if (order.get(i).getId().equals(club.getId())) priority = i + 1;
    Set<UUID> mineClaims = new HashSet<>();
    for (WaiverClaim c : claims.findByCareerIdOrderByCreatedAtAsc(save.getId())) if (c.getClub().getId().equals(club.getId())) mineClaims.add(c.getPlayer().getId());
    List<Player> mine = clubPlayers(club);
    List<Player> exposedPlayers = new ArrayList<>(players.findByWorldIdAndRosterStatus(save.getWorld().getId(), RosterStatus.WAIVED));
    exposedPlayers.addAll(players.findByWorldIdAndRosterStatus(save.getWorld().getId(), RosterStatus.PRACTICE_SQUAD_WAIVERS));
    exposedPlayers.sort(MarketService.BEST);
    List<MarketDtos.WaiverPlayerDto> rows = new ArrayList<>();
    for (Player p : exposedPlayers) {
      Exposed x = exposed(save, p);
      String blocked = blocked(save, club, x, mine);
      boolean claimed = mineClaims.contains(p.getId());
      PlayerContract c = x.contract();
      var r = p.ratingAt(p.getPosition());
      rows.add(new MarketDtos.WaiverPlayerDto(p.getId(), p.getName(), p.getPosition().name(), p.getAge(), r.overall(), r.tier().name(), x.kind(),
          x.from() == null ? null : CareerAssembler.club(x.from()), c == null ? 0 : c.capHit(c.yearIndexOf(year), rules.getBonusProrationMaxYears()),
          c == null ? 0 : Math.max(0, c.endSeason() - year + 1), claimed, blocked == null && !claimed, claimed ? "ALREADY_CLAIMED" : blocked));
    }
    String resolvesAt = season == null ? "SEASON_ADVANCE" : season.getPhase() == SeasonPhase.FREE_AGENCY ? "FREE_AGENCY_DAY"
        : season.isCompleted() ? "SEASON_ADVANCE" : "WEEK_CLOSE";
    List<MarketDtos.ClaimResultDto> recent = season == null ? List.of() : events.findBySeasonIdOrderByCreatedAtAsc(season.getId()).stream()
        .filter(e -> e.getKind() == ContractEvent.Kind.WAIVER_CLAIM).sorted(Comparator.comparing(ContractEvent::getCreatedAt).reversed()).limit(10)
        .map(e -> new MarketDtos.ClaimResultDto(e.getPlayer().getId(), e.getPlayer().getName(), e.getPlayer().getPosition().name(),
            CareerAssembler.club(e.getClub()), e.getSource().name(), e.getCreatedAt())).toList();
    return new MarketDtos.WaiversDto(priority, order.size(), resolvesAt, cap.of(save, club.getId(), mine), roster.counts(save, mine), rows, recent);
  }

  private List<Player> clubPlayers(Club club) {
    return players.findByClubIdOrderByOverallDesc(club.getId()).stream().filter(x -> x.getRosterStatus().requiresClub()).toList();
  }
}
