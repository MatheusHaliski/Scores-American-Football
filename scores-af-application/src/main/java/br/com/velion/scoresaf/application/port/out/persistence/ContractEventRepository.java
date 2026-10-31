package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.market.ContractEvent;
import java.util.List;
import java.util.UUID;

/** Transações de contrato da carreira (bloco 7a). */
public interface ContractEventRepository extends BaseRepository<ContractEvent, UUID> {
  List<ContractEvent> findByCareerIdOrderByCreatedAtAsc(UUID careerId);
  List<ContractEvent> findBySeasonIdOrderByCreatedAtAsc(UUID seasonId);
  boolean existsBySeasonIdAndClubIdAndKind(UUID seasonId, UUID clubId, ContractEvent.Kind kind);
  List<ContractEvent> findBySeasonIdAndClubIdAndKind(UUID seasonId, UUID clubId, ContractEvent.Kind kind);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
