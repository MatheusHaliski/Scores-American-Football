package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
import org.springframework.stereotype.Component;

/** Até o bloco 7 não há draft: a fase é pulada no avanço da temporada (D-99). */
@Component
public class NoDraft implements OffseasonPhase {
  @Override public SeasonPhase phase() { return SeasonPhase.DRAFT; }
  @Override public String run(CareerSave save, Season closing) { return "SKIPPED"; }
}
