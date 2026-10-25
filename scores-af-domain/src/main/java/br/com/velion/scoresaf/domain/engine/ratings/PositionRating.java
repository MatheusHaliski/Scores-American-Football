package br.com.velion.scoresaf.domain.engine.ratings;

import br.com.velion.scoresaf.domain.shared.Position;

/**
 * Avaliação do jogador numa posição: overall (OVR v1), nível do card, macros da família da posição e o aviso de fora de posição
 * (a posição avaliada não é a natural nem a secundária).
 */
public record PositionRating(Position position, int overall, CardTier tier, MacroRatings macros, boolean outOfPosition, String version) {}
