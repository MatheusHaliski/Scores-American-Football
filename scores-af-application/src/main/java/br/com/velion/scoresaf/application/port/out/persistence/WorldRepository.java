package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Mundos do catálogo (RF5, RF34). */
public interface WorldRepository extends BaseRepository<World, UUID> {
  Optional<World> findFirstByKindAndOwnerUserIsNullOrderByCreatedAtAsc(WorldKind kind);
  boolean existsByKindAndOwnerUserIsNull(WorldKind kind);
  List<World> findByOwnerUserIdAndKindOrderByCreatedAtAsc(UUID ownerUserId, WorldKind kind);
  /** Mundos copiados de {@code sourceWorldId} (modelos clonados e mundos SAVE de carreiras), para soltar a origem antes de apagá-lo. */
  List<World> findBySourceWorldId(UUID sourceWorldId);
}
