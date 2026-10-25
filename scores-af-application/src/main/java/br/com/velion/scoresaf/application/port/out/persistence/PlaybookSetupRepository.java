package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.match.PlaybookSetup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Playbooks salvos (RF61). */
public interface PlaybookSetupRepository extends BaseRepository<PlaybookSetup, UUID>, FlushingRepository<PlaybookSetup> {
  Optional<PlaybookSetup> findByCareerIdAndClubId(UUID careerId, UUID clubId);
  List<PlaybookSetup> findByCareerId(UUID careerId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
