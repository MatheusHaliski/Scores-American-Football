package br.com.velion.scoresaf.application.usecase.support;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.common.security.CareerAccess;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.shared.EmploymentStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Apoio comum aos casos de uso da carreira (como no basquete): dono do save ({@link CareerAccess}), carreira jogável (ARCHIVED é
 * só leitura), franquia do usuário, regras da liga, temporada e jogadores do mundo da carreira.
 */
@Component
public class CareerSupport {
  private final CareerAccess access;
  private final PlayerRepository players;

  public CareerSupport(CareerAccess access, PlayerRepository players) { this.access = access; this.players = players; }

  /** Save do dono (404 inexistente, 403 de outro usuário com auditoria). */
  public CareerSave owned(UUID careerId, UUID userId) { return access.requireOwner(careerId, userId); }

  /** Save do dono e ACTIVE (arquivada é só leitura até reativar, RF11). */
  public CareerSave writable(UUID careerId, UUID userId) {
    CareerSave s = owned(careerId, userId);
    if (!s.isActive()) throw new BusinessRuleException("CAREER_ARCHIVED", "Carreira arquivada: reative-a para fazer alterações");
    return s;
  }

  public Club userClub(CareerSave save) {
    if (save.getEmploymentStatus() != EmploymentStatus.EMPLOYED || save.getClub() == null) {
      throw new BusinessRuleException("CAREER_UNEMPLOYED", "Você está sem franquia no momento");
    }
    return save.getClub();
  }

  public static LeagueRules rules(CareerSave save) {
    League l = save.getLeague();
    if (l == null) return LeagueRules.pro();
    return l.getRules() == null ? LeagueRules.defaults(l.getRuleset()) : l.getRules();
  }

  public static int seasonYear(CareerSave save) { return save.getSeason() != null ? save.getSeason().getYear() : save.getWorld().getBaseYear(); }

  /** Jogador do mundo da carreira (de qualquer franquia ou livre); fora do mundo responde 404, sem revelar outras carreiras. */
  public Player worldPlayer(CareerSave save, UUID playerId) {
    return players.findById(playerId).filter(p -> p.getWorld() != null && p.getWorld().getId().equals(save.getWorld().getId()))
        .orElseThrow(() -> new NotFoundException("Jogador", playerId));
  }

  /** Jogador da franquia do usuário (53, practice squad ou IR). */
  public Player clubPlayer(CareerSave save, UUID playerId) {
    Club club = userClub(save);
    Player p = worldPlayer(save, playerId);
    if (p.getClub() == null || !p.getClub().getId().equals(club.getId())) {
      throw new BusinessRuleException("PLAYER_NOT_IN_ROSTER", "O jogador não está no elenco da sua franquia");
    }
    return p;
  }
}
