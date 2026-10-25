package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.game.Ruleset;
import br.com.velion.scoresaf.domain.shared.OvertimeFormat;

/**
 * Cópia imutável das regras de {@link LeagueRules} que o motor usa (RF42), congelada no início da partida: editar a liga depois não
 * muda um jogo em andamento, e o {@link #version} fica gravado no resultado. O campo tem sempre 100 jardas mais as end zones; o
 * motor não lê {@code fieldLengthYards} (D-31).
 */
public record RulesSnapshot(
    int version,
    Ruleset ruleset,
    int quarterMinutes,
    int playClockSeconds,
    int playClockAfterStoppageSeconds,
    boolean twoMinuteWarning,
    int timeoutsPerHalf,
    boolean clockStopsOnFirstDown,
    OvertimeFormat overtimeFormat,
    int overtimeMinutes,
    int overtimeStartYardLine,
    boolean regularSeasonTiesAllowed,
    int downs,
    int yardsToGain,
    int pointsTouchdown,
    int pointsExtraPoint,
    int pointsTwoPoint,
    int pointsFieldGoal,
    int pointsSafety,
    int patYardLine,
    int twoPointYardLine,
    int kickoffYardLine,
    int touchbackYardLine,
    boolean oneFootInbounds) {

  public static RulesSnapshot of(LeagueRules r, Ruleset ruleset) {
    return new RulesSnapshot(r.getVersion(), ruleset == null ? Ruleset.PRO : ruleset, r.getQuarterMinutes(), r.getPlayClockSeconds(),
        r.getPlayClockAfterStoppageSeconds(), r.isTwoMinuteWarning(), r.getTimeoutsPerHalf(), r.isClockStopsOnFirstDown(), r.getOvertimeFormat(),
        r.getOvertimeMinutes(), r.getOvertimeStartYardLine(), r.isRegularSeasonTiesAllowed(), r.getDowns(), r.getYardsToGain(),
        r.getPointsTouchdown(), r.getPointsExtraPoint(), r.getPointsTwoPoint(), r.getPointsFieldGoal(), r.getPointsSafety(), r.getPatYardLine(),
        r.getTwoPointYardLine(), r.getKickoffYardLine(), r.getTouchbackYardLine(), r.isOneFootInbounds());
  }

  public static RulesSnapshot pro() { return of(LeagueRules.pro(), Ruleset.PRO); }

  public static RulesSnapshot college() { return of(LeagueRules.college(), Ruleset.COLLEGE); }

  public int quarterSeconds() { return quarterMinutes * 60; }

  public int overtimeSeconds() { return overtimeMinutes * 60; }

  /** Prorrogação com posses alternadas e sem relógio (COLLEGE). */
  public boolean alternatingOvertime() { return overtimeFormat == OvertimeFormat.ALTERNATING_POSSESSIONS; }
}
