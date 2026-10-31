package br.com.velion.scoresaf.application.usecase.rf04;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.dto.RosterDtos;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.service.CapSheet;
import br.com.velion.scoresaf.application.service.DepthChartService;
import br.com.velion.scoresaf.application.service.market.ContractActions;
import br.com.velion.scoresaf.application.service.market.MarketService;
import br.com.velion.scoresaf.application.usecase.rf05.EditorAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.career.RosterRules;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.engine.ratings.OverallCalculator;
import br.com.velion.scoresaf.domain.engine.ratings.PositionRating;
import br.com.velion.scoresaf.domain.match.ChartSlot;
import br.com.velion.scoresaf.domain.match.DepthChart;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.Unit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF4 / RF56 (leitura) — elenco da franquia do usuário: os 53 por unidade (ataque, defesa, times especiais), practice squad, IR e
 * os expostos a waivers rumo ao practice squad, com overall na posição, macros da família, nível e contrato; o jogador da carreira em
 * detalhe; movimentos entre 53, practice squad e IR pelas {@link RosterRules} sobre {@code LeagueRules} (dos 53 para o practice squad
 * o jogador passa por waivers até a semana avançar, Q-23: {@code service/WaiverWire}); dispensa para waivers com dead money pelo
 * contrato do bloco 4 e a regra do 1º de junho, recusada com 422 {@code CAP_EXCEEDED} quando estoura o teto (Q-24; a fila de
 * waivers e os claims da IA são do bloco 7). Todo movimento fica no histórico ({@link RosterMove}) e tira o jogador do depth chart
 * quando ele deixa de estar ativo.
 */
@Service
public class RosterUseCases {
  private final CareerSupport support; private final PlayerRepository players; private final RosterMoveRepository moves;
  private final DepthChartService depthCharts; private final CapSheet cap; private final AuditService audit;
  private final ContractActions contractActions; private final MarketService market;

  public RosterUseCases(CareerSupport support, PlayerRepository players, RosterMoveRepository moves, DepthChartService depthCharts, CapSheet cap,
                        AuditService audit, ContractActions contractActions, MarketService market) {
    this.support = support; this.players = players; this.moves = moves; this.depthCharts = depthCharts; this.cap = cap; this.audit = audit;
    this.contractActions = contractActions; this.market = market;
  }

  private static final Comparator<Player> ORDER = Comparator.comparingInt((Player p) -> p.getPosition().ordinal())
      .thenComparing(Comparator.comparingInt(Player::getOverall).reversed()).thenComparingInt(Player::getJerseyNumber);

  @Transactional
  public RosterDtos.RosterDto roster(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    Club club = support.userClub(save);
    LeagueRules rules = CareerSupport.rules(save);
    int year = CareerSupport.seasonYear(save);
    List<Player> roster = players.findByClubIdOrderByOverallDesc(club.getId());
    Map<UUID, List<String>> chart = chartLabels(save, club, rules);
    Map<String, List<RosterDtos.PlayerCardDto>> units = new LinkedHashMap<>();
    for (Unit u : Unit.values()) units.put(u.name(), new ArrayList<>());
    List<RosterDtos.PlayerCardDto> ps = new ArrayList<>(), ir = new ArrayList<>(), waivers = new ArrayList<>();
    for (Player p : roster.stream().sorted(ORDER).toList()) {
      RosterDtos.PlayerCardDto card = card(p, year, rules, chart.getOrDefault(p.getId(), List.of()));
      if (p.getRosterStatus().countsTowardRoster()) units.get(p.getUnit().name()).add(card);
      else if (p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD) ps.add(card);
      else if (p.getRosterStatus() == RosterStatus.IR) ir.add(card);
      else if (p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD_WAIVERS) waivers.add(card);
    }
    return new RosterDtos.RosterDto(club.getId(), club.getName(), year, save.getCurrentWeek(), rulesDto(rules), counts(save, roster), cap.of(save, roster),
        units, ps, ir, waivers);
  }

  @Transactional
  public RosterDtos.PlayerDetailDto player(UUID careerId, UUID userId, UUID playerId) {
    CareerSave save = support.owned(careerId, userId);
    Player p = support.worldPlayer(save, playerId);
    LeagueRules rules = CareerSupport.rules(save);
    int year = CareerSupport.seasonYear(save);
    boolean own = save.getClub() != null && p.getClub() != null && p.getClub().getId().equals(save.getClub().getId());
    List<String> labels = own ? chartLabels(save, save.getClub(), rules).getOrDefault(p.getId(), List.of()) : List.of();
    Map<String, Integer> byPosition = new LinkedHashMap<>();
    OverallCalculator.overallByPosition(p.getAttributes()).forEach((pos, v) -> byPosition.put(pos.name(), v));
    List<RosterMove> history = moves.findByCareerIdAndPlayerIdOrderByCreatedAtAsc(save.getId(), p.getId());
    Integer irWeek = p.getRosterStatus() == RosterStatus.IR ? irPlacedWeek(history) : null;
    Integer irReturn = irWeek == null ? null : irWeek + rules.getIrMinWeeks();
    PlayerContract.DeadMoney dead = CapSheet.deadMoneyIfCut(p, year, cap.gameDate(save), rules);
    return new RosterDtos.PlayerDetailDto(card(p, year, rules, labels), p.getClub() == null ? null : p.getClub().getId(),
        p.getClub() == null ? null : p.getClub().getName(), own, p.getNationality(), p.getCollege(), br.com.velion.scoresaf.application.common.text.IsoDates.of(p.getBirthDate()), p.getPotential(),
        p.getAttributes().asGroupedMap(), byPosition, EditorAssembler.contract(p.getContract(), rules), dead.currentSeason(), dead.nextSeason(),
        irWeek, irReturn, history.stream().map(RosterUseCases::move).toList(), contractActions.playerMarket(save, p, market.discountPct(save)));
  }

  /**
   * Troca a situação no elenco (ACTIVE, INACTIVE, PRACTICE_SQUAD, IR); dispensar é {@link #release}. Dos 53 para PRACTICE_SQUAD o
   * jogador fica exposto a waivers ({@link RosterStatus#PRACTICE_SQUAD_WAIVERS}, Q-23) até a semana avançar.
   */
  @Transactional
  public RosterDtos.RosterChangeDto changeStatus(UUID careerId, UUID userId, UUID playerId, String status) {
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    Player p = support.clubPlayer(save, playerId);
    RosterStatus to = parseStatus(status);
    LeagueRules rules = CareerSupport.rules(save);
    List<Player> roster = players.findByClubIdOrderByOverallDesc(club.getId());
    List<RosterMove> seasonMoves = seasonMoves(save);
    List<RosterMove> history = moves.findByCareerIdAndPlayerIdOrderByCreatedAtAsc(save.getId(), p.getId());
    Integer irWeek = irPlacedWeek(history);
    int weeksOnIr = save.getCurrentWeek() - (irWeek == null ? 0 : irWeek);
    int irReturns = (int) seasonMoves.stream().filter(m -> m.getFromStatus() == RosterStatus.IR && m.getToStatus().countsTowardRoster()).count();
    RosterRules.Violation v = RosterRules.checkMove(p, to, RosterRules.Counts.of(roster), rules, weeksOnIr, irReturns);
    if (v != null) throw new BusinessRuleException(v.code(), "Movimento não permitido: " + v.code(), v.args().toArray());
    RosterStatus from = p.getRosterStatus();
    RosterStatus landing = RosterRules.landingStatus(from, to);
    if (from != landing) {
      p.setRosterStatus(landing);
      players.save(p);
      record(save, p, club, RosterMoveKind.STATUS_CHANGE, from, landing, PlayerContract.DeadMoney.NONE, 0);
      if (landing != RosterStatus.ACTIVE) depthCharts.removePlayer(save, club, p.getId(), rules.getGameDayActives());
      audit.ok(AuditActions.ROSTER_MOVE, "Player", p.getId(), from + " → " + landing);
    }
    int year = CareerSupport.seasonYear(save);
    return new RosterDtos.RosterChangeDto(card(p, year, rules, chartLabels(save, club, rules).getOrDefault(p.getId(), List.of())), counts(save, roster),
        cap.of(save, roster));
  }

  /**
   * Dispensa: o jogador vai a waivers (WAIVED, sem franquia) e o dead money do contrato entra no teto pela regra do 1º de junho
   * (Q-24: antes, tudo na temporada; a partir dele, a parcela do ano na temporada e o resto na seguinte). Recusada com 422
   * {@code CAP_EXCEEDED} quando o dead money da temporada estoura o teto ({@link CapSheet#requireRoomForRelease}).
   */
  @Transactional
  public RosterDtos.ReleaseResultDto release(UUID careerId, UUID userId, UUID playerId) {
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    Player p = support.clubPlayer(save, playerId);
    LeagueRules rules = CareerSupport.rules(save);
    int year = CareerSupport.seasonYear(save);
    PlayerContract.DeadMoney dead = CapSheet.deadMoneyIfCut(p, year, cap.gameDate(save), rules);
    long hit = CapSheet.capHit(p, year, rules);
    CapSheet.requireRoomForRelease(cap.of(save, players.findByClubIdOrderByOverallDesc(club.getId())), hit, dead.currentSeason(), year, rules.isHardCap());
    RosterStatus from = p.getRosterStatus();
    p.setRosterStatus(RosterStatus.WAIVED);
    p.setClub(null);
    players.save(p);
    record(save, p, club, RosterMoveKind.RELEASE, from, RosterStatus.WAIVED, dead, hit);
    depthCharts.removePlayer(save, club, p.getId(), rules.getGameDayActives());
    audit.ok(AuditActions.PLAYER_RELEASED, "Player", p.getId(), "dead money " + dead.currentSeason() + " + " + dead.nextSeason() + " na próxima temporada");
    List<Player> roster = players.findByClubIdOrderByOverallDesc(club.getId());
    return new RosterDtos.ReleaseResultDto(p.getId(), p.getName(), p.getRosterStatus().name(), dead.currentSeason(), dead.nextSeason(), hit,
        counts(save, roster), cap.of(save, roster));
  }

  private void record(CareerSave save, Player p, Club club, RosterMoveKind kind, RosterStatus from, RosterStatus to, PlayerContract.DeadMoney dead,
                      long hit) {
    RosterMove m = new RosterMove();
    m.setCareer(save); m.setSeason(save.getSeason()); m.setPlayer(p); m.setClub(club); m.setWeek(save.getCurrentWeek()); m.setKind(kind);
    m.setFromStatus(from); m.setToStatus(to); m.setDeadMoney(dead.currentSeason()); m.setDeadMoneyNextSeason(dead.nextSeason()); m.setCapHitRemoved(hit);
    moves.save(m);
  }

  private List<RosterMove> seasonMoves(CareerSave save) {
    return save.getSeason() == null ? List.of() : moves.findByCareerIdAndSeasonIdOrderByCreatedAtAsc(save.getId(), save.getSeason().getId());
  }

  /** Semana em que entrou na IR pela última vez ({@code null} sem registro: veio assim do mundo de origem). */
  static Integer irPlacedWeek(List<RosterMove> history) {
    Integer week = null;
    for (RosterMove m : history) if (m.getToStatus() == RosterStatus.IR) week = m.getWeek();
    return week;
  }

  private static RosterStatus parseStatus(String raw) {
    try {
      return RosterStatus.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InvalidInputException("INVALID_ROSTER_STATUS", "Situação no elenco inválida: " + raw, raw);
    }
  }

  /** Contadores do elenco da franquia do usuário (com os retornos da IR usados na temporada). */
  public RosterDtos.CountsDto counts(CareerSave save, List<Player> roster) {
    RosterRules.Counts c = RosterRules.Counts.of(roster);
    int irReturns = (int) seasonMoves(save).stream().filter(m -> m.getFromStatus() == RosterStatus.IR && m.getToStatus().countsTowardRoster()).count();
    return new RosterDtos.CountsDto(c.roster(), c.active(), c.inactive(), c.practiceSquad(), c.injuredReserve(), irReturns, c.waivers());
  }

  private static RosterDtos.RosterRulesDto rulesDto(LeagueRules r) {
    return new RosterDtos.RosterRulesDto(r.getRosterMax(), r.getGameDayActives(), r.getPracticeSquadMax(), r.getIrMinWeeks(), r.getIrReturnsPerSeason());
  }

  /** Vagas do depth chart de cada jogador ({@code "QB1"}, {@code "KR2"}). */
  private Map<UUID, List<String>> chartLabels(CareerSave save, Club club, LeagueRules rules) {
    DepthChart dc = depthCharts.getOrCreate(save, club, rules.getGameDayActives());
    Map<UUID, List<String>> out = new HashMap<>();
    for (Map.Entry<ChartSlot, List<UUID>> e : depthCharts.read(dc).entrySet()) {
      List<UUID> ids = e.getValue();
      for (int i = 0; i < ids.size(); i++) out.computeIfAbsent(ids.get(i), k -> new ArrayList<>()).add(e.getKey().name() + (i + 1));
    }
    return out;
  }

  public static RosterDtos.PlayerCardDto card(Player p, int year, LeagueRules rules, List<String> chart) {
    PositionRating r = p.ratingAt(p.getPosition());
    return new RosterDtos.PlayerCardDto(p.getId(), p.getName(), p.getJerseyNumber(), p.getPosition().name(),
        p.getSecondaryPosition() == null ? null : p.getSecondaryPosition().name(), p.getUnit().name(), p.getAge(), p.getHeightCm(), p.getWeightKg(),
        r.overall(), r.tier().name(), r.position().family().name(), r.macros().values(), p.getRosterStatus().name(), p.getCondition(), p.getMorale(),
        p.getExperienceYears(), p.getInjuryWeeksOut(), p.getSuspensionWeeks(), CapSheet.capHit(p, year, rules),
        p.getContract() == null ? null : p.getContract().endSeason(), p.getContract() == null || p.getContract().getType() == null ? null : p.getContract().getType().name(),
        List.copyOf(chart), p.isTradeBlock(), p.getContract() != null && p.getContract().isFranchiseTag());
  }

  static RosterDtos.RosterMoveDto move(RosterMove m) {
    return new RosterDtos.RosterMoveDto(m.getId(), m.getWeek(), m.getKind().name(), m.getFromStatus().name(), m.getToStatus().name(), m.getDeadMoney(),
        m.getCreatedAt());
  }
}
