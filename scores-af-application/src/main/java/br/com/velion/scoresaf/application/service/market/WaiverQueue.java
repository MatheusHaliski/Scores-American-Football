package br.com.velion.scoresaf.application.service.market;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WaiverClaimRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldFactory;
import br.com.velion.scoresaf.application.service.WaiverClaimPolicy;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.market.ContractEvent;
import br.com.velion.scoresaf.domain.market.WaiverClaim;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A fila de waivers (RF65, D-137; substitui o {@code NoWaiverClaims} provisório da D-78). Para cada exposto (o melhor primeiro), as
 * franquias da liga são consultadas na ordem inversa da classificação ({@link MarketService#priorityOrder}); leva a primeira que:
 *
 * <ul>
 *   <li><b>usuário</b>: tem reivindicação pendente pelo jogador ({@code POST /waivers/{pid}/claim}), vaga nos 53 e espaço no teto para o
 *   contrato que assume;</li>
 *   <li><b>IA</b>: tem vaga nos 53, precisa da posição (abaixo da composição de referência do elenco) e cabe no teto deixando a reserva
 *   das vagas que faltam.</li>
 * </ul>
 * A franquia que expôs o jogador não o reivindica. Quem leva assume o contrato nos 53 (ACTIVE); de um dispensado, só os salários e as
 * garantias: as luvas ficam com quem dispensou, e o dead money da dispensa passa a ser só o das luvas (a garantia saiu com o contrato).
 * As reivindicações pendentes dos expostos são apagadas no fim do período, com ou sem sucesso.
 */
@Component
public class WaiverQueue implements WaiverClaimPolicy {
  private final MarketService market; private final WaiverClaimRepository claims; private final RosterMoveRepository moves; private final AuditService audit;

  public WaiverQueue(MarketService market, WaiverClaimRepository claims, RosterMoveRepository moves, AuditService audit) {
    this.market = market; this.claims = claims; this.moves = moves; this.audit = audit;
  }

  @Override
  public Set<UUID> claim(CareerSave save, Season season, int week, List<Player> exposed) {
    Set<UUID> out = new HashSet<>();
    List<WaiverClaim> pending = claims.findByCareerIdOrderByCreatedAtAsc(save.getId());
    Set<UUID> userWants = new HashSet<>();
    for (WaiverClaim c : pending) userWants.add(c.getPlayer().getId());
    List<Club> order = market.priorityOrder(save);
    UUID user = save.getClub() == null ? null : save.getClub().getId();
    Map<UUID, MarketService.Book> books = new HashMap<>();
    Map<Position, Integer> template = DefaultWorldFactory.rosterTemplate();
    List<Player> sorted = new ArrayList<>(exposed);
    sorted.sort(MarketService.BEST);
    for (Player x : sorted) {
      RosterMove release = x.getRosterStatus() == RosterStatus.WAIVED ? lastRelease(save, x) : null;
      Club from = x.getRosterStatus() == RosterStatus.WAIVED ? (release == null ? null : release.getClub()) : x.getClub();
      PlayerContract original = x.getContract();
      PlayerContract contract = x.getRosterStatus() == RosterStatus.WAIVED ? MarketService.claimContract(original) : original;
      if (contract == null) continue;
      for (Club c : order) {
        if (from != null && c.getId().equals(from.getId())) continue;
        boolean isUser = c.getId().equals(user);
        if (isUser && !userWants.contains(x.getId())) continue;
        MarketService.Book b = books.computeIfAbsent(c.getId(), k -> market.book(save, c));
        long hit = contract.capHit(contract.yearIndexOf(b.seasonYear), b.rules.getBonusProrationMaxYears());
        boolean room = b.counts().roster() < b.rules.getRosterMax();
        boolean wins = isUser ? room && (!b.rules.isHardCap() || b.committed + hit <= b.salaryCap)
            : room && template.getOrDefault(x.getPosition(), 0) > b.rosterByPosition.getOrDefault(x.getPosition(), 0) && b.affords(hit, RosterStatus.ACTIVE);
        if (!wins) continue;
        RosterStatus fromStatus = x.getRosterStatus();
        market.sign(save, b, x, contract, RosterStatus.ACTIVE, RosterMoveKind.WAIVER_CLAIM, ContractEvent.Kind.WAIVER_CLAIM,
            isUser ? ContractEvent.Source.USER : ContractEvent.Source.AI);
        if (release != null) relieve(release, bonusLeft(original, release.getSeason().getYear(), b.rules.getBonusProrationMaxYears()),
            original.prorationFor(Math.max(0, original.yearIndexOf(release.getSeason().getYear())), b.rules.getBonusProrationMaxYears()));
        audit.ok(AuditActions.WAIVER_CLAIM, "Player", x.getId(), "reivindicado de " + fromStatus + " por " + c.getShortName() + (isUser ? " (usuário)" : " (IA)"));
        out.add(x.getId());
        break;
      }
    }
    Set<UUID> resolved = new HashSet<>();
    for (Player x : exposed) resolved.add(x.getId());
    List<WaiverClaim> done = pending.stream().filter(c -> resolved.contains(c.getPlayer().getId())).toList();
    for (WaiverClaim c : done) {
      if (!out.contains(c.getPlayer().getId()) || c.getPlayer().getClub() == null || !c.getPlayer().getClub().getId().equals(user)) {
        audit.ok(AuditActions.WAIVER_CLAIM, "Player", c.getPlayer().getId(), "reivindicação do usuário não atendida");
      }
      claims.delete(c);
    }
    return out;
  }

  /** Luvas ainda não lançadas no teto quando o jogador foi dispensado na temporada {@code seasonYear}. */
  static long bonusLeft(PlayerContract c, int seasonYear, int prorationMaxYears) {
    long out = 0;
    for (int y = Math.max(0, c.yearIndexOf(seasonYear)); y < c.years(); y++) out += c.prorationFor(y, prorationMaxYears);
    return out;
  }

  /**
   * A dispensa do jogador reivindicado passa a ter só o dead money das luvas (a garantia foi com o contrato): depois de 1º de junho
   * (com parte na temporada seguinte), a parte da temporada fica até o que ela já tinha e o resto vai para a seguinte.
   */
  private void relieve(RosterMove release, long bonusLeft, long yearBonus) {
    long total = release.getDeadMoney() + release.getDeadMoneyNextSeason();
    long keep = Math.min(total, bonusLeft);
    if (release.getDeadMoneyNextSeason() > 0) {
      long current = Math.min(Math.min(release.getDeadMoney(), yearBonus), keep);
      release.setDeadMoney(current);
      release.setDeadMoneyNextSeason(keep - current);
    } else {
      release.setDeadMoney(keep);
    }
    moves.save(release);
  }

  private RosterMove lastRelease(CareerSave save, Player p) {
    RosterMove out = null;
    for (RosterMove m : moves.findByCareerIdAndPlayerIdOrderByCreatedAtAsc(save.getId(), p.getId())) if (m.getKind() == RosterMoveKind.RELEASE) out = m;
    return out;
  }
}
