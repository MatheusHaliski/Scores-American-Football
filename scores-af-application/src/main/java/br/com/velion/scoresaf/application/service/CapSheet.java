package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Teto salarial da temporada da carreira, provisório até o CapEngine do bloco 7 (RF49): comprometido = cap hit da temporada de
 * cada jogador da franquia (53, practice squad e IR; contrato do bloco 4) + dead money das dispensas da temporada (lançado inteiro na
 * temporada, D-46). Espaço = teto da liga − comprometido.
 */
@Component
public class CapSheet {
  private final RosterMoveRepository moves;

  public CapSheet(RosterMoveRepository moves) { this.moves = moves; }

  public static long capHit(Player p, int seasonYear, LeagueRules rules) {
    PlayerContract c = p.getContract();
    return c == null ? 0 : c.capHit(c.yearIndexOf(seasonYear), rules.getBonusProrationMaxYears());
  }

  public static long deadMoneyIfCut(Player p, int seasonYear, LeagueRules rules) {
    PlayerContract c = p.getContract();
    return c == null ? 0 : c.deadMoneyIfCut(Math.max(0, c.yearIndexOf(seasonYear)), rules.getBonusProrationMaxYears());
  }

  public long deadMoney(CareerSave save) {
    if (save.getSeason() == null) return 0;
    return moves.findByCareerIdAndSeasonIdOrderByCreatedAtAsc(save.getId(), save.getSeason().getId()).stream()
        .filter(m -> m.getKind() == RosterMoveKind.RELEASE).mapToLong(RosterMove::getDeadMoney).sum();
  }

  /** {@code roster}: jogadores da franquia (todas as situações com clube). */
  public CareerDtos.CapDto of(CareerSave save, List<Player> roster) {
    LeagueRules rules = CareerSupport.rules(save);
    int year = CareerSupport.seasonYear(save);
    long hits = roster.stream().filter(p -> p.getRosterStatus().requiresClub()).mapToLong(p -> capHit(p, year, rules)).sum();
    long dead = deadMoney(save);
    long capValue = save.getLeague() == null ? 0 : save.getLeague().getSalaryCap();
    long used = hits + dead;
    return new CareerDtos.CapDto(capValue, used, dead, capValue - used);
  }
}
