package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Player;
import org.springframework.stereotype.Component;

/** Até a fila de waivers do bloco 7, a IA não reivindica ninguém: todo exposto entra no practice squad no avanço da semana (Q-23). */
@Component
public class NoWaiverClaims implements WaiverClaimPolicy {
  @Override
  public boolean tryClaim(CareerSave save, Player exposed) { return false; }
}
