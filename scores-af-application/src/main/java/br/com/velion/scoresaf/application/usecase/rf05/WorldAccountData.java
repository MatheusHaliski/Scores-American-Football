package br.com.velion.scoresaf.application.usecase.rf05;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.persistence.WorldRepository;
import br.com.velion.scoresaf.application.service.WorldGraphStore;
import br.com.velion.scoresaf.application.usecase.rf46.AccountErasureHandler;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os mundos modelo do editor (RF5) na eliminação da conta (RF46 CA07, LGPD Art. 18, VI; Q-27, D-81): apaga cada mundo TEMPLATE do
 * titular, criado ou clonado no editor, com o catálogo inteiro ({@link WorldGraphStore#delete}). O mundo padrão (sem dono) nunca é
 * apagado. As carreiras de outros usuários nascidas de um desses mundos continuam: cada uma tem a própria cópia SAVE (RNF1) e só
 * perde a referência de origem (o nome de origem fica na carreira). Os mundos SAVE do titular saem com as carreiras dele
 * ({@code CareerAccountData}).
 */
@Component
public class WorldAccountData implements AccountErasureHandler {
  private final WorldRepository worlds;
  private final WorldGraphStore graph;
  private final AuditService audit;

  public WorldAccountData(WorldRepository worlds, WorldGraphStore graph, AuditService audit) {
    this.worlds = worlds; this.graph = graph; this.audit = audit;
  }

  @Override
  @Transactional
  public void erase(UUID userId) {
    for (World w : worlds.findByOwnerUserIdAndKindOrderByCreatedAtAsc(userId, WorldKind.TEMPLATE)) {
      if (w.isDefaultTemplate()) continue;
      UUID id = w.getId();
      graph.delete(w);
      audit.ok(AuditActions.WORLD_DELETED, "World", id, "eliminação da conta " + userId);
    }
  }
}
