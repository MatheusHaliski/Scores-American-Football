package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.season.Season;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fila de waivers (Q-23, D-78; bloco 7a, D-137: {@code service.market.WaiverQueue}, que substitui o {@code NoWaiverClaims} provisório).
 * No fim do período (fechamento da semana, dia da free agency ou avanço da temporada), o {@link WaiverWire} entrega todos os expostos
 * — dispensados (WAIVED) e os que saíram dos 53 rumo ao practice squad (PRACTICE_SQUAD_WAIVERS) — e a fila decide, na ordem inversa da
 * classificação, quem leva cada um (as reivindicações do usuário e a necessidade e o teto das franquias da IA), move o jogador com o
 * contrato e grava o movimento, a transação e a auditoria. Quem não for reivindicado volta ao {@link WaiverWire}.
 */
public interface WaiverClaimPolicy {
  /** Ids dos expostos reivindicados (já movidos para a franquia que os levou). */
  Set<UUID> claim(CareerSave save, Season season, int week, List<Player> exposed);
}
