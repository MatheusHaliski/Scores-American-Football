package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Player;

/**
 * Gancho da reivindicação de waivers (Q-23, D-78) para o bloco 7. No avanço da semana, o {@link WaiverWire} pergunta, jogador a
 * jogador exposto a waivers rumo ao practice squad, se alguma franquia da IA o reivindica. O bloco 7 troca o {@link NoWaiverClaims}
 * por uma implementação que escolhe a franquia (prioridade de waivers, necessidade, teto), acha a vaga e o número da camisa, move o
 * jogador para ela com o contrato e grava o movimento e a auditoria; devolve {@code true} quando reivindicou. Devolvendo
 * {@code false}, o jogador entra no practice squad da franquia que o expôs, com o contrato mantido.
 */
public interface WaiverClaimPolicy {
  /** {@code true} se o jogador foi reivindicado (e já movido para a franquia que o reivindicou). */
  boolean tryClaim(CareerSave save, Player exposed);
}
