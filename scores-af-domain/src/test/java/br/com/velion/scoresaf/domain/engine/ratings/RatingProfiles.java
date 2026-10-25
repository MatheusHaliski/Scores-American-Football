package br.com.velion.scoresaf.domain.engine.ratings;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import br.com.velion.scoresaf.domain.shared.Position;
import java.util.SplittableRandom;

/** Perfis de teste: especialista numa posição (os subatributos que a posição pesa em {@code high}, o resto em {@code low}). */
final class RatingProfiles {
  private RatingProfiles() {}

  static PlayerAttributes specialist(Position position, int high, int low) {
    PlayerAttributes a = PlayerAttributes.uniform(low);
    for (AttributeKey k : OverallWeights.V1.of(position).keySet()) a.with(k, high);
    return a;
  }

  /** Especialista com ruído determinístico (±{@code noise}) em todos os subatributos. */
  static PlayerAttributes noisySpecialist(Position position, int high, int low, int noise, long seed) {
    SplittableRandom rng = new SplittableRandom(seed);
    PlayerAttributes a = specialist(position, high, low);
    for (AttributeKey k : AttributeKey.values()) a.with(k, a.get(k) + rng.nextInt(-noise, noise + 1));
    return a;
  }
}
