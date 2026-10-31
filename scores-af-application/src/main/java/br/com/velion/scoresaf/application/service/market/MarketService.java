package br.com.velion.scoresaf.application.service.market;

import br.com.velion.scoresaf.application.port.out.persistence.ContractEventRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FreeAgencyWindowRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldFactory;
import br.com.velion.scoresaf.application.service.CapSheet;
import br.com.velion.scoresaf.application.service.StandingsService;
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
import br.com.velion.scoresaf.domain.market.FreeAgencyWindow;
import br.com.velion.scoresaf.domain.market.MarketRules;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Standings;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Apoio comum do mercado (bloco 7a, RF49/RF65): o tempo do mercado (janela de free agency, desconto do dia, temporada em que o contrato
 * novo começa), o pedido de cada jogador ({@link ContractDemand} sobre a fórmula salarial do mundo semente), a fila de prioridade (ordem
 * inversa da classificação, D-137), a folha de cada franquia ({@link Book}) e a mecânica de uma contratação (franquia, situação,
 * contrato, número da camisa, movimento e transação de contrato), usada pelo usuário e pela IA.
 */
@Component
public class MarketService {
  /** O melhor primeiro: overall maior, depois mais novo, nome e id (independe da ordem de leitura, D-109). */
  public static final Comparator<Player> BEST = Comparator.comparingInt((Player p) -> -p.getOverall()).thenComparingInt(Player::getAge)
      .thenComparing(Player::getName).thenComparing(p -> p.getId().toString());

  private final PlayerRepository players; private final RosterMoveRepository moves; private final ContractEventRepository events;
  private final CapSheet cap; private final FreeAgencyWindowRepository windows; private final StandingsService standings;
  private final FixtureRepository fixtures; private final SeasonRepository seasons;

  public MarketService(PlayerRepository players, RosterMoveRepository moves, ContractEventRepository events, CapSheet cap,
                       FreeAgencyWindowRepository windows, StandingsService standings, FixtureRepository fixtures, SeasonRepository seasons) {
    this.players = players; this.moves = moves; this.events = events; this.cap = cap; this.windows = windows; this.standings = standings;
    this.fixtures = fixtures; this.seasons = seasons;
  }

  // ================================================================ tempo do mercado

  /** Janela de free agency aberta na temporada atual. */
  public Optional<FreeAgencyWindow> openWindow(CareerSave save) {
    Season s = save.getSeason();
    return s == null ? Optional.empty() : windows.findBySeasonId(s.getId()).filter(FreeAgencyWindow::isOpen);
  }

  /** Desconto do pedido dos agentes livres: 10 % por dia de free agency; fora da janela, o de quem sobrou no mercado (40 %). */
  public int discountPct(CareerSave save) {
    return openWindow(save).map(w -> discountForDay(w.getDay())).orElse(MarketRules.LATE_MARKET_DISCOUNT_PCT);
  }

  public static int discountForDay(int day) { return Math.min(MarketRules.LATE_MARKET_DISCOUNT_PCT, day * MarketRules.DISCOUNT_PER_DAY_PCT); }

  /** Dia de mercado (nulo fora da janela). */
  public Integer marketDay(CareerSave save) { return openWindow(save).map(FreeAgencyWindow::getDay).orElse(null); }

  /** Temporada em que um contrato novo começa: a atual; com a temporada encerrada (entressafra), a seguinte. */
  public static int contractStart(CareerSave save) {
    Season s = save.getSeason();
    return s != null && s.isCompleted() ? s.getYear() + 1 : CareerSupport.seasonYear(save);
  }

  // ================================================================ pedido

  /** Valor anual da posição pelo overall (a fórmula salarial do mundo semente), em centavos. */
  public static long baseValue(Player p) { return DefaultWorldFactory.yearOneSalary(p.getPosition(), p.getOverall()) * 100L; }

  public static ContractDemand.Profile profile(Player p, Integer morale, String role) {
    return new ContractDemand.Profile(baseValue(p), p.getAge(), p.getOverall(), morale, role);
  }

  /** O agente livre aceita o practice squad quando o que ele pede não passa de 10 % acima do mínimo. */
  public static boolean acceptsPracticeSquad(Player p, int discountPct) {
    return ContractDemand.askingApy(profile(p, null, null), discountPct) <= MarketRules.MIN_SALARY * 11 / 10;
  }

  // ================================================================ fila de prioridade

  /**
   * Fila de prioridade de waivers e da free agency (D-137): todas as franquias da liga na ordem inversa da classificação — percentual
   * menor primeiro, depois a pior colocação na conferência, depois o nome. Usa a temporada atual quando ela já tem jogo encerrado;
   * senão a anterior; sem nenhuma, a projeção da tabela (prestígio).
   */
  public List<Club> priorityOrder(CareerSave save) {
    Season current = save.getSeason();
    if (current == null) return List.of();
    Season ref = current;
    List<Fixture> games = fixtures.findBySeasonId(current.getId());
    if (games.stream().noneMatch(f -> f.isFinished() && f.getWeekNumber() <= current.getRegularSeasonWeeks())) {
      Season prev = seasons.findByCareerIdOrderByNumberAsc(save.getId()).stream().filter(s -> s.getNumber() == current.getNumber() - 1).findFirst()
          .orElse(null);
      if (prev != null) { ref = prev; games = fixtures.findBySeasonId(prev.getId()); }
    }
    StandingsService.League league = standings.league(ref);
    Standings.Table table = standings.table(ref, league, games, ref.getRegularSeasonWeeks(), CareerSupport.rules(save));
    Map<String, Integer> confRank = new HashMap<>();
    table.conferences().values().forEach(l -> l.forEach(r -> confRank.put(r.line().team().key(), r.rank())));
    List<Club> out = new ArrayList<>(league.clubs());
    out.sort(Comparator.comparingDouble((Club c) -> pct(table, c)).thenComparing(c -> -confRank.getOrDefault(c.getId().toString(), 0))
        .thenComparing(Club::getName));
    return out;
  }

  private static double pct(Standings.Table t, Club c) {
    Standings.Line l = t.line(c.getId().toString());
    return l == null ? 0 : l.overall().pct();
  }

  // ================================================================ folha de cada franquia

  /** Franquia no mercado: jogadores, contagens e comprometido da temporada atual, atualizados a cada contratação. */
  public final class Book {
    public final Club club;
    public final List<Player> players;
    public final Map<Position, Integer> rosterByPosition = new EnumMap<>(Position.class);
    public long committed;
    public final long salaryCap;
    public final LeagueRules rules;
    public final int seasonYear;

    Book(CareerSave save, Club club, List<Player> list) {
      this.club = club;
      this.players = new ArrayList<>(list.stream().filter(p -> p.getRosterStatus().requiresClub()).toList());
      this.rules = CareerSupport.rules(save);
      this.seasonYear = contractStart(save);
      this.salaryCap = CapSheet.salaryCap(save);
      this.committed = cap.committed(save, club.getId(), players, seasonYear);
      for (Player p : players) if (p.getRosterStatus().countsTowardRoster()) rosterByPosition.merge(p.getPosition(), 1, Integer::sum);
    }

    public RosterRules.Counts counts() { return RosterRules.Counts.of(players); }

    /** Vagas que faltam nos 53 e no practice squad, pagas pelo mínimo: o que a IA guarda no teto para fechar o elenco. */
    public long reserve(int rosterAfter, int practiceAfter) {
      return Math.max(0, rules.getRosterMax() - rosterAfter) * MarketRules.MIN_SALARY
          + Math.max(0, rules.getPracticeSquadMax() - practiceAfter) * MarketRules.PRACTICE_SALARY;
    }

    /** Cabe {@code hit} no teto deixando a reserva das vagas que faltam (teto flexível: sempre cabe). */
    public boolean affords(long hit, RosterStatus to) {
      if (!rules.isHardCap()) return true;
      RosterRules.Counts c = counts();
      int roster = c.roster() + (to.countsTowardRoster() ? 1 : 0), ps = c.practiceSquadReserved() + (to == RosterStatus.PRACTICE_SQUAD ? 1 : 0);
      return committed + hit + reserve(roster, ps) <= salaryCap;
    }

    void add(Player p, long hit) {
      players.add(p);
      committed += hit;
      if (p.getRosterStatus().countsTowardRoster()) rosterByPosition.merge(p.getPosition(), 1, Integer::sum);
    }
  }

  public Book book(CareerSave save, Club club) { return new Book(save, club, players.findByClubIdOrderByOverallDesc(club.getId())); }

  // ================================================================ contratação

  /**
   * Leva {@code p} para {@code club} em {@code to} com {@code contract}: troca a franquia e a situação, acerta o número da camisa se
   * estiver tomado, grava o movimento ({@code moveKind}) e a transação de contrato. Devolve a transação.
   */
  public ContractEvent sign(CareerSave save, Book book, Player p, PlayerContract contract, RosterStatus to, RosterMoveKind moveKind,
                            ContractEvent.Kind kind, ContractEvent.Source source) {
    RosterStatus from = p.getRosterStatus();
    p.setClub(book.club);
    p.setRosterStatus(to);
    p.setContract(contract);
    p.setTradeBlock(false);
    p.setJerseyNumber(freeJersey(book.players, p));
    players.save(p);
    book.add(p, contract.capHit(contract.yearIndexOf(book.seasonYear), book.rules.getBonusProrationMaxYears()));
    RosterMove m = new RosterMove();
    m.setCareer(save); m.setSeason(save.getSeason()); m.setPlayer(p); m.setClub(book.club); m.setWeek(save.getCurrentWeek()); m.setKind(moveKind);
    m.setFromStatus(from); m.setToStatus(to);
    moves.save(m);
    return record(save, book.club, p, kind, source, contract, kind == ContractEvent.Kind.SIGNING || kind == ContractEvent.Kind.RENEWAL
        ? contract.totalValue() / Math.max(1, contract.years()) : contract.capHit(contract.yearIndexOf(book.seasonYear), book.rules.getBonusProrationMaxYears()));
  }

  /** Grava a transação de contrato (o histórico do mercado). */
  public ContractEvent record(CareerSave save, Club club, Player p, ContractEvent.Kind kind, ContractEvent.Source source, PlayerContract contract,
                              long amount) {
    ContractEvent e = new ContractEvent();
    e.setCareer(save); e.setSeason(save.getSeason()); e.setClub(club); e.setPlayer(p); e.setKind(kind); e.setSource(source);
    e.setDay(marketDay(save));
    e.setYears(contract == null ? 0 : contract.years());
    e.setTotalValue(contract == null ? 0 : contract.totalValue());
    e.setSigningBonus(contract == null ? 0 : contract.getSigningBonus());
    e.setGuaranteed(contract == null ? 0 : contract.getGuaranteed());
    e.setAmount(amount);
    if (kind == ContractEvent.Kind.FRANCHISE_TAG) e.setTagSlot(1);
    return events.save(e);
  }

  /** O número do jogador se estiver livre na franquia; senão o primeiro livre de 1 a 99 (0 por último). */
  static int freeJersey(List<Player> clubPlayers, Player p) {
    Set<Integer> used = new HashSet<>();
    for (Player x : clubPlayers) if (x != p && !x.getId().equals(p.getId())) used.add(x.getJerseyNumber());
    if (!used.contains(p.getJerseyNumber())) return p.getJerseyNumber();
    for (int n = 1; n <= 99; n++) if (!used.contains(n)) return n;
    return 0;
  }

  /** Agentes livres do mundo da carreira (sem franquia e fora de waivers), o melhor primeiro. */
  public List<Player> freeAgents(CareerSave save) {
    List<Player> out = new ArrayList<>(players.findByWorldIdAndRosterStatus(save.getWorld().getId(), RosterStatus.FREE_AGENT));
    out.sort(BEST);
    return out;
  }

  /** De onde veio cada agente livre: o último movimento de saída (fim de contrato, waivers sem reivindicação) da carreira. */
  public Map<UUID, RosterMove> lastExits(CareerSave save) {
    Map<UUID, RosterMove> out = new HashMap<>();
    for (RosterMove m : moves.findByCareerIdOrderByCreatedAtAsc(save.getId())) {
      if (m.getKind() == RosterMoveKind.CONTRACT_EXPIRED || m.getKind() == RosterMoveKind.RELEASE || m.getKind() == RosterMoveKind.WAIVER_UNCLAIMED) {
        out.put(m.getPlayer().getId(), m);
      } else if (m.getKind() == RosterMoveKind.SIGNING || m.getKind() == RosterMoveKind.WAIVER_CLAIM) {
        out.remove(m.getPlayer().getId());
      }
    }
    return out;
  }

  /** Contrato que quem reivindica em waivers assume: os salários e garantias; as luvas ficam como dead money de quem dispensou (D-137). */
  public static PlayerContract claimContract(PlayerContract c) {
    if (c == null) return null;
    return c.toBuilder().baseSalaryByYear(new ArrayList<>(c.getBaseSalaryByYear())).signingBonus(0).prorationByYear(new ArrayList<>()).franchiseTag(false)
        .build();
  }
}
