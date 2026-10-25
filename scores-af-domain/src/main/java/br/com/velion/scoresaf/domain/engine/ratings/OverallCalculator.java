package br.com.velion.scoresaf.domain.engine.ratings;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import br.com.velion.scoresaf.domain.shared.Position;
import java.util.EnumMap;
import java.util.Map;

/**
 * Overall por posição (docs/01 §3.4): média ponderada dos subatributos com os pesos oficiais de {@link OverallWeights} (versão
 * corrente {@link #CURRENT}). O mesmo jogador tem um overall em cada posição; não há penalidade fixa por jogar fora de posição — os
 * pesos de outra posição já valorizam outras coisas — mas o card avisa ({@link PositionRating#outOfPosition()}).
 *
 * <p>Conta inteira: {@code (Σ peso × valor + 500) / 1000}, com pesos em milésimos que somam 1000. Valores fora de [0, 100] são
 * limitados antes. O port do front faz exatamente a mesma conta.
 */
public final class OverallCalculator {
  public static final OverallWeights CURRENT = OverallWeights.V1;

  private OverallCalculator() {}

  public static int overall(PlayerAttributes attributes, Position position) { return overall(attributes, position, CURRENT); }

  public static int overall(PlayerAttributes attributes, Position position, OverallWeights weights) {
    if (attributes == null || position == null) return 0;
    return weighted(attributes, weights.of(position));
  }

  /** Overall em todas as posições, na ordem de {@link Position}. */
  public static Map<Position, Integer> overallByPosition(PlayerAttributes attributes) {
    Map<Position, Integer> m = new EnumMap<>(Position.class);
    for (Position p : Position.values()) m.put(p, overall(attributes, p));
    return m;
  }

  /** Avaliação completa na posição {@code playing} para um jogador de posição natural {@code natural} (secundária opcional). */
  public static PositionRating rate(PlayerAttributes attributes, Position natural, Position secondary, Position playing) {
    int ovr = overall(attributes, playing);
    boolean out = natural != null && playing != natural && playing != secondary;
    return new PositionRating(playing, ovr, CardTier.of(ovr), MacroRatings.of(attributes, playing.family()), out, CURRENT.version());
  }

  public static PositionRating rate(PlayerAttributes attributes, Position natural) { return rate(attributes, natural, null, natural); }

  /** Média ponderada inteira com pesos em milésimos (soma 1000), arredondada para cima a partir de ,5. */
  static int weighted(PlayerAttributes a, Map<AttributeKey, Integer> weights) {
    long sum = 0;
    for (AttributeKey k : AttributeKey.values()) {
      Integer w = weights.get(k);
      if (w != null) sum += (long) w * PlayerAttributes.clamp(a.get(k));
    }
    return (int) ((sum + OverallWeights.SCALE / 2) / OverallWeights.SCALE);
  }
}
