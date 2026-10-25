package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.EngineState.ClockState;
import br.com.velion.scoresaf.domain.engine.model.GamePlan.Tempo;
import br.com.velion.scoresaf.domain.engine.model.RulesSnapshot;
import java.util.SplittableRandom;

/**
 * Gestão do relógio (docs/01 §3.5): quanto tempo corre entre as jogadas e se o relógio para depois de cada uma. Para: passe
 * incompleto, pontuação, troca de posse, tempo técnico, aviso dos 2 minutos, spike e bola fora de campo nos minutos finais de cada
 * tempo. Reinicia quando a bola fica pronta: falta, bola fora de campo fora dos minutos finais e, no COLLEGE, a primeira descida
 * ({@code clockStopsOnFirstDown}).
 */
final class ClockModel {
  private ClockModel() {}

  /** Relógio de jogada de referência das distribuições abaixo (40 s correndo, 25 s quando a bola fica pronta). */
  private static final double RUNNING_REFERENCE = 40;
  private static final double READY_REFERENCE = 25;

  /**
   * Segundos que correm do fim da jogada anterior até o snap. Nunca passa do relógio de jogada da liga: {@code playClockSeconds} com o
   * relógio correndo e {@code playClockAfterStoppageSeconds} quando ele reinicia com a bola pronta. As distribuições são escritas para
   * 40/25 s e escalam com o relógio configurado (uma liga de 20 s joga mais rápido, não fica sempre no limite).
   */
  static int runoff(ClockState state, Tempo tempo, RulesSnapshot rules, SplittableRandom r) {
    return switch (state) {
      case STOPPED -> 0;
      case RUNNING -> limited(switch (tempo) {
        case HURRY_UP -> Rng.gaussian(r, 13, 3, 7, 20);
        case NORMAL -> Rng.gaussian(r, 34.5, 2.5, 27, 40);
        case MILK_CLOCK -> Rng.gaussian(r, 38.5, 1.5, 35, 40);
      }, RUNNING_REFERENCE, rules.playClockSeconds());
      case RESTART_ON_READY -> limited(switch (tempo) {
        case HURRY_UP -> Rng.gaussian(r, 6, 2, 3, 10);
        case NORMAL -> Rng.gaussian(r, 22, 3, 14, 25);
        case MILK_CLOCK -> Rng.gaussian(r, 24, 1, 21, 25);
      }, READY_REFERENCE, rules.playClockAfterStoppageSeconds());
    };
  }

  private static int limited(double sampled, double reference, int playClock) {
    return (int) Math.min(playClock, Math.round(sampled * playClock / reference));
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
