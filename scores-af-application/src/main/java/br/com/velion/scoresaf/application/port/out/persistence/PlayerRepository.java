package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Jogadores (RF4, RF5). */
public interface PlayerRepository extends BaseRepository<Player, UUID> {
  List<Player> findByWorldId(UUID worldId);
  List<Player> findByClubIdOrderByOverallDesc(UUID clubId);
  /** Jogadores do mundo numa situação (ex.: os expostos a waivers da carreira, Q-23). */
  List<Player> findByWorldIdAndRosterStatus(UUID worldId, RosterStatus status);
  long countByWorldId(UUID worldId);
  long countByClubIdAndRosterStatusIn(UUID clubId, Collection<RosterStatus> statuses);
  boolean existsByClubIdAndJerseyNumberAndIdNot(UUID clubId, int jerseyNumber, UUID playerId);
}
