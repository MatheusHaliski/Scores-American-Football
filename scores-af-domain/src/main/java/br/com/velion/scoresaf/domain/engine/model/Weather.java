package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.shared.ClimateZone;
import br.com.velion.scoresaf.domain.shared.RoofType;
import java.util.SplittableRandom;

/**
 * Clima da partida (docs/01 §3.5). Estádio coberto ({@link RoofType#DOME}) joga sempre em {@link #INDOOR}. Os modificadores são
 * pequenos de propósito: o clima tempera o jogo, não decide sozinho.
 *
 * @param passAccuracy pontos percentuais somados à chance de passe completo
 * @param fumbleFactor multiplicador da chance de fumble
 * @param kickRange jardas somadas ao alcance e à distância dos chutes
 */
public enum Weather {
  INDOOR(0.0, 1.00, 0),
  CLEAR(0.0, 1.00, 0),
  RAIN(-0.03, 1.30, -2),
  SNOW(-0.05, 1.35, -4),
  WIND(-0.03, 1.05, -5);

  private final double passAccuracy;
  private final double fumbleFactor;
  private final int kickRange;

  Weather(double passAccuracy, double fumbleFactor, int kickRange) {
    this.passAccuracy = passAccuracy; this.fumbleFactor = fumbleFactor; this.kickRange = kickRange;
  }

  public double passAccuracy() { return passAccuracy; }
  public double fumbleFactor() { return fumbleFactor; }
  public int kickRange() { return kickRange; }

  /** Sorteio determinístico do clima pelo estádio e pela semente da partida (estádio coberto ou retrátil fechado: INDOOR). */
  public static Weather roll(RoofType roof, ClimateZone climate, long seed) {
    if (roof == RoofType.DOME) return INDOOR;
    SplittableRandom r = new SplittableRandom(seed ^ 0x5DEECE66DL);
    if (roof == RoofType.RETRACTABLE && r.nextInt(100) < 60) return INDOOR;
    int x = r.nextInt(100);
    ClimateZone z = climate == null ? ClimateZone.TEMPERATE : climate;
    return switch (z) {
      case COLD -> x < 55 ? CLEAR : x < 70 ? RAIN : x < 85 ? SNOW : WIND;
      case TEMPERATE -> x < 70 ? CLEAR : x < 85 ? RAIN : x < 88 ? SNOW : WIND;
      case WARM -> x < 78 ? CLEAR : x < 92 ? RAIN : WIND;
      case HOT -> x < 85 ? CLEAR : x < 95 ? RAIN : WIND;
    };
  }
}
