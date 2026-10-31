package br.com.velion.scoresaf.application.service.market;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.persistence.FreeAgencyWindowRepository;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldFactory;
import br.com.velion.scoresaf.application.service.OffseasonPhase;
import br.com.velion.scoresaf.application.service.WaiverWire;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.career.RosterRules;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.market.ContractDemand;
import br.com.velion.scoresaf.domain.market.ContractEvent;
import br.com.velion.scoresaf.domain.market.ContractMath;
import br.com.velion.scoresaf.domain.market.FreeAgencyWindow;
import br.com.velion.scoresaf.domain.market.MarketRules;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Fase de free agency da entressafra (RF65, D-10, D-136; substitui o {@code NoFreeAgency} provisório da D-99). Abre no avanço da
 * temporada, depois do calendário novo: a temporada nova fica na fase FREE_AGENCY no dia 0 (a semana 1 não joga enquanto a janela está
 * aberta) e a lista é a de todos os agentes livres do mundo (contratos encerrados e dispensados sem reivindicação). A cada dia
 * ({@link #advanceDay}): os pedidos caem 10 %, a IA contrata (cada franquia até {@value MarketRules#AI_SIGNINGS_PER_DAY} por rodada, na
 * ordem inversa da classificação, pela posição de que mais precisa e o que cabe no teto com a reserva das vagas que faltam) e os waivers
 * se resolvem. No último dia ({@link MarketRules#FREE_AGENCY_DAYS}) a IA completa os 53 e o practice squad e a janela fecha: a temporada
 * passa à temporada regular (o draft do bloco 7b entra entre as duas).
 */
@Component
public class FreeAgencyService implements OffseasonPhase {
  private final MarketService market; private final FreeAgencyWindowRepository windows; private final SeasonRepository seasons;
  private final WaiverWire waivers; private final AuditService audit;

  public FreeAgencyService(MarketService market, FreeAgencyWindowRepository windows, SeasonRepository seasons, WaiverWire waivers, AuditService audit) {
    this.market = market; this.windows = windows; this.seasons = seasons; this.waivers = waivers; this.audit = audit;
  }

  @Override public SeasonPhase phase() { return SeasonPhase.FREE_AGENCY; }

  /** Abre a janela na temporada atual da carreira (a nova, já criada pelo avanço). Devolve OPEN. */
  @Override
  public String run(CareerSave save, Season closing) {
    Season season = save.getSeason();
    FreeAgencyWindow w = windows.findBySeasonId(season.getId()).orElseGet(FreeAgencyWindow::new);
    w.setCareer(save); w.setSeason(season); w.setDay(0); w.setDays(MarketRules.FREE_AGENCY_DAYS); w.setStatus(FreeAgencyWindow.Status.OPEN);
    windows.save(w);
    season.setPhase(SeasonPhase.FREE_AGENCY);
    seasons.save(season);
    audit.ok(AuditActions.FREE_AGENCY, "Season", season.getId(), "free agency aberta: " + market.freeAgents(save).size() + " agentes livres");
    return "OPEN";
  }

  /** Um dia de mercado: pedidos caem, a IA contrata, os waivers se resolvem; no último, fecha. Devolve as contratações da IA. */
  public int advanceDay(CareerSave save, FreeAgencyWindow w) {
    w.setDay(w.getDay() + 1);
    windows.save(w);
    boolean closing = w.getDay() >= w.getDays();
    waivers.resolve(save, save.getSeason(), save.getCurrentWeek());
    int signed = aiDay(save, MarketService.discountForDay(w.getDay()), closing);
    if (closing) close(save, w);
    else audit.ok(AuditActions.FREE_AGENCY, "Season", save.getSeason().getId(), "dia " + w.getDay() + ": " + signed + " contratações da IA");
    return signed;
  }

  private void close(CareerSave save, FreeAgencyWindow w) {
    w.setStatus(FreeAgencyWindow.Status.CLOSED);
    w.setClosedAt(Instant.now());
    windows.save(w);
    Season season = save.getSeason();
    season.setPhase(SeasonPhase.REGULAR);
    seasons.save(season);
    audit.ok(AuditActions.FREE_AGENCY, "Season", season.getId(), "free agency fechada no dia " + w.getDay());
  }

  /**
   * Contratações da IA num dia de mercado com o desconto {@code discountPct}. Rodadas na ordem da fila: em cada uma, cada franquia da
   * IA contrata no máximo um jogador, para a posição em que está mais abaixo da composição de referência dos 53, o melhor agente livre
   * daquela posição cujo pedido cabe no teto com a reserva das vagas que faltam. Num dia comum são {@value MarketRules#AI_SIGNINGS_PER_DAY}
   * rodadas; no fechamento ({@code closing}), rodadas até não sobrar vaga: depois das posições em falta, o melhor de qualquer posição, e
   * por fim o practice squad (quem aceita, com a elegibilidade, pelo salário do practice squad).
   */
  public int aiDay(CareerSave save, int discountPct, boolean closing) {
    UUID user = save.getClub() == null ? null : save.getClub().getId();
    List<MarketService.Book> books = new ArrayList<>();
    for (Club c : market.priorityOrder(save)) if (!c.getId().equals(user)) books.add(market.book(save, c));
    List<Player> pool = market.freeAgents(save);
    Set<UUID> taken = new HashSet<>();
    Map<Position, Integer> template = DefaultWorldFactory.rosterTemplate();
    int start = MarketService.contractStart(save);
    int signed = 0;
    int rounds = closing ? Integer.MAX_VALUE : MarketRules.AI_SIGNINGS_PER_DAY;
    for (int r = 0; r < rounds; r++) {
      boolean any = false;
      for (MarketService.Book b : books) {
        if (b.counts().roster() >= b.rules.getRosterMax()) continue;
        Pick pick = pickForRoster(b, pool, taken, template, discountPct, start, closing);
        if (pick == null) continue;
        taken.add(pick.player().getId());
        market.sign(save, b, pick.player(), pick.contract(), RosterStatus.ACTIVE, RosterMoveKind.SIGNING, ContractEvent.Kind.SIGNING, ContractEvent.Source.AI);
        signed++;
        any = true;
      }
      if (!any) break;
    }
    if (closing) {
      boolean any = true;
      while (any) {
        any = false;
        for (MarketService.Book b : books) {
          Player p = pickForPracticeSquad(b, pool, taken, discountPct);
          if (p == null) continue;
          taken.add(p.getId());
          market.sign(save, b, p, ContractMath.practice(start), RosterStatus.PRACTICE_SQUAD, RosterMoveKind.SIGNING, ContractEvent.Kind.SIGNING,
              ContractEvent.Source.AI);
          signed++;
          any = true;
        }
      }
    }
    return signed;
  }

  record Pick(Player player, PlayerContract contract) {}

  /** O agente livre que a franquia contrata para os 53 agora (ou nenhum). */
  static Pick pickForRoster(MarketService.Book b, List<Player> pool, Set<UUID> taken, Map<Position, Integer> template, int discountPct, int start,
                            boolean anyPosition) {
    List<Position> needs = new ArrayList<>();
    for (Position p : Position.values()) if (template.getOrDefault(p, 0) > b.rosterByPosition.getOrDefault(p, 0)) needs.add(p);
    needs.sort(Comparator.comparingInt((Position p) -> b.rosterByPosition.getOrDefault(p, 0) - template.getOrDefault(p, 0)).thenComparingInt(Enum::ordinal));
    for (Position need : needs) {
      Pick p = firstAffordable(b, pool, taken, need, discountPct, start);
      if (p != null) return p;
    }
    return anyPosition ? firstAffordable(b, pool, taken, null, discountPct, start) : null;
  }

  private static Pick firstAffordable(MarketService.Book b, List<Player> pool, Set<UUID> taken, Position pos, int discountPct, int start) {
    int prorate = b.rules.getBonusProrationMaxYears();
    for (Player p : pool) {
      if (taken.contains(p.getId()) || (pos != null && p.getPosition() != pos)) continue;
      PlayerContract c = ContractMath.sign(start, ContractDemand.ask(MarketService.profile(p, null, null), discountPct));
      if (b.affords(c.capHit(c.yearIndexOf(b.seasonYear), prorate), RosterStatus.ACTIVE)) return new Pick(p, c);
    }
    return null;
  }

  private static Player pickForPracticeSquad(MarketService.Book b, List<Player> pool, Set<UUID> taken, int discountPct) {
    RosterRules.Counts counts = b.counts();
    if (counts.practiceSquadReserved() >= b.rules.getPracticeSquadMax()) return null;
    if (!b.affords(MarketRules.PRACTICE_SALARY, RosterStatus.PRACTICE_SQUAD)) return null;
    for (Player p : pool) {
      if (taken.contains(p.getId()) || !MarketService.acceptsPracticeSquad(p, discountPct)) continue;
      if (RosterRules.checkSigning(p, RosterStatus.PRACTICE_SQUAD, counts, b.rules) == null) return p;
    }
    return null;
  }
}
