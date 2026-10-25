package br.com.velion.scoresaf.application.usecase.rf41;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.dto.MoraleDtos;
import br.com.velion.scoresaf.application.service.MoraleService;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF41 — moral do elenco e de cada jogador ({@code CareerController.morale} do basquete, que lá é a moral do manager; aqui a do elenco,
 * D-102), calculada por regras na leitura ({@link MoraleService}). A moral do head coach com a diretoria e com a liga (demissão,
 * propostas) fica para o bloco 7, com o RF8/RF37.
 */
@Service
public class GetMoraleUseCase {
  private final CareerSupport support; private final MoraleService morale;

  public GetMoraleUseCase(CareerSupport support, MoraleService morale) { this.support = support; this.morale = morale; }

  @Transactional
  public MoraleDtos.SquadMoraleDto execute(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    support.userClub(save);
    if (save.getSeason() == null) throw new BusinessRuleException("NO_SEASON", "Carreira sem temporada");
    return morale.compute(save).squad();
  }
}
