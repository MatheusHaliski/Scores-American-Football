package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.match.MatchSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Partidas ao vivo do usuário (RF27/RF29/RF62). */
public interface MatchSessionRepository extends BaseRepository<MatchSession, UUID>, FlushingRepository<MatchSession> {
  Optional<MatchSession> findByFixtureId(UUID fixtureId);
  List<MatchSession> findByCareerIdOrderByCreatedAtAsc(UUID careerId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
