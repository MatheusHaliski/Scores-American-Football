package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.EngineState.ClockState;
import br.com.velion.scoresaf.domain.engine.model.GamePlan.Tempo;
import java.util.SplittableRandom;

/**
 * Gestão do relógio (docs/01 §3.5): quanto tempo corre entre as jogadas e se o relógio para depois de cada uma. Para: passe
 * incompleto, pontuação, troca de posse, tempo técnico, aviso dos 2 minutos, spike e bola fora de campo nos minutos finais de cada
 * tempo. Reinicia quando a bola fica pronta: falta, bola fora de campo fora dos minutos finais e, no COLLEGE, a primeira descida
 * ({@code clockStopsOnFirstDown}).
 */
final class ClockModel {
  private ClockModel() {}

  /** Segundos que correm do fim da jogada anterior até o snap. */
  static int runoff(ClockState state, Tempo tempo, SplittableRandom r) {
    return switch (state) {
      case STOPPED -> 0;
      case RUNNING -> (int) Math.round(switch (tempo) {
        case HURRY_UP -> Rng.gaussian(r, 13, 3, 7, 20);
        case NORMAL -> Rng.gaussian(r, 34.5, 2.5, 27, 40);
        case MILK_CLOCK -> Rng.gaussian(r, 38.5, 1.5, 35, 40);
      });
      case RESTART_ON_READY -> (int) Math.round(switch (tempo) {
        case HURRY_UP -> Rng.gaussian(r, 6, 2, 3, 10);
        case NORMAL -> Rng.gaussian(r, 25, 4, 15, 33);
        case MILK_CLOCK -> Rng.gaussian(r, 24, 1, 20, 25);
      });
    };
  }

  /** Situação do relógio depois de uma jogada de scrimmage que valeu. */
  static ClockState after(Outcome o, boolean firstDown, boolean penaltyAccepted, boolean possessionOrScoreChange, LiveMatch m) {
    if (possessionOrScoreChange || o.incomplete || o.type == br.com.velion.scoresaf.domain.engine.model.PlayType.SPIKE) return ClockState.STOPPED;
    if (penaltyAccepted) return ClockState.RESTART_ON_READY;
    if (o.outOfBounds) return m.lateWindow() ? ClockState.STOPPED : ClockState.RESTART_ON_READY;
    if (firstDown && m.rules.clockStopsOnFirstDown()) return ClockState.RESTART_ON_READY;
    return ClockState.RUNNING;
  }
}
