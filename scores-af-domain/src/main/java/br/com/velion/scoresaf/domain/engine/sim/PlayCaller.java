package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.engine.model.DepthSlot;
import br.com.velion.scoresaf.domain.engine.model.EngineState.ClockState;
import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.engine.model.GamePlan.Aggression;
import br.com.velion.scoresaf.domain.engine.model.GamePlan.Tempo;
import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.Formation;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.SplittableRandom;

/**
 * Chamada de jogada da IA (RF42): o ataque pela situação (descida, distância, campo, placar, relógio) e pelo plano de jogo; a
 * defesa lendo a formação. Também decide 4ª descida (tentar, punt, FG), conversão (PAT ou 2 pontos), kneel, spike, onside e os
 * tempos técnicos.
 */
final class PlayCaller {
  private PlayCaller() {}

  enum Kind { PLAY, PUNT, FIELD_GOAL, KNEEL, SPIKE, EXTRA_POINT, TWO_POINT }

  record OffenseCall(Kind kind, PlayConcept concept, Formation formation) {
    static OffenseCall of(Kind k) {
      return switch (k) {
        case PUNT -> new OffenseCall(k, PlayConcept.PUNT, null);
        case FIELD_GOAL, EXTRA_POINT -> new OffenseCall(k, PlayConcept.FIELD_GOAL, null);
        case KNEEL -> new OffenseCall(k, PlayConcept.KNEEL, Formation.SINGLEBACK);
        case SPIKE -> new OffenseCall(k, PlayConcept.SPIKE, Formation.SHOTGUN);
        default -> throw new IllegalArgumentException(k.name());
      };
    }
  }

  record DefenseCall(DefensePackage pkg, Coverage coverage, boolean blitz, boolean spy) {}

  // ---------------------------------------------------------------- situação
  /** Alcance de FG do chutador titular, em jardas de chute. */
  static int fieldGoalRange(LiveMatch m, TeamState t) {
    PlayerState k = t.special(DepthSlot.K);
    double r = 50 + (k.a(AttributeKey.KICK_POWER) - 78) * 0.35 + (k.a(AttributeKey.KICK_ACCURACY) - 78) * 0.10 + m.setup.weather().kickRange();
    return (int) Math.round(Rng.clamp(r, 38, 60));
  }

  static int fieldGoalDistance(int yd) { return 100 - yd + EngineConstants.FG_EXTRA_YARDS; }

  /** Ataque no "two-minute drill": fim do 1º tempo ou atrás/empatado no fim do jogo. */
  static boolean hurry(LiveMatch m, TeamState off) {
    if (!m.timed()) return false;
    int d = m.diff(off);
    return (m.quarter == 2 && m.clock <= 120) || (m.quarter >= 4 && d <= 0 && m.clock <= 240) || (m.quarter >= 4 && d < -8 && m.clock <= 600);
  }

  static Tempo tempo(LiveMatch m, TeamState off) {
    if (hurry(m, off)) return Tempo.HURRY_UP;
    if (m.quarter >= 4 && m.diff(off) > 0 && m.clock <= 420) return Tempo.MILK_CLOCK;
    return off.plan.tempo();
  }

  /** Dá para ajoelhar até o fim do tempo: snaps restantes × 2 s + as corridas de 40 s que o adversário não consegue parar. */
  static boolean canKneelOut(LiveMatch m) {
    if (!m.timed() || m.phase != br.com.velion.scoresaf.domain.engine.model.EngineState.Phase.SCRIMMAGE) return false;
    TeamState off = m.offense();
    int snaps = m.rules.downs() - m.down + 1;
    int runoffs = snaps - 1 + (m.clockState == ClockState.RUNNING ? 1 : 0);
    int killable = snaps * 2 + Math.max(0, runoffs - m.defense().timeoutsLeft()) * 38;
    if (m.clock > killable) return false;
    if (m.quarter == 4 || (m.overtime() && m.quarter >= 5)) return m.diff(off) > 0;
    return m.quarter == 2 && m.yd < 55 && m.diff(off) >= -14;
  }

  // ---------------------------------------------------------------- ataque
  static OffenseCall offense(LiveMatch m, SplittableRandom r) {
    TeamState off = m.offense();
    GamePlan plan = off.plan;
    int d = m.diff(off);
    boolean late = m.quarter >= 4;
    int fgDist = fieldGoalDistance(m.yd);
    boolean inRange = fgDist <= fieldGoalRange(m, off);

    if (canKneelOut(m)) return OffenseCall.of(Kind.KNEEL);
    if (m.timed() && inRange && m.clock <= 6 && (m.quarter == 2 || (late && d >= -3 && d <= 0))) return OffenseCall.of(Kind.FIELD_GOAL);
    if (m.timed() && m.clockState == ClockState.RUNNING && off.timeoutsLeft() == 0 && m.down < 4 && m.clock <= 35 && m.clock >= 4
        && hurry(m, off) && r.nextDouble() < 0.75) {
      return OffenseCall.of(Kind.SPIKE);
    }
    if (m.down >= m.rules.downs()) {
      Kind k = fourthDown(m, off, d, inRange, r);
      if (k != Kind.PLAY) return OffenseCall.of(k);
    }
    return scrimmagePlay(m, off, plan, d, r);
  }

  private static Kind fourthDown(LiveMatch m, TeamState off, int d, boolean inRange, SplittableRandom r) {
    int dist = m.distance;
    int yd = m.yd;
    Aggression ag = off.plan.fourthDownAggression();
    if (m.collegeOvertime()) return inRange && (dist > 2 || d < 0 && d >= -3) ? Kind.FIELD_GOAL : Kind.PLAY;
    boolean late = m.quarter >= 4;
    if (late && d < 0 && (m.overtime() || m.clock <= 330)) {
      int need = -d;
      if (need <= 3 && inRange) return Kind.FIELD_GOAL;
      if (m.overtime() || m.clock <= 150 || need > 8) return Kind.PLAY;
    }
    if (late && d > 0 && m.clock <= 150) return inRange ? Kind.FIELD_GOAL : Kind.PUNT;
    if (m.overtime() && d == 0 && inRange) return Kind.FIELD_GOAL;
    double goGoalLine = switch (ag) { case AGGRESSIVE -> 0.75; case NORMAL -> 0.45; case CONSERVATIVE -> 0.2; };
    int shortFrom = switch (ag) { case AGGRESSIVE -> 32; case NORMAL -> 45; case CONSERVATIVE -> 62; };
    boolean go = false;
    if (yd + dist >= 100 && dist <= 2) go = r.nextDouble() < goGoalLine;
    else if (dist <= 1 && yd >= shortFrom) go = true;
    else if (dist <= 2 && yd >= 55 && yd < 70 && ag != Aggression.CONSERVATIVE) go = true;
    else if (dist <= 3 && yd >= 40 && yd <= 70 && ag == Aggression.AGGRESSIVE) go = true;
    else if (late && d <= -10 && dist <= 5 && yd >= 35) go = true;
    if (go) return Kind.PLAY;
    if (inRange) return Kind.FIELD_GOAL;
    if (yd >= 62 && dist <= 4) return Kind.PLAY;
    return Kind.PUNT;
  }

  private static OffenseCall scrimmagePlay(LiveMatch m, TeamState off, GamePlan plan, int d, SplittableRandom r) {
    int dist = m.distance;
    boolean hurry = hurry(m, off);
    double pass = plan.passRatio() / 100.0;
    switch (m.down) {
      case 1 -> pass -= 0.07;
      case 2 -> pass += dist >= 8 ? 0.08 : dist <= 3 ? -0.15 : 0;
      default -> pass = dist >= 7 ? 0.92 : dist >= 4 ? 0.78 : dist >= 2 ? 0.55 : 0.33;
    }
    if (m.yd + dist >= 100 && m.down <= 2) pass -= 0.12;
    if (hurry) pass += 0.22;
    if (m.quarter >= 4 && d < -8) pass += 0.15;
    if (m.quarter >= 4 && d > 0 && m.clock <= 420) pass -= 0.25;
    pass = Rng.clamp(pass, 0.10, 0.95);
    boolean goalLine = m.yd >= 97;
    if (r.nextDouble() >= pass) {
      PlayConcept c;
      if (dist <= 1 && r.nextDouble() < 0.28) c = PlayConcept.QB_SNEAK;
      else c = r.nextDouble() < 0.6 ? PlayConcept.INSIDE_RUN : PlayConcept.OUTSIDE_RUN;
      Formation f;
      if (goalLine || dist <= 1) f = r.nextDouble() < 0.7 ? Formation.GOAL_LINE : Formation.I_FORM;
      else f = new Formation[] {Formation.I_FORM, Formation.SINGLEBACK, Formation.PISTOL, Formation.SHOTGUN}[Rng.pick(r, 35, 35, 15, 15)];
      return new OffenseCall(Kind.PLAY, c, f);
    }
    double[] w;
    if (hurry) w = new double[] {5, 40, 35, 20, 0};
    else if (m.down >= 3 && dist >= 8) w = new double[] {7, 25, 45, 22, 1};
    else if (m.down >= 3) w = new double[] {8, 55, 30, 6, 1};
    else w = new double[] {8, 40, 24, 13, m.down == 1 ? 18 : 10};
    if (m.yd >= 85) { w[3] = 0; w[4] *= 0.5; }
    PlayConcept c = new PlayConcept[] {PlayConcept.SCREEN, PlayConcept.SHORT_PASS, PlayConcept.MEDIUM_PASS, PlayConcept.DEEP_PASS, PlayConcept.PLAY_ACTION}[Rng.pick(r, w)];
    Formation f = c == PlayConcept.PLAY_ACTION
        ? new Formation[] {Formation.I_FORM, Formation.SINGLEBACK, Formation.PISTOL}[Rng.pick(r, 40, 40, 20)]
        : new Formation[] {Formation.SHOTGUN, Formation.SINGLEBACK, Formation.EMPTY, Formation.PISTOL, Formation.I_FORM}[Rng.pick(r, 55, 18, 10, 10, 7)];
    return new OffenseCall(Kind.PLAY, c, f);
  }

  /** PAT ou 2 pontos (tabela de conversão pela diferença depois do TD, só no 4º quarto; obrigatório na prorrogação COLLEGE a partir da 2ª rodada). */
  static OffenseCall conversion(LiveMatch m, SplittableRandom r) {
    boolean forcedTwo = m.collegeOvertime() && m.collegeRound >= 2;
    int margin = m.diff(m.offense());
    boolean chart = m.quarter >= 4 && switch (margin) { case -16, -12, -10, -9, -5, -2, 1, 5, 12 -> true; default -> false; };
    if (!forcedTwo && !chart && r.nextDouble() >= 0.02) return new OffenseCall(Kind.EXTRA_POINT, PlayConcept.FIELD_GOAL, null);
    return twoPoint(r.nextDouble() < 0.45);
  }

  static OffenseCall twoPoint(boolean run) {
    return run ? new OffenseCall(Kind.TWO_POINT, PlayConcept.INSIDE_RUN, Formation.GOAL_LINE) : new OffenseCall(Kind.TWO_POINT, PlayConcept.SHORT_PASS, Formation.SHOTGUN);
  }

  // ---------------------------------------------------------------- defesa
  static DefenseCall defense(LiveMatch m, OffenseCall o, SplittableRandom r) {
    TeamState def = m.defense();
    GamePlan plan = def.plan;
    Formation f = o.formation() == null ? Formation.SINGLEBACK : o.formation();
    int dist = m.distance;
    boolean longYardage = m.down >= 3 && dist >= 7;
    DefensePackage pkg;
    if (f == Formation.GOAL_LINE || m.yd >= 97) pkg = DefensePackage.GOAL_LINE;
    else if (f == Formation.EMPTY || longYardage) pkg = (dist >= 10 || plan.defensiveFocus() == GamePlan.DefensiveFocus.STOP_PASS) && r.nextDouble() < 0.6 ? DefensePackage.DIME : DefensePackage.NICKEL;
    else if (f == Formation.SHOTGUN) pkg = r.nextDouble() < 0.6 ? DefensePackage.NICKEL : plan.baseDefense();
    else if (f == Formation.I_FORM) pkg = plan.baseDefense();
    else pkg = r.nextDouble() < (plan.defensiveFocus() == GamePlan.DefensiveFocus.STOP_RUN ? 0.8 : 0.6) ? plan.baseDefense() : DefensePackage.NICKEL;
    double blitzP = switch (plan.blitzFrequency()) { case LOW -> 0.15; case NORMAL -> 0.25; case HIGH -> 0.38; };
    if (m.down >= 3) blitzP += 0.06;
    if (pkg == DefensePackage.GOAL_LINE) blitzP += 0.10;
    boolean blitz = r.nextDouble() < blitzP;
    Coverage cov;
    if (blitz && r.nextDouble() < 0.6) cov = r.nextDouble() < 0.3 ? Coverage.COVER_0 : Coverage.COVER_1;
    else if (pkg == DefensePackage.GOAL_LINE) cov = new Coverage[] {Coverage.COVER_0, Coverage.COVER_1, Coverage.COVER_3}[Rng.pick(r, 30, 45, 25)];
    else if (longYardage || (m.quarter >= 4 && m.diff(def) > 0 && m.clock <= 150)) cov = new Coverage[] {Coverage.COVER_1, Coverage.COVER_2, Coverage.COVER_3, Coverage.COVER_4, Coverage.COVER_6}[Rng.pick(r, 12, 24, 20, 26, 18)];
    else cov = new Coverage[] {Coverage.COVER_0, Coverage.COVER_1, Coverage.COVER_2, Coverage.COVER_3, Coverage.COVER_4, Coverage.COVER_6}[Rng.pick(r, 3, 20, 18, 32, 15, 12)];
    PlayerState qb = m.offense().first(DepthSlot.QB, java.util.List.of());
    boolean spy = qb != null && qb.raw(AttributeKey.SPEED) >= 75 && r.nextDouble() < 0.2;
    return new DefenseCall(pkg, cov, blitz, spy);
  }

  // ---------------------------------------------------------------- times especiais e relógio
  /** Onside quando atrás por até 16 pontos nos 4 minutos finais. */
  static boolean wantsOnside(LiveMatch m, TeamState kicker) {
    int d = m.diff(kicker);
    return m.timed() && m.quarter >= 4 && m.clock <= 240 && d < 0 && d >= -16;
  }

  /** Quem pede tempo técnico antes do snap para não deixar o relógio correr ({@code null} = ninguém). */
  static TeamState timeoutBeforeSnap(LiveMatch m) {
    if (!m.timed()) return null;
    TeamState off = m.offense(), def = m.defense();
    int dDef = m.diff(def);
    if (m.quarter >= 4 && def.timeoutsLeft() > 0 && dDef < 0 && dDef >= -16 && m.clock <= (dDef >= -8 ? 170 : 300) && !canKneelOut(m)) return def;
    int dOff = m.diff(off);
    if (off.timeoutsLeft() > 0) {
      if (m.quarter == 2 && m.clock <= 50 && m.yd >= 40) return off;
      if (m.quarter >= 4 && dOff <= 0 && dOff >= -16 && m.clock <= 100) return off;
    }
    return null;
  }

  /** O ataque aceita deixar o tempo acabar sem jogar (está na frente, ou longe do campo de ataque no fim do 1º tempo). */
  static boolean happyToExpire(LiveMatch m, boolean userForcedPlay) {
    if (userForcedPlay) return false;
    if (m.quarter == 1 || m.quarter == 3) return true;
    TeamState off = m.offense();
    if (m.quarter == 2) return m.yd < 55;
    return m.diff(off) > 0;
  }
}
