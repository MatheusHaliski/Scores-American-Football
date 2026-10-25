package br.com.velion.scoresaf.application.common.security;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.ForbiddenException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.domain.career.CareerSave;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Regra transversal de autorização da carreira (RNF3, HU-RF3 CA14), igual à do basquete: o save só é acessível pelo dono. Id
 * inexistente responde 404; save de outro usuário responde 403 e grava {@code ACCESS_DENIED} na auditoria (RNF7). Vale para toda
 * rota {@code /api/v1/careers/{id}/**} (central, calendário, elenco, depth chart) e para os jogadores do mundo da carreira.
 */
@Component
public class CareerAccess {
  private final CareerSaveRepository saves;
  private final AuditService audit;

  public CareerAccess(CareerSaveRepository saves, AuditService audit) { this.saves = saves; this.audit = audit; }

  public CareerSave requireOwner(UUID careerId, UUID userId) {
    CareerSave save = saves.findById(careerId).orElseThrow(() -> new NotFoundException("Carreira", careerId));
    if (save.getUser() == null || !save.getUser().getId().equals(userId)) {
      audit.denied(AuditActions.ACCESS_DENIED, "CareerSave", careerId, "usuário " + userId + " não é dono da carreira");
      throw new ForbiddenException("Esta carreira pertence a outro usuário");
    }
    return save;
  }
}
