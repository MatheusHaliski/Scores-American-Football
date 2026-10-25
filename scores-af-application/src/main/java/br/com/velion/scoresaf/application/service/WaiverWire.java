package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Fim do período de waivers no avanço da semana (Q-23, D-78), chamado pelo {@link WeekProcessor} quando a semana fecha: cada jogador
 * do mundo da carreira em {@link RosterStatus#PRACTICE_SQUAD_WAIVERS} passa pelo {@link WaiverClaimPolicy} (o gancho do bloco 7); sem
 * reivindicação, entra no practice squad da franquia que o expôs, com o contrato mantido, e o movimento {@code WAIVER_CLEARED} vai ao
 * histórico (com a franquia, na semana que fechou). A vaga do practice squad ficou reservada desde o movimento ({@code RosterRules}).
 */
@Service
public class WaiverWire {
  private final PlayerRepository players; private final RosterMoveRepository moves; private final WaiverClaimPolicy claims; private final AuditService audit;

  public WaiverWire(PlayerRepository players, RosterMoveRepository moves, WaiverClaimPolicy claims, AuditService audit) {
    this.players = players; this.moves = moves; this.claims = claims; this.audit = audit;
  }

  /** Resolve os expostos a waivers ao fim da semana {@code week}; devolve quantos entraram no practice squad. */
  public int resolve(CareerSave save, Season season, int week) {
    List<Player> exposed = players.findByWorldIdAndRosterStatus(save.getWorld().getId(), RosterStatus.PRACTICE_SQUAD_WAIVERS).stream()
        .sorted(Comparator.comparing(Player::getId)).toList();
    int cleared = 0;
    for (Player p : exposed) {
      if (claims.tryClaim(save, p)) continue;
      p.setRosterStatus(RosterStatus.PRACTICE_SQUAD);
      players.save(p);
      RosterMove m = new RosterMove();
      m.setCareer(save); m.setSeason(season); m.setPlayer(p); m.setClub(p.getClub()); m.setWeek(week); m.setKind(RosterMoveKind.WAIVER_CLEARED);
      m.setFromStatus(RosterStatus.PRACTICE_SQUAD_WAIVERS); m.setToStatus(RosterStatus.PRACTICE_SQUAD);
      moves.save(m);
      audit.ok(AuditActions.WAIVER_CLEARED, "Player", p.getId(), "sem reivindicação: practice squad (semana " + week + ")");
      cleared++;
    }
    return cleared;
  }
}
