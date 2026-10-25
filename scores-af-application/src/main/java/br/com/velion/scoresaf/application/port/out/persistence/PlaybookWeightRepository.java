package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.match.PlaybookWeight;
import java.util.List;
import java.util.UUID;

/** Pesos dos playbooks salvos (RF61). */
public interface PlaybookWeightRepository extends BaseRepository<PlaybookWeight, UUID> {
  List<PlaybookWeight> findByPlaybookId(UUID playbookId);
  /** Exclusão em lote, antes de gravar os pesos novos. */
  void deleteByPlaybookId(UUID playbookId);
  /** Exclusão em lote dos pesos de todos os playbooks da carreira. */
  void deleteAllOfCareer(UUID careerId);
}
