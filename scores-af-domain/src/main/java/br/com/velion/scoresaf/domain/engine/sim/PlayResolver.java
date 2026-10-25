package br.com.velion.scoresaf.domain.engine.sim;

import static br.com.velion.scoresaf.domain.catalog.AttributeKey.*;

import br.com.velion.scoresaf.domain.engine.model.EventKeys;
import br.com.velion.scoresaf.domain.engine.model.PlayResult;
import br.com.velion.scoresaf.domain.engine.model.PlayType;
import br.com.velion.scoresaf.domain.engine.sim.PlayCaller.DefenseCall;
import br.com.velion.scoresaf.domain.engine.sim.PlayCaller.OffenseCall;
import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.Formation;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Resolução da jogada de scrimmage pelos confrontos (docs/01 §3.5): bloqueio de passe × pass rush decide sack e pressão; rota ×
 * cobertura e a precisão do QB decidem alvo, passe completo e interceptação; bloqueio de corrida × defesa da corrida e corredor ×
 * tackle decidem as jardas no chão. Fadiga e moral entram nos atributos efetivos ({@link PlayerState#a}), o clima na precisão e nos
 * fumbles, e o encaixe ataque × defesa (formação, pacote, cobertura, blitz, espião) nos modificadores. As constantes foram calibradas
 * pelo teste de plausibilidade contra as médias da liga profissional.
 */
final class PlayResolver {
  private PlayResolver() {}

  /** 3ª e 4ª descidas: a defesa sabe o que vem (passe completo e corrida um pouco mais difíceis, mais sacks). */
  static final double MONEY_DOWN_COMPLETION = 0.08;
  static final double MONEY_DOWN_RUN = 0.5;

  static Outcome resolve(LiveMatch m, OffenseCall oc, DefenseCall dc, Lineups.Offense o, Lineups.Defense d, boolean conversion, SplittableRandom r) {
    Outcome out = new Outcome();
    out.concept = oc.concept();
    out.formation = oc.formation();
    out.pkg = dc.pkg();
    out.coverage = dc.coverage();
    out.blitz = dc.blitz();
    switch (oc.concept()) {
      case KNEEL -> kneel(m, out, o);
      case SPIKE -> spike(out, o);
      case INSIDE_RUN, OUTSIDE_RUN, QB_SNEAK -> run(m, out, o, d, dc, r);
      default -> pass(m, out, o, d, dc, r);
    }
    if (conversion) out.penalty = null;
    else if (out.penalty == null && oc.concept() != PlayConcept.KNEEL && oc.concept() != PlayConcept.SPIKE) {
      double passEdge = (Ratings.passProtection(o) - Ratings.passRush(d, dc.blitz())) / EngineConstants.EDGE_SCALE;
      out.penalty = PenaltyModel.during(m, out, o, d, passEdge, r);
    }
    return out;
  }

  // ---------------------------------------------------------------- corrida
  private static void run(LiveMatch m, Outcome out, Lineups.Offense o, Lineups.Defense d, DefenseCall dc, SplittableRandom r) {
    out.type = PlayType.RUN;
    out.rush = true;
    PlayerState carrier = out.concept == PlayConcept.QB_SNEAK || o.rb() == null ? o.qb() : o.rb();
    double eRun = (Ratings.runBlocking(o) - Ratings.runDefense(d)) / EngineConstants.EDGE_SCALE;
    eRun += formationRun(out.formation) + packageRun(dc.pkg());
    eRun += switch (m.defense().plan.defensiveFocus()) { case STOP_RUN -> -0.35; case STOP_PASS -> 0.35; default -> 0; };
    if (dc.blitz()) eRun += r.nextDouble() < 0.5 ? -0.6 : 0.5;
    if (m.down >= 3) eRun -= MONEY_DOWN_RUN;
    double eCar = (Ratings.carrier(carrier) - Ratings.secondLevelTackling(d)) / EngineConstants.EDGE_SCALE;
    double y;
    switch (out.concept) {
      case QB_SNEAK -> y = Rng.gaussian(r, 1.7 + 0.5 * eRun, 1.3, -2, 6);
      case OUTSIDE_RUN -> {
        y = Rng.gaussian(r, 3.15 + 0.75 * eRun + 0.8 * eCar, 4.0, -8, 30);
        if (r.nextDouble() < Rng.clamp(0.055 + 0.015 * eCar, 0.02, 0.12)) y += Rng.exponential(r, 17);
      }
      default -> {
        y = Rng.gaussian(r, 3.45 + 0.95 * eRun + 0.45 * eCar, 2.8, -5, 25);
        if (r.nextDouble() < Rng.clamp(0.032 + 0.012 * eCar, 0.01, 0.09)) y += Rng.exponential(r, 15);
      }
    }
    int yards = (int) Math.round(y);
    finishCarry(m, out, carrier, d, yards, out.concept == PlayConcept.OUTSIDE_RUN ? 0.30 : 0.06, r);
  }

  /** Fim de uma corrida (também scramble e jogada após a recepção já tratada à parte): TD, safety, fumble, tackle. */
  private static void finishCarry(LiveMatch m, Outcome out, PlayerState carrier, Lineups.Defense d, int yards, double oobChance, SplittableRandom r) {
    out.carrier = carrier;
    out.named("rusher", carrier);
    yards = Math.max(yards, -m.yd);
    yards = Math.min(yards, 100 - m.yd);
    out.yards = yards;
    out.param("yards", yards);
    out.credit(carrier, Stat.RUSH_ATT, 1);
    out.credit(carrier, Stat.RUSH_YDS, yards);
    out.credit(carrier, Stat.RUSH_LONG, yards);
    out.seconds = (int) Math.round(Rng.clamp(5 + Math.abs(yards) / 8.0 + r.nextGaussian() * 0.8, 3, 12));
    if (m.yd + yards >= 100) {
      out.result = PlayResult.TOUCHDOWN;
      out.key = out.scramble ? EventKeys.SCRAMBLE : EventKeys.RUN_TD;
      out.credit(carrier, Stat.RUSH_TD, 1);
      return;
    }
    PlayerState tackler = tackler(d, out.concept == PlayConcept.OUTSIDE_RUN || yards > 12, null, r);
    out.tackler = tackler;
    out.named("tackler", tackler);
    out.credit(tackler, Stat.TACKLES, 1);
    if (yards < 0) out.credit(tackler, Stat.TFL, 1);
    if (m.yd + yards <= 0) {
      out.result = PlayResult.SAFETY;
      out.key = EventKeys.RUN_SAFETY;
      return;
    }
    double fumbleP = 0.0115 * m.setup.weather().fumbleFactor() * Math.exp(-(carrier.a(BALL_SECURITY) - 75) * 0.03) * Math.exp((tackler.a(HIT_POWER) - 70) * 0.02);
    if (r.nextDouble() < fumbleP) {
      fumble(m, out, carrier, tackler, d, yards, r);
      if (out.fumbleLost) { out.key = EventKeys.RUN_FUMBLE; return; }
    }
    out.outOfBounds = r.nextDouble() < oobChance;
    out.result = out.scramble ? PlayResult.SCRAMBLE : PlayResult.GAIN;
    out.key = out.scramble ? EventKeys.SCRAMBLE : EventKeys.RUN;
  }

  private static void fumble(LiveMatch m, Outcome out, PlayerState carrier, PlayerState forcer, Lineups.Defense d, int yards, SplittableRandom r) {
    out.fumble = true;
    out.credit(carrier, Stat.FUMBLES, 1);
    out.credit(forcer, Stat.FORCED_FUMBLES, 1);
    if (r.nextDouble() < 0.55) {
      out.fumbleLost = true;
      out.result = PlayResult.FUMBLE_LOST;
      out.credit(carrier, Stat.FUMBLES_LOST, 1);
      PlayerState rec = PenaltyModel.pick(d.all(), r);
      out.defender = rec;
      out.named("recoveredBy", rec);
      out.credit(rec, Stat.FUMBLE_RECOVERIES, 1);
      int ret = r.nextDouble() < 0.04 ? (int) Math.round(Rng.exponential(r, 30)) : (int) Math.round(Rng.gaussian(r, 1.5, 3, 0, 15));
      out.returnYards = ret;
      out.turnoverSpot = m.yd + yards - ret;
    } else {
      out.result = PlayResult.FUMBLE_RECOVERED;
    }
  }

  private static double formationRun(Formation f) {
    if (f == null) return 0;
    return switch (f) { case I_FORM -> 0.15; case GOAL_LINE -> 0.10; case PISTOL -> 0.05; case SHOTGUN -> -0.2; case EMPTY -> -0.5; default -> 0; };
  }

  private static double packageRun(DefensePackage p) {
    return switch (p) { case NICKEL -> 0.35; case DIME -> 0.75; case GOAL_LINE -> -0.6; default -> 0; };
  }

  // ---------------------------------------------------------------- passe
  private static void pass(LiveMatch m, Outcome out, Lineups.Offense o, Lineups.Defense d, DefenseCall dc, SplittableRandom r) {
    out.type = PlayType.PASS;
    PlayerState qb = o.qb();
    out.passer = qb;
    out.named("passer", qb);
    PlayConcept c = out.concept;
    double ePB = (Ratings.passProtection(o) - Ratings.passRush(d, dc.blitz())) / EngineConstants.EDGE_SCALE;
    if (out.formation == Formation.SHOTGUN || out.formation == Formation.EMPTY) ePB += 0.15;
    double mob = Ratings.mobility(qb);
    double conceptSack = switch (c) { case SCREEN -> 0.3; case SHORT_PASS -> 0.72; case MEDIUM_PASS -> 1.1; case DEEP_PASS -> 1.45; case PLAY_ACTION -> 1.3; default -> 1; };
    double escape = Rng.clamp(1.2 - (mob - 60) * 0.01, 0.75, 1.25);
    double sackP = 0.068 * conceptSack * Math.exp(-0.45 * ePB) * (dc.blitz() ? 1.4 : 1.0) * escape * (m.down >= 3 ? 1.15 : 1.0);
    if (r.nextDouble() < sackP) {
      if (r.nextDouble() < 0.06) { grounding(m, out, qb); return; }
      sack(m, out, qb, d, r);
      return;
    }
    boolean pressured = r.nextDouble() < 0.20 * Math.exp(-0.35 * ePB) * (dc.blitz() ? 1.3 : 1.0);
    if (pressured && r.nextDouble() < Rng.clamp((mob - 55) / 100.0, 0.02, 0.35) * (dc.spy() ? 0.5 : 1.0)) {
      scramble(m, out, qb, d, r);
      return;
    }
    out.passAttempt = true;
    boolean man = dc.coverage().isMan();
    List<PlayerState> recs = o.receivers();
    List<PlayerState> matched = new ArrayList<>(recs.size());
    for (int i = 0; i < recs.size(); i++) matched.add(matchup(recs.get(i), i, o, d));
    double[] w = new double[recs.size()];
    double read = 0.15 + 0.25 * qb.a(READ_DEFENSE) / 100.0;
    for (int i = 0; i < recs.size(); i++) {
      PlayerState rec = recs.get(i);
      double base = targetWeight(rec, i, o, c);
      double open = (Ratings.separation(rec, c) - Ratings.coverage(matched.get(i), man)) / EngineConstants.EDGE_SCALE;
      w[i] = base * Math.exp(read * open);
    }
    int ti = Rng.pick(r, w);
    PlayerState target = recs.get(ti);
    PlayerState cover = matched.get(ti);
    out.receiver = target;
    out.defender = cover;
    out.named("receiver", target);
    out.credit(qb, Stat.PASS_ATT, 1);
    out.credit(target, Stat.TARGETS, 1);

    double covUnit = Ratings.secondaryCoverage(d, man);
    double eQ = (Ratings.accuracy(qb, c) - covUnit) / EngineConstants.EDGE_SCALE;
    double eSep = (Ratings.separation(target, c) - Ratings.coverage(cover, man)) / EngineConstants.EDGE_SCALE;
    double eHands = (Ratings.hands(target) - 75) / 10.0; // absoluto: mãos boas pegam mais
    double air = switch (c) {
      case SCREEN -> Rng.gaussian(r, -1, 1.5, -4, 2);
      case SHORT_PASS -> Rng.gaussian(r, 5, 2.2, -1, 9);
      case MEDIUM_PASS -> Rng.gaussian(r, 12.5, 3, 8, 19);
      case DEEP_PASS -> Rng.gaussian(r, 27, 7, 18, 55);
      default -> Rng.gaussian(r, 13, 4.5, 6, 28);
    };
    int airYards = (int) Math.round(Math.min(air, 100 - m.yd));
    out.airYards = airYards;

    double intBase = switch (c) { case SCREEN -> 0.006; case SHORT_PASS -> 0.016; case MEDIUM_PASS -> 0.027; case DEEP_PASS -> 0.048; default -> 0.026; };
    double intP = intBase * (pressured ? 1.7 : 1.0) * Math.exp(-0.30 * eQ) * Math.exp(-0.15 * eSep) * (man ? 0.95 : 1.08);
    if (r.nextDouble() < intP) { interception(m, out, qb, target, cover, d, airYards, r); return; }

    double p = switch (c) { case SCREEN -> 0.865; case SHORT_PASS -> 0.795; case MEDIUM_PASS -> 0.675; case DEEP_PASS -> 0.455; default -> 0.69; };
    p += 0.050 * eQ + 0.035 * eSep + 0.012 * eHands + m.setup.weather().passAccuracy();
    p += coverageFit(c, dc);
    if (c == PlayConcept.PLAY_ACTION) p += (dc.pkg() == DefensePackage.BASE_4_3 || dc.pkg() == DefensePackage.BASE_3_4 || dc.pkg() == DefensePackage.GOAL_LINE) ? 0.04 : -0.03;
    if (m.down >= 3) p -= MONEY_DOWN_COMPLETION;
    if (pressured) p -= Rng.clamp(0.16 - (qb.a(POISE) - 75) * 0.003, 0.08, 0.24);
    if (m.yd + airYards >= 100) p -= 0.08;
    p = Rng.clamp(p, 0.05, 0.95);
    if (r.nextDouble() >= p) {
      incomplete(out, cover, r);
      return;
    }
    double dropP = Rng.clamp(0.035 - (target.a(HANDS) - 75) * 0.002, 0.01, 0.07);
    if (r.nextDouble() < dropP) {
      out.incomplete = true;
      out.result = PlayResult.INCOMPLETE;
      out.key = EventKeys.PASS_DROP;
      out.credit(target, Stat.DROPS, 1);
      out.seconds = (int) Math.round(Rng.gaussian(r, 5, 1, 3, 8));
      return;
    }
    completion(m, out, qb, target, d, c, airYards, r);
  }

  private static double coverageFit(PlayConcept c, DefenseCall dc) {
    Coverage cov = dc.coverage();
    double v = 0;
    switch (c) {
      case SHORT_PASS -> v = cov.isMan() ? 0.01 : -0.02;
      case DEEP_PASS -> v = cov == Coverage.COVER_0 ? 0.08 : cov == Coverage.COVER_1 ? 0.03 : cov.deepDefenders() >= 3 ? -0.04 : -0.01;
      case SCREEN -> v = dc.blitz() ? 0.05 : 0;
      case MEDIUM_PASS -> v = cov == Coverage.COVER_2 ? 0.02 : cov == Coverage.COVER_4 ? -0.02 : 0;
      default -> { }
    }
    if (dc.blitz() && c != PlayConcept.SCREEN) v += 0.02;
    v += switch (dc.pkg()) { case DIME -> -0.02; case NICKEL -> -0.01; case GOAL_LINE -> 0.05; default -> 0.01; };
    return v;
  }

  private static double targetWeight(PlayerState rec, int index, Lineups.Offense o, PlayConcept c) {
    boolean wr = o.wr().contains(rec);
    boolean te = o.te().contains(rec);
    int wrRank = wr ? o.wr().indexOf(rec) : 9;
    double wrW = wrRank == 0 ? 1.35 : wrRank == 1 ? 1.15 : wrRank == 2 ? 0.9 : 0.6;
    return switch (c) {
      case SCREEN -> wr ? 0.55 : te ? 0.3 : 1.6;
      case DEEP_PASS -> wr ? wrW * 1.15 : te ? 0.35 : 0.03;
      case MEDIUM_PASS, PLAY_ACTION -> wr ? wrW : te ? 0.85 : 0.18;
      default -> wr ? wrW * 0.85 : te ? 0.9 : 0.55;
    };
  }

  /** Defensor do confronto: WR1–4 × CB1–4 (sem CB, safety), TE × SS/LB, RB e FB × LB. */
  private static PlayerState matchup(PlayerState rec, int index, Lineups.Offense o, Lineups.Defense d) {
    int wrRank = o.wr().indexOf(rec);
    if (wrRank >= 0) {
      if (wrRank < d.cb().size()) return d.cb().get(wrRank);
      List<PlayerState> b = d.s().isEmpty() ? d.lb() : d.s();
      return b.get(Math.min(wrRank - d.cb().size(), b.size() - 1));
    }
    if (o.te().contains(rec)) {
      int t = o.te().indexOf(rec);
      if (t == 0 && !d.s().isEmpty()) return d.s().get(d.s().size() - 1);
      return d.lb().isEmpty() ? d.s().get(0) : d.lb().get(Math.min(t, d.lb().size() - 1));
    }
    return d.lb().isEmpty() ? d.s().get(0) : d.lb().get(d.lb().size() - 1);
  }

  private static void incomplete(Outcome out, PlayerState cover, SplittableRandom r) {
    out.incomplete = true;
    out.result = PlayResult.INCOMPLETE;
    out.seconds = (int) Math.round(Rng.gaussian(r, 5, 1, 3, 8));
    if (r.nextDouble() < 0.35) {
      out.credit(cover, Stat.PASSES_DEFENDED, 1);
      out.named("defender", cover);
      out.key = EventKeys.PASS_DEFENDED;
    } else {
      out.key = EventKeys.PASS_INCOMPLETE;
    }
  }

  private static void completion(LiveMatch m, Outcome out, PlayerState qb, PlayerState target, Lineups.Defense d, PlayConcept c, int airYards, SplittableRandom r) {
    out.completion = true;
    out.result = PlayResult.COMPLETE;
    double yacMean = switch (c) { case SCREEN -> 6.5; case SHORT_PASS -> 4.3; case MEDIUM_PASS -> 3.2; case DEEP_PASS -> 4.0; default -> 3.6; };
    double eYac = (Ratings.afterCatch(target) - Ratings.secondLevelTackling(d)) / EngineConstants.EDGE_SCALE;
    double yac = Rng.exponential(r, yacMean * 0.8 * Math.exp(0.12 * eYac));
    if (r.nextDouble() < 0.035) yac += Rng.exponential(r, 18);
    int yards = airYards + (int) Math.round(yac);
    yards = Math.max(yards, -m.yd);
    yards = Math.min(yards, 100 - m.yd);
    int yacInt = yards - airYards;
    out.yards = yards;
    out.param("yards", yards);
    out.credit(qb, Stat.PASS_CMP, 1);
    out.credit(qb, Stat.PASS_YDS, yards);
    out.credit(qb, Stat.PASS_LONG, yards);
    out.credit(target, Stat.REC, 1);
    out.credit(target, Stat.REC_YDS, yards);
    out.credit(target, Stat.YAC, yacInt);
    out.credit(target, Stat.REC_LONG, yards);
    out.seconds = (int) Math.round(Rng.clamp(5 + yards / 10.0 + r.nextGaussian() * 0.8, 4, 14));
    if (m.yd + yards >= 100) {
      out.result = PlayResult.TOUCHDOWN;
      out.key = EventKeys.PASS_TD;
      out.credit(qb, Stat.PASS_TD, 1);
      out.credit(target, Stat.REC_TD, 1);
      return;
    }
    PlayerState tackler = tackler(d, yards > 15, out.defender, r);
    out.tackler = tackler;
    out.named("tackler", tackler);
    out.credit(tackler, Stat.TACKLES, 1);
    if (m.yd + yards <= 0) { out.result = PlayResult.SAFETY; out.key = EventKeys.RUN_SAFETY; return; }
    double fumbleP = 0.0075 * m.setup.weather().fumbleFactor() * Math.exp(-(target.a(BALL_SECURITY) - 70) * 0.02);
    if (r.nextDouble() < fumbleP) {
      fumble(m, out, target, tackler, d, yards, r);
      if (out.fumbleLost) { out.key = EventKeys.PASS_FUMBLE; return; }
      out.result = PlayResult.COMPLETE;
    }
    out.outOfBounds = r.nextDouble() < (c == PlayConcept.DEEP_PASS ? 0.30 : 0.22) * (PlayCaller.hurry(m, m.offense()) ? 1.6 : 1.0);
    out.key = EventKeys.PASS_COMPLETE;
  }

  private static void interception(LiveMatch m, Outcome out, PlayerState qb, PlayerState target, PlayerState cover, Lineups.Defense d, int airYards,
                                   SplittableRandom r) {
    out.interception = true;
    out.result = PlayResult.INTERCEPTION;
    out.credit(qb, Stat.PASS_INT, 1);
    PlayerState picker = r.nextDouble() < 0.7 ? cover : PenaltyModel.pick(d.backs(), r);
    out.defender = picker;
    out.named("interceptedBy", picker);
    out.credit(picker, Stat.DEF_INT, 1);
    out.credit(picker, Stat.PASSES_DEFENDED, 1);
    int spot = Math.min(m.yd + airYards, 100);
    int ret;
    if (spot >= 100) ret = r.nextDouble() < 0.6 ? 0 : (int) Math.round(Rng.gaussian(r, 18, 8, 0, 60));
    else ret = (int) Math.round(Rng.gaussian(r, 9, 9, 0, 40)) + (r.nextDouble() < 0.06 ? (int) Math.round(Rng.exponential(r, 35)) : 0);
    out.returnYards = ret;
    // spot >= 100 sem retorno: touchback na 20 da defesa (jarda 80 do ataque)
    out.turnoverSpot = spot >= 100 && ret == 0 ? 80 : spot - ret;
    out.seconds = (int) Math.round(Rng.clamp(6 + ret / 8.0, 4, 14));
    out.key = EventKeys.INTERCEPTION;
  }

  private static void sack(LiveMatch m, Outcome out, PlayerState qb, Lineups.Defense d, SplittableRandom r) {
    out.sack = true;
    out.type = PlayType.PASS;
    out.result = PlayResult.SACK;
    List<PlayerState> rushers = new ArrayList<>(d.dl());
    rushers.addAll(d.lb());
    double[] w = new double[rushers.size()];
    for (int i = 0; i < rushers.size(); i++) w[i] = Math.pow(Math.max(1, Ratings.rush(rushers.get(i))) / 70.0, 4) * (i < d.dl().size() ? 1.0 : (out.blitz ? 0.7 : 0.25));
    PlayerState sacker = rushers.get(Rng.pick(r, w));
    int loss = (int) Math.round(Rng.gaussian(r, 7, 2.5, 1, 15));
    int yards = Math.max(-loss, -m.yd);
    out.yards = yards;
    out.tackler = sacker;
    out.named("sackedBy", sacker);
    out.param("yards", -yards);
    out.credit(qb, Stat.SACKED, 1);
    out.credit(qb, Stat.SACK_YDS, -yards);
    out.credit(sacker, Stat.SACKS, 1);
    out.credit(sacker, Stat.TACKLES, 1);
    out.credit(sacker, Stat.TFL, 1);
    out.seconds = (int) Math.round(Rng.gaussian(r, 5, 1, 3, 8));
    if (m.yd + yards <= 0) { out.result = PlayResult.SAFETY; out.key = EventKeys.SACK_SAFETY; return; }
    if (r.nextDouble() < 0.11 * m.setup.weather().fumbleFactor()) {
      fumble(m, out, qb, sacker, d, yards, r);
      if (out.fumbleLost) { out.key = EventKeys.SACK_FUMBLE; return; }
      out.result = PlayResult.SACK;
    }
    out.key = EventKeys.SACK;
  }

  /** Intentional grounding no lugar do sack: passe incompleto e a falta (local, perda de descida) que a defesa aceita ou recusa. */
  private static void grounding(LiveMatch m, Outcome out, PlayerState qb) {
    out.passAttempt = true;
    out.incomplete = true;
    out.result = PlayResult.INCOMPLETE;
    out.key = EventKeys.PASS_INCOMPLETE;
    out.credit(qb, Stat.PASS_ATT, 1);
    out.seconds = 5;
    out.penalty = new Outcome.PenaltyCall(br.com.velion.scoresaf.domain.shared.PenaltyCode.INTENTIONAL_GROUNDING, false, qb, m.yd - 7, false);
    out.groundingForced = true;
  }

  private static void scramble(LiveMatch m, Outcome out, PlayerState qb, Lineups.Defense d, SplittableRandom r) {
    out.scramble = true;
    out.rush = true;
    out.type = PlayType.PASS;
    double y = Rng.gaussian(r, 5.5 + (Ratings.mobility(qb) - 70) * 0.08, 4, -3, 25);
    if (r.nextDouble() < 0.04) y += Rng.exponential(r, 14);
    finishCarry(m, out, qb, d, (int) Math.round(y), 0.35, r);
  }

  /** Ajoelhar perde 1 jarda — na linha de 1 não perde nada (ajoelhar na end zone seria safety). */
  private static void kneel(LiveMatch m, Outcome out, Lineups.Offense o) {
    out.type = PlayType.KNEEL;
    out.rush = true;
    out.result = PlayResult.KNEEL;
    out.key = EventKeys.KNEEL;
    out.yards = m.yd > 1 ? -1 : 0;
    out.carrier = o.qb();
    out.named("rusher", o.qb());
    out.credit(o.qb(), Stat.RUSH_ATT, 1);
    out.credit(o.qb(), Stat.RUSH_YDS, out.yards);
    out.seconds = 2;
  }

  private static void spike(Outcome out, Lineups.Offense o) {
    out.type = PlayType.SPIKE;
    out.passAttempt = true;
    out.incomplete = true;
    out.result = PlayResult.SPIKE;
    out.key = EventKeys.SPIKE;
    out.passer = o.qb();
    out.named("passer", o.qb());
    out.credit(o.qb(), Stat.PASS_ATT, 1);
    out.seconds = 1;
  }

  /** Quem faz o tackle: linha e linebackers nas corridas por dentro, secundária nas jogadas longas e pelo lado. */
  static PlayerState tackler(Lineups.Defense d, boolean open, PlayerState matched, SplittableRandom r) {
    List<PlayerState> all = d.all();
    double[] w = new double[all.size()];
    for (int i = 0; i < all.size(); i++) {
      PlayerState p = all.get(i);
      double base = d.dl().contains(p) ? (open ? 0.25 : 0.9) : d.lb().contains(p) ? 1.4 : d.cb().contains(p) ? (open ? 1.2 : 0.45) : (open ? 1.5 : 0.7);
      if (p == matched) base *= 3;
      w[i] = base * (p.a(TACKLE) + p.a(PURSUIT)) / 140.0;
    }
    return all.get(Rng.pick(r, w));
  }
}
