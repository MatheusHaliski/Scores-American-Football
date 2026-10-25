package br.com.velion.scoresaf.domain.engine.sim;

import static br.com.velion.scoresaf.domain.catalog.AttributeKey.*;

import br.com.velion.scoresaf.domain.engine.model.DepthSlot;
import br.com.velion.scoresaf.domain.engine.model.EventKeys;
import br.com.velion.scoresaf.domain.engine.model.PlayResult;
import br.com.velion.scoresaf.domain.engine.model.PlayType;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Times especiais (base do RF63): kickoff (touchback na linha das regras), onside, free kick depois do safety, punt (touchback,
 * fair catch, bola morta, retorno, bloqueio, muff), field goal pela distância e ponto extra. A unidade de cada chute vem do depth
 * chart (K, P, LS, H, KR, PR); a estratégia configurável do RF63 entra no bloco 6.
 */
final class KickResolver {
  private KickResolver() {}

  /** Cobertura de chute: LBs e safeties reservas (os 4 primeiros da segunda linha do elenco). */
  private static List<PlayerState> coverageUnit(TeamState t) {
    List<PlayerState> out = new ArrayList<>();
    for (DepthSlot s : new DepthSlot[] {DepthSlot.OLB, DepthSlot.MLB, DepthSlot.SS, DepthSlot.FS, DepthSlot.CB}) {
      List<PlayerState> l = t.depth.get(s);
      if (l.size() > 1) out.add(l.get(1));
      else if (!l.isEmpty()) out.add(l.get(0));
    }
    if (out.isEmpty()) out.add(t.players.get(0));
    return out;
  }

  private static PlayerState coverageTackler(List<PlayerState> unit, SplittableRandom r) { return unit.get(r.nextInt(unit.size())); }

  // ---------------------------------------------------------------- kickoff
  static Outcome kickoff(LiveMatch m, TeamState kicker, TeamState receiver, SplittableRandom r) {
    Outcome out = new Outcome();
    out.type = PlayType.KICKOFF;
    PlayerState k = kicker.special(DepthSlot.K);
    out.named("kicker", k);
    double kp = k.a(KICK_POWER);
    double tb = Rng.clamp(0.58 + (kp - 78) * 0.012 + m.setup.weather().kickRange() * 0.02, 0.15, 0.92);
    if (r.nextDouble() < tb) {
      out.result = PlayResult.TOUCHBACK;
      out.key = EventKeys.KICKOFF_TOUCHBACK;
      out.newYd = m.rules.touchbackYardLine();
      out.seconds = 0;
      return out;
    }
    double catchAt = Rng.gaussian(r, 4 - (kp - 78) * 0.15 - m.setup.weather().kickRange() * 0.5, 4, -5, 15);
    returnKick(m, out, kicker, receiver, catchAt, 22.5, r);
    return out;
  }

  /** Free kick do próprio 20 depois do safety (chute de punt, sem touchback provável). */
  static Outcome safetyKick(LiveMatch m, TeamState kicker, TeamState receiver, SplittableRandom r) {
    Outcome out = new Outcome();
    out.type = PlayType.FREE_KICK;
    PlayerState p = kicker.special(DepthSlot.P);
    out.named("kicker", p);
    double dist = Rng.gaussian(r, 44 + (p.a(KICK_POWER) - 78) * 0.15, 6, 25, 65);
    double catchAt = 100 - (EngineConstants.SAFETY_KICK_YARD_LINE + dist);
    if (catchAt <= 0) {
      out.result = PlayResult.TOUCHBACK;
      out.key = EventKeys.KICKOFF_TOUCHBACK;
      out.newYd = m.rules.touchbackYardLine();
      out.seconds = 0;
      return out;
    }
    returnKick(m, out, kicker, receiver, catchAt, 10, r);
    out.key = out.returnTd ? EventKeys.KICKOFF_RETURN_TD : out.kickingTeamKeeps ? EventKeys.KICKOFF_FUMBLE : EventKeys.FREE_KICK_RETURN;
    return out;
  }

  static Outcome onside(LiveMatch m, TeamState kicker, TeamState receiver, SplittableRandom r) {
    Outcome out = new Outcome();
    out.type = PlayType.ONSIDE_KICK;
    PlayerState k = kicker.special(DepthSlot.K);
    out.named("kicker", k);
    int spot = m.rules.kickoffYardLine() + 11; // relativo a quem chuta
    out.seconds = 3;
    if (r.nextDouble() < 0.12 + (k.a(KICK_ACCURACY) - 78) * 0.002) {
      out.result = PlayResult.ONSIDE_RECOVERED;
      out.key = EventKeys.ONSIDE_RECOVERED;
      out.kickingTeamKeeps = true;
      out.newYd = spot;
    } else {
      out.result = PlayResult.ONSIDE_LOST;
      out.key = EventKeys.ONSIDE_LOST;
      out.newYd = 100 - spot;
    }
    return out;
  }

  /**
   * Retorno de kickoff ou free kick a partir de {@code catchAt} (jardas da linha do gol de quem recebe). Retorno pela nota do KR contra
   * a cobertura, com chance de escapada; fumble perdido devolve a bola a quem chutou.
   */
  private static void returnKick(LiveMatch m, Outcome out, TeamState kicker, TeamState receiver, double catchAt, double mean, SplittableRandom r) {
    PlayerState kr = receiver.special(DepthSlot.KR);
    out.named("returner", kr);
    List<PlayerState> unit = coverageUnit(kicker);
    double e = (Ratings.returner(kr) - Ratings.avg(unit, Ratings::tackling)) / EngineConstants.EDGE_SCALE;
    double ret = Rng.gaussian(r, mean + 2.0 * e, 6.5, 0, 45);
    if (r.nextDouble() < Rng.clamp(0.011 + 0.004 * e, 0.003, 0.03)) ret += Rng.exponential(r, 35);
    int start = (int) Math.round(Math.max(catchAt, 0));
    int spot = (int) Math.round(catchAt + ret);
    int retYds = spot - (int) Math.round(catchAt);
    out.seconds = (int) Math.round(Rng.gaussian(r, 6, 1.5, 4, 12));
    out.credit(kr, Stat.KR, 1);
    out.credit(kr, Stat.KR_YDS, retYds);
    out.credit(kr, Stat.RET_LONG, retYds);
    out.param("yards", retYds);
    out.result = PlayResult.KICK_RETURNED;
    if (spot >= 100) {
      out.returnTd = true;
      out.result = PlayResult.TOUCHDOWN;
      out.key = EventKeys.KICKOFF_RETURN_TD;
      out.credit(kr, Stat.RET_TD, 1);
      out.newYd = 100;
      return;
    }
    spot = Math.max(spot, 1);
    PlayerState t = coverageTackler(unit, r);
    out.named("tackler", t);
    out.credit(t, Stat.TACKLES, 1);
    if (r.nextDouble() < 0.007 * m.setup.weather().fumbleFactor()) {
      out.credit(kr, Stat.FUMBLES, 1);
      out.credit(t, Stat.FORCED_FUMBLES, 1);
      if (r.nextDouble() < 0.6) {
        out.credit(kr, Stat.FUMBLES_LOST, 1);
        out.credit(t, Stat.FUMBLE_RECOVERIES, 1);
        out.result = PlayResult.FUMBLE_LOST;
        out.key = EventKeys.KICKOFF_FUMBLE;
        out.kickingTeamKeeps = true;
        out.fumbleLost = true;
        out.newYd = 100 - spot;
        return;
      }
    }
    out.key = EventKeys.KICKOFF_RETURN;
    out.newYd = spot;
    if (start < 0) out.param("fromEndZone", true);
  }

  // ---------------------------------------------------------------- punt
  static Outcome punt(LiveMatch m, TeamState kicker, TeamState receiver, SplittableRandom r) {
    Outcome out = new Outcome();
    out.type = PlayType.PUNT;
    out.concept = PlayConcept.PUNT;
    PlayerState p = kicker.special(DepthSlot.P);
    out.named("punter", p);
    int yd = m.yd;
    out.seconds = (int) Math.round(Rng.gaussian(r, 7, 1, 5, 9));
    if (r.nextDouble() < 0.004) {
      out.result = PlayResult.PUNT_BLOCKED;
      out.key = EventKeys.PUNT_BLOCKED;
      out.credit(p, Stat.PUNTS, 1);
      int spot = yd - 8;
      if (spot <= 0) { out.returnTd = true; out.newYd = 100; return out; } // recuperado na end zone pela defesa
      out.newYd = 100 - spot;
      return out;
    }
    double gross = Rng.gaussian(r, 45.5 + (p.a(KICK_POWER) - 78) * 0.18 + m.setup.weather().kickRange(), 6.5, 25, 70);
    if (yd >= 50) gross = Math.min(gross, Rng.gaussian(r, 100 - yd - 8 + (p.a(KICK_ACCURACY) - 78) * 0.05, 5, 15, 70));
    int g = (int) Math.round(gross);
    int landing = yd + g;
    out.credit(p, Stat.PUNTS, 1);
    out.param("yards", g);
    if (landing >= 100) {
      int counted = 100 - yd;
      out.credit(p, Stat.PUNT_YDS, counted);
      out.credit(p, Stat.PUNT_LONG, counted);
      out.credit(p, Stat.PUNT_TB, 1);
      out.result = PlayResult.TOUCHBACK;
      out.key = EventKeys.PUNT_TOUCHBACK;
      out.newYd = EngineConstants.PUNT_TOUCHBACK_YARD_LINE;
      out.param("yards", counted);
      return out;
    }
    out.credit(p, Stat.PUNT_YDS, g);
    out.credit(p, Stat.PUNT_LONG, g);
    int receiveAt = 100 - landing;
    PlayerState pr = receiver.special(DepthSlot.PR);
    out.named("returner", pr);
    double fair = receiveAt <= 12 ? 0.55 : 0.30;
    double downed = receiveAt <= 12 ? 0.35 : 0.10;
    double u = r.nextDouble();
    if (u < fair) {
      out.result = PlayResult.FAIR_CATCH;
      out.key = EventKeys.PUNT_FAIR_CATCH;
      out.newYd = receiveAt;
    } else if (u < fair + downed) {
      out.result = PlayResult.PUNT_DOWNED;
      out.key = EventKeys.PUNT_DOWNED;
      out.newYd = receiveAt;
    } else if (r.nextDouble() < 0.008) {
      out.result = PlayResult.MUFFED;
      out.key = EventKeys.PUNT_MUFFED;
      out.credit(pr, Stat.FUMBLES, 1);
      out.credit(pr, Stat.FUMBLES_LOST, 1);
      out.kickingTeamKeeps = true;
      out.fumbleLost = true;
      out.newYd = landing;
      return out;
    } else {
      List<PlayerState> unit = coverageUnit(kicker);
      double e = (Ratings.returner(pr) - Ratings.avg(unit, Ratings::tackling)) / EngineConstants.EDGE_SCALE;
      double ret = Rng.gaussian(r, 8.5 + e, 6, -3, 40);
      if (r.nextDouble() < Rng.clamp(0.012 + 0.004 * e, 0.003, 0.03)) ret += Rng.exponential(r, 30);
      int retYds = (int) Math.round(ret);
      int spot = receiveAt + retYds;
      out.credit(pr, Stat.PR, 1);
      out.credit(pr, Stat.PR_YDS, retYds);
      out.credit(pr, Stat.RET_LONG, retYds);
      out.param("returnYards", retYds);
      out.seconds += 4;
      out.result = PlayResult.KICK_RETURNED;
      if (spot >= 100) {
        out.returnTd = true;
        out.result = PlayResult.TOUCHDOWN;
        out.key = EventKeys.PUNT_RETURN_TD;
        out.credit(pr, Stat.RET_TD, 1);
        out.newYd = 100;
        return out;
      }
      spot = Math.max(spot, 1);
      PlayerState t = coverageTackler(unit, r);
      out.named("tackler", t);
      out.credit(t, Stat.TACKLES, 1);
      out.key = EventKeys.PUNT_RETURN;
      out.newYd = spot;
    }
    if (out.newYd <= 20) out.credit(p, Stat.PUNT_IN20, 1);
    return out;
  }

  // ---------------------------------------------------------------- FG e PAT
  /** Chance de converter um chute de {@code distance} jardas (logística pela distância, ajustada pelo chutador e pelo clima). */
  static double makeProbability(LiveMatch m, PlayerState k, int distance) {
    double d50 = 52.5 + (k.a(KICK_ACCURACY) - 78) * 0.22 + (k.a(KICK_POWER) - 78) * 0.18 + m.setup.weather().kickRange();
    return 0.985 / (1 + Math.exp((distance - d50) / 6.5));
  }

  static Outcome fieldGoal(LiveMatch m, TeamState kicker, SplittableRandom r) {
    Outcome out = new Outcome();
    out.type = PlayType.FIELD_GOAL;
    out.concept = PlayConcept.FIELD_GOAL;
    PlayerState k = kicker.special(DepthSlot.K);
    out.named("kicker", k);
    int distance = PlayCaller.fieldGoalDistance(m.yd);
    out.param("distance", distance);
    out.yards = distance;
    out.seconds = 5;
    Stat att = distance < 40 ? Stat.FG_ATT_0_39 : distance < 50 ? Stat.FG_ATT_40_49 : Stat.FG_ATT_50;
    Stat made = distance < 40 ? Stat.FG_MADE_0_39 : distance < 50 ? Stat.FG_MADE_40_49 : Stat.FG_MADE_50;
    out.credit(k, att, 1);
    if (r.nextDouble() < 0.012) {
      out.result = PlayResult.FIELD_GOAL_BLOCKED;
      out.key = EventKeys.FIELD_GOAL_BLOCKED;
    } else if (r.nextDouble() < makeProbability(m, k, distance)) {
      out.good = true;
      out.result = PlayResult.FIELD_GOAL_GOOD;
      out.key = EventKeys.FIELD_GOAL_GOOD;
      out.credit(k, made, 1);
      out.credit(k, Stat.FG_LONG, distance);
      return out;
    } else {
      out.result = PlayResult.FIELD_GOAL_MISSED;
      out.key = EventKeys.FIELD_GOAL_MISSED;
    }
    // errado ou bloqueado: a defesa recebe no ponto do chute (7 jardas atrás da linha) ou na própria 20, o que for melhor para ela
    int spotOfKick = m.yd - 7;
    out.newYd = spotOfKick > 80 ? 20 : 100 - spotOfKick;
    return out;
  }

  static Outcome extraPoint(LiveMatch m, TeamState kicker, SplittableRandom r) {
    Outcome out = new Outcome();
    out.type = PlayType.EXTRA_POINT;
    PlayerState k = kicker.special(DepthSlot.K);
    out.named("kicker", k);
    int distance = m.rules.patYardLine() + EngineConstants.FG_EXTRA_YARDS;
    out.param("distance", distance);
    out.credit(k, Stat.PAT_ATT, 1);
    out.seconds = 0;
    if (r.nextDouble() < 0.995 * makeProbability(m, k, distance)) {
      out.good = true;
      out.result = PlayResult.EXTRA_POINT_GOOD;
      out.key = EventKeys.EXTRA_POINT_GOOD;
      out.credit(k, Stat.PAT_MADE, 1);
    } else {
      out.result = PlayResult.EXTRA_POINT_MISSED;
      out.key = EventKeys.EXTRA_POINT_MISSED;
    }
    return out;
  }
}
