package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.market.WaiverClaim;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reivindicações pendentes do usuário em waivers (bloco 7a). */
public interface WaiverClaimRepository extends BaseRepository<WaiverClaim, UUID> {
  List<WaiverClaim> findByCareerIdOrderByCreatedAtAsc(UUID careerId);
  Optional<WaiverClaim> findByCareerIdAndPlayerIdAndClubId(UUID careerId, UUID playerId, UUID clubId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
