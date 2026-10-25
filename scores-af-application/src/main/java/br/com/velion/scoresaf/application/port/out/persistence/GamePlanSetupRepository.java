package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.match.GamePlanSetup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Planos de jogo salvos (RF61). */
public interface GamePlanSetupRepository extends BaseRepository<GamePlanSetup, UUID>, FlushingRepository<GamePlanSetup> {
  Optional<GamePlanSetup> findByCareerIdAndClubId(UUID careerId, UUID clubId);
  List<GamePlanSetup> findByCareerId(UUID careerId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
