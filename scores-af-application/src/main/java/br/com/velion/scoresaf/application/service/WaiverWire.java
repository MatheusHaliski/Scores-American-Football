package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Fim do período de waivers (Q-23, D-78; bloco 7a, D-137), chamado quando a semana fecha ({@link WeekProcessor}), em cada dia da free
 * agency e no avanço da temporada: todos os expostos do mundo da carreira passam pela fila ({@link WaiverClaimPolicy}); sem
 * reivindicação, quem saiu dos 53 rumo ao practice squad entra nele, com o contrato mantido (movimento {@code WAIVER_CLEARED}), e quem foi
 * dispensado vira agente livre (movimento {@code WAIVER_UNCLAIMED}, com a franquia que dispensou; o dead money dela não muda).
 */
@Service
public class WaiverWire {
  private final PlayerRepository players; private final RosterMoveRepository moves; private final WaiverClaimPolicy claims; private final AuditService audit;

  public WaiverWire(PlayerRepository players, RosterMoveRepository moves, WaiverClaimPolicy claims, AuditService audit) {
    this.players = players; this.moves = moves; this.claims = claims; this.audit = audit;
  }

  /** Resolve os expostos a waivers ao fim do período; devolve quantos entraram no practice squad da franquia que os expôs. */
  public int resolve(CareerSave save, Season season, int week) {
    List<Player> exposed = new ArrayList<>(players.findByWorldIdAndRosterStatus(save.getWorld().getId(), RosterStatus.PRACTICE_SQUAD_WAIVERS));
    exposed.addAll(players.findByWorldIdAndRosterStatus(save.getWorld().getId(), RosterStatus.WAIVED));
    exposed.sort(Comparator.comparing(Player::getName).thenComparing(p -> p.getId().toString()));
    if (exposed.isEmpty()) return 0;
    Set<UUID> claimed = claims.claim(save, season, week, exposed);
    int cleared = 0;
    for (Player p : exposed) {
      if (claimed.contains(p.getId())) continue;
      RosterStatus from = p.getRosterStatus();
      Club club = p.getClub();
      if (from == RosterStatus.PRACTICE_SQUAD_WAIVERS) {
        p.setRosterStatus(RosterStatus.PRACTICE_SQUAD);
        cleared++;
      } else {
        p.setRosterStatus(RosterStatus.FREE_AGENT);
        club = releasingClub(save, p);
      }
      players.save(p);
      RosterMove m = new RosterMove();
      m.setCareer(save); m.setSeason(season); m.setPlayer(p); m.setClub(club); m.setWeek(week);
      m.setKind(from == RosterStatus.PRACTICE_SQUAD_WAIVERS ? RosterMoveKind.WAIVER_CLEARED : RosterMoveKind.WAIVER_UNCLAIMED);
      m.setFromStatus(from); m.setToStatus(p.getRosterStatus());
      moves.save(m);
      audit.ok(AuditActions.WAIVER_CLEARED, "Player", p.getId(), from == RosterStatus.PRACTICE_SQUAD_WAIVERS
          ? "sem reivindicação: practice squad (semana " + week + ")" : "sem reivindicação: agente livre (semana " + week + ")");
    }
    return cleared;
  }

  /** Franquia que dispensou (o último RELEASE do jogador na carreira). */
  private Club releasingClub(CareerSave save, Player p) {
    Club out = null;
    for (RosterMove m : moves.findByCareerIdAndPlayerIdOrderByCreatedAtAsc(save.getId(), p.getId())) if (m.getKind() == RosterMoveKind.RELEASE) out = m.getClub();
    return out;
  }
}
