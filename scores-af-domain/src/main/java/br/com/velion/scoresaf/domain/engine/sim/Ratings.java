package br.com.velion.scoresaf.domain.engine.sim;

import static br.com.velion.scoresaf.domain.catalog.AttributeKey.*;

import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Notas compostas dos confrontos (docs/01 §3.5): bloqueio de passe × pass rush, bloqueio de corrida × defesa da corrida, rota ×
 * cobertura, corredor × tackle, chute. Cada nota é uma média ponderada dos subatributos efetivos (fadiga e moral já aplicadas) mais
 * o mando de campo; o motor compara notas dos dois lados em escala de 10 pontos.
 */
final class Ratings {
  private Ratings() {}

  static double avg(List<PlayerState> ps, ToDoubleFunction<PlayerState> f) {
    if (ps.isEmpty()) return 50;
    double s = 0;
    for (PlayerState p : ps) s += f.applyAsDouble(p);
    return s / ps.size();
  }

  static double passBlock(PlayerState p) {
    return 0.45 * p.a(PASS_BLOCK) + 0.20 * p.a(BLOCK_TECHNIQUE) + 0.15 * p.a(BLOCK_FINESSE) + 0.10 * p.a(STRENGTH) + 0.10 * p.a(AWARENESS);
  }

  static double runBlock(PlayerState p) {
    return 0.45 * p.a(RUN_BLOCK) + 0.20 * p.a(BLOCK_POWER) + 0.15 * p.a(STRENGTH) + 0.10 * p.a(BLOCK_TECHNIQUE) + 0.10 * p.a(AWARENESS);
  }

  static double rush(PlayerState p) {
    return 0.30 * p.a(FINESSE_MOVES) + 0.30 * p.a(POWER_MOVES) + 0.20 * p.a(BLOCK_SHEDDING) + 0.10 * p.a(SPEED) + 0.10 * p.a(STRENGTH);
  }

  static double runStopFront(PlayerState p) {
    return 0.35 * p.a(BLOCK_SHEDDING) + 0.20 * p.a(STRENGTH) + 0.20 * p.a(TACKLE) + 0.15 * p.a(PURSUIT) + 0.10 * p.a(PLAY_RECOGNITION);
  }

  static double runStopBacker(PlayerState p) {
    return 0.30 * p.a(TACKLE) + 0.25 * p.a(PURSUIT) + 0.25 * p.a(PLAY_RECOGNITION) + 0.10 * p.a(BLOCK_SHEDDING) + 0.10 * p.a(SPEED);
  }

  static double tackling(PlayerState p) {
    return 0.40 * p.a(TACKLE) + 0.30 * p.a(PURSUIT) + 0.20 * p.a(SPEED) + 0.10 * p.a(HIT_POWER);
  }

  static double carrier(PlayerState p) {
    return 0.22 * p.a(VISION) + 0.14 * p.a(BREAK_TACKLE) + 0.10 * p.a(JUKE) + 0.06 * p.a(SPIN) + 0.06 * p.a(STIFF_ARM) + 0.16 * p.a(SPEED)
        + 0.12 * p.a(ACCELERATION) + 0.14 * p.a(CARRYING);
  }

  static double afterCatch(PlayerState p) {
    return 0.35 * p.a(SPEED) + 0.25 * p.a(AGILITY) + 0.20 * p.a(BREAK_TACKLE) + 0.20 * p.a(JUKE);
  }

  static double mobility(PlayerState qb) { return 0.5 * qb.a(SPEED) + 0.3 * qb.a(AGILITY) + 0.2 * qb.a(AWARENESS); }

  static double accuracy(PlayerState qb, PlayConcept c) {
    return switch (c) {
      case DEEP_PASS -> 0.45 * qb.a(DEEP_ACCURACY) + 0.30 * qb.a(THROW_POWER) + 0.15 * qb.a(READ_DEFENSE) + 0.10 * qb.a(POISE);
      case MEDIUM_PASS -> 0.55 * qb.a(MEDIUM_ACCURACY) + 0.25 * qb.a(READ_DEFENSE) + 0.10 * qb.a(THROW_POWER) + 0.10 * qb.a(POISE);
      case PLAY_ACTION -> 0.45 * qb.a(MEDIUM_ACCURACY) + 0.25 * qb.a(PLAY_ACTION) + 0.20 * qb.a(READ_DEFENSE) + 0.10 * qb.a(THROW_POWER);
      default -> 0.60 * qb.a(SHORT_ACCURACY) + 0.20 * qb.a(READ_DEFENSE) + 0.10 * qb.a(POISE) + 0.10 * qb.a(AWARENESS);
    };
  }

  static double separation(PlayerState r, PlayConcept c) {
    return switch (c) {
      case DEEP_PASS -> 0.40 * r.a(DEEP_ROUTE) + 0.35 * r.a(SPEED) + 0.15 * r.a(RELEASE) + 0.10 * r.a(JUMPING);
      case MEDIUM_PASS, PLAY_ACTION -> 0.45 * r.a(MEDIUM_ROUTE) + 0.20 * r.a(RELEASE) + 0.20 * r.a(SPEED) + 0.15 * r.a(AGILITY);
      default -> 0.45 * r.a(SHORT_ROUTE) + 0.20 * r.a(RELEASE) + 0.20 * r.a(AGILITY) + 0.15 * r.a(SPEED);
    };
  }

  static double hands(PlayerState r) { return 0.6 * r.a(HANDS) + 0.4 * r.a(CATCH_IN_TRAFFIC); }

  static double coverage(PlayerState d, boolean man) {
    return man
        ? 0.50 * d.a(MAN_COVERAGE) + 0.20 * d.a(SPEED) + 0.15 * d.a(PRESS) + 0.15 * d.a(PLAY_RECOGNITION)
        : 0.50 * d.a(ZONE_COVERAGE) + 0.20 * d.a(PLAY_RECOGNITION) + 0.15 * d.a(SPEED) + 0.15 * d.a(AWARENESS);
  }

  static double returner(PlayerState p) {
    return 0.35 * p.a(KICK_RETURN) + 0.25 * p.a(SPEED) + 0.15 * p.a(ACCELERATION) + 0.15 * p.a(VISION) + 0.10 * p.a(BREAK_TACKLE);
  }

  // ---------------------------------------------------------------- unidades
  static double passProtection(Lineups.Offense o) { return avg(o.ol(), Ratings::passBlock) + o.qb().team.homeBonus; }

  /** Os quatro melhores rushers (linha defensiva e, no 3-4, os OLBs de borda); blitz soma um rusher a mais. */
  static double passRush(Lineups.Defense d, boolean blitz) {
    List<PlayerState> rushers = new ArrayList<>(d.dl());
    if (d.pkg() == DefensePackage.BASE_3_4) rushers.addAll(d.lb());
    rushers.sort(Comparator.comparingDouble(Ratings::rush).reversed());
    double r = avg(rushers.subList(0, Math.min(4, rushers.size())), Ratings::rush);
    return r + (blitz ? 5 : 0) + d.dl().get(0).team.homeBonus;
  }

  static double runBlocking(Lineups.Offense o) {
    List<PlayerState> extra = new ArrayList<>(o.te());
    if (o.fb() != null) extra.add(o.fb());
    double line = avg(o.ol(), Ratings::runBlock);
    return (extra.isEmpty() ? line : 0.82 * line + 0.18 * avg(extra, Ratings::runBlock)) + o.qb().team.homeBonus;
  }

  static double runDefense(Lineups.Defense d) {
    return 0.55 * avg(d.dl(), Ratings::runStopFront) + 0.45 * avg(d.lb().isEmpty() ? d.s() : d.lb(), Ratings::runStopBacker) + d.dl().get(0).team.homeBonus;
  }

  static double secondLevelTackling(Lineups.Defense d) {
    List<PlayerState> ps = new ArrayList<>(d.lb());
    ps.addAll(d.s());
    return avg(ps, Ratings::tackling);
  }

  static double secondaryCoverage(Lineups.Defense d, boolean man) { return avg(d.backs(), p -> coverage(p, man)) + d.cb().get(0).team.homeBonus; }
}
