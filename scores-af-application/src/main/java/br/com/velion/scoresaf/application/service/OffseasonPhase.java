package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;

/**
 * Gancho de uma fase da entressafra (D-10, D-99) para o bloco 7. O avanço da temporada ({@code POST /season/advance}) roda as fases na
 * ordem da liga profissional: renovações (os contratos que terminam viram agentes livres e ficam como pendência, D-100), free agency,
 * draft e pré-temporada. Free agency e draft ainda não existem: {@link NoFreeAgency} e {@link NoDraft} devolvem {@code SKIPPED}; o
 * bloco 7 troca cada um por uma implementação que roda a fase (propostas, contratações, escolhas do draft) e devolve {@code DONE}.
 */
public interface OffseasonPhase {
  /** A fase que este gancho cuida (FREE_AGENCY ou DRAFT). */
  SeasonPhase phase();

  /** Roda a fase para a carreira, com a temporada que está fechando; devolve DONE ou SKIPPED. */
  String run(CareerSave save, Season closing);
}
