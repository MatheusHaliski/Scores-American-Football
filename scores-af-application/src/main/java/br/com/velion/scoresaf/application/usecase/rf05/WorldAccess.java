package br.com.velion.scoresaf.application.usecase.rf05;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ForbiddenException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.port.out.persistence.WorldRepository;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Quem lê e quem edita o catálogo de um mundo (RF5, mesmas regras do basquete). Leitura: o mundo padrão é público; os outros, só
 * do dono — ou de um ADMIN, para suporte, com auditoria (D-21). Edição: só mundos modelo do próprio usuário; o mundo padrão é só
 * leitura para todos (inclusive ADMIN: ele nasce do código e da semente) e mundos de carreira não passam pelo editor. Toda recusa
 * fica no log de auditoria (RNF7).
 */
@Component
public class WorldAccess {
  private final WorldRepository worlds;
  private final AuditService audit;

  public WorldAccess(WorldRepository worlds, AuditService audit) { this.worlds = worlds; this.audit = audit; }

  public World readable(UUID worldId, EditorActor actor) {
    World w = worlds.findById(worldId).orElseThrow(() -> new NotFoundException("Mundo", worldId));
    if (w.isDefaultTemplate() || isOwner(w, actor)) return w;
    if (actor.admin()) {
      audit.ok(AuditActions.ADMIN_WORLD_READ, "World", worldId, "admin " + actor.userId());
      return w;
    }
    audit.denied(AuditActions.ACCESS_DENIED, "World", worldId, "usuário " + actor.userId() + " não é dono do mundo");
    throw new ForbiddenException("Este mundo pertence a outro usuário");
  }

  public World editable(UUID worldId, EditorActor actor) {
    World w = worlds.findById(worldId).orElseThrow(() -> new NotFoundException("Mundo", worldId));
    if (w.isDefaultTemplate()) {
      audit.denied(AuditActions.ACCESS_DENIED, "World", worldId, "mundo padrão é só leitura (usuário " + actor.userId() + ")");
      throw new BusinessRuleException("WORLD_READ_ONLY", "O mundo padrão é somente leitura — clone-o para editar");
    }
    if (!isOwner(w, actor)) {
      audit.denied(AuditActions.ACCESS_DENIED, "World", worldId, "usuário " + actor.userId() + " tentou editar mundo alheio");
      throw new ForbiddenException("Este mundo pertence a outro usuário");
    }
    if (w.getKind() != WorldKind.TEMPLATE) throw new BusinessRuleException("WORLD_NOT_TEMPLATE", "Só mundos modelo podem ser editados");
    return w;
  }

  public static boolean isOwner(World w, EditorActor actor) {
    return w.getOwnerUser() != null && actor != null && w.getOwnerUser().getId().equals(actor.userId());
  }
}
