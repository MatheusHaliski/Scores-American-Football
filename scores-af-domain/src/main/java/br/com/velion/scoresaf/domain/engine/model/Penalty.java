package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.shared.PenaltyCode;
import java.util.UUID;

/**
 * Falta marcada no lance (RF54). {@code yards} são as jardas efetivamente aplicadas (já com a regra da metade da distância);
 * recusada, vale o resultado da jogada e {@code yards} é 0.
 */
public record Penalty(PenaltyCode code, UUID againstClubId, UUID playerId, int yards, boolean accepted, boolean automaticFirstDown,
                      boolean lossOfDown, boolean preSnap) {}
