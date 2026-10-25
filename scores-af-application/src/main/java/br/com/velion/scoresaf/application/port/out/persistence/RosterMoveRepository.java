package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.career.RosterMove;
import java.util.List;
import java.util.UUID;

/** Movimentos do elenco da carreira (RF4). */
public interface RosterMoveRepository extends BaseRepository<RosterMove, UUID> {
  List<RosterMove> findByCareerIdAndSeasonIdOrderByCreatedAtAsc(UUID careerId, UUID seasonId);
  List<RosterMove> findByCareerIdAndPlayerIdOrderByCreatedAtAsc(UUID careerId, UUID playerId);
  List<RosterMove> findByCareerIdOrderByCreatedAtAsc(UUID careerId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
