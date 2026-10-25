package br.com.velion.scoresaf.application.usecase.rf42;

import br.com.velion.scoresaf.domain.engine.model.DepthChart;
import java.util.UUID;

/** Depth chart salvo de uma franquia (RF60) no formato do motor; {@code null} = sem depth chart salvo (o motor deriva o automático). */
@FunctionalInterface
public interface SavedDepthCharts {
  DepthChart forClub(UUID clubId);

  SavedDepthCharts NONE = clubId -> null;
}
