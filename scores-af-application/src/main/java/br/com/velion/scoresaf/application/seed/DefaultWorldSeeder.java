package br.com.velion.scoresaf.application.seed;

import br.com.velion.scoresaf.application.port.out.persistence.WorldRepository;
import br.com.velion.scoresaf.application.service.WorldGraphStore;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Semente do mundo padrão por código, como no basquete (D-23): se não existe mundo TEMPLATE sem dono, gera o do
 * {@link DefaultWorldFactory} com a semente padrão e grava o grafo. Idempotente. Chamado pelo {@code SeedRunner} na subida
 * ({@code scores.seed.enabled}) e pelos testes.
 */
@Service
public class DefaultWorldSeeder {
  private static final Logger log = LoggerFactory.getLogger(DefaultWorldSeeder.class);
  private final WorldRepository worlds;
  private final WorldGraphStore store;

  public DefaultWorldSeeder(WorldRepository worlds, WorldGraphStore store) { this.worlds = worlds; this.store = store; }

  /** Grava o mundo padrão se ainda não existir; devolve o mundo padrão (novo ou existente). */
  @Transactional
  public World seedIfMissing() {
    Optional<World> existing = worlds.findFirstByKindAndOwnerUserIsNullOrderByCreatedAtAsc(WorldKind.TEMPLATE);
    if (existing.isPresent()) return existing.get();
    DefaultWorld w = new DefaultWorldFactory().build(DefaultWorldFactory.DEFAULT_SEED);
    store.persist(w);
    log.info("Semente: mundo padrão '{}' criado — {}", w.world().getName(), w.counts());
    return w.world();
  }
}
