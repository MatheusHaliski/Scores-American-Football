package br.com.velion.scoresaf.domain.career;

import br.com.velion.scoresaf.domain.catalog.AttributeGroup;
import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import java.util.EnumSet;
import java.util.Set;

/**
 * Envelhecimento e progressão no avanço da temporada (RF35, D-101), puros e sem sorteio: mesma idade, mesmo overall e mesmo
 * potencial ⇒ mesma mudança, em qualquer JVM. A idade é a da temporada que terminou.
 *
 * <ul>
 *   <li>Até 24 anos: sobe rumo ao potencial, {@code min(4, ⌈(potencial − overall) / 3⌉)} em todos os subatributos (0 sem margem).</li>
 *   <li>25 a 28: sobe 1 enquanto houver pelo menos 3 de margem até o potencial; senão, estável.</li>
 *   <li>29 e 30: técnica e mental estáveis, físicos −1.</li>
 *   <li>31 a 33: físicos −2, os demais −1 (liderança e consciência +1: experiência).</li>
 *   <li>34 ou mais: físicos −3, os demais −2 (liderança e consciência +1).</li>
 * </ul>
 * Especialistas (K, P, LS) envelhecem dois anos mais devagar. Subatributos ficam em 0–100.
 */
public final class PlayerProgression {
  private PlayerProgression() {}

  private static final Set<AttributeKey> EXPERIENCE = EnumSet.of(AttributeKey.AWARENESS, AttributeKey.LEADERSHIP);

  /** Mudança de um ano: subatributos novos e o deslocamento aplicado aos físicos e aos demais. */
  public record Change(PlayerAttributes attributes, int physicalDelta, int otherDelta) {}

  /**
   * @param age idade na temporada que terminou
   * @param overall overall atual (posição natural)
   * @param potential potencial
   * @param specialist chutador, punter ou long snapper
   */
  public static Change apply(PlayerAttributes current, int age, int overall, int potential, boolean specialist) {
    int a = specialist ? age - 2 : age;
    int gap = Math.max(0, potential - overall);
    int physical, other;
    boolean veteran = false;
    if (a <= 24) { physical = other = Math.min(4, (gap + 2) / 3); }
    else if (a <= 28) { physical = other = gap >= 3 ? 1 : 0; }
    else if (a <= 30) { physical = -1; other = 0; }
    else if (a <= 33) { physical = -2; other = -1; veteran = true; }
    else { physical = -3; other = -2; veteran = true; }
    PlayerAttributes next = current.copy();
    for (AttributeKey k : AttributeKey.values()) {
      int d = k.group() == AttributeGroup.PHYSICAL ? physical : other;
      if (veteran && EXPERIENCE.contains(k)) d = 1;
      next = next.with(k, PlayerAttributes.clamp(current.get(k) + d));
    }
    return new Change(next, physical, other);
  }
}
