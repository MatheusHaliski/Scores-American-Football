package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.shared.PenaltyCode;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Sorteio das faltas (RF54): antes do snap (false start, delay of game, offside, encroachment) e durante a jogada (holding,
 * interferências, roughing, unnecessary roughness, facemask, formação ilegal; intentional grounding nasce de um sack evitado no
 * resolvedor). A chance sobe com o ruído da torcida para o ataque visitante e cai com a consciência ({@code AWARENESS}) de quem
 * comete — o motor não tem atributo de disciplina.
 */
final class PenaltyModel {
  private PenaltyModel() {}

  private static double discipline(List<PlayerState> group) {
    return Math.exp(-(Ratings.avg(group, p -> p.a(AttributeKey.AWARENESS)) - 70) * 0.02);
  }

  static Outcome.PenaltyCall preSnap(LiveMatch m, Lineups.Offense o, Lineups.Defense d, boolean hurry, SplittableRandom r) {
    boolean awayOffense = !m.homeOffense && !m.setup.neutralSite();
    double off = discipline(o.ol());
    double def = discipline(d.dl());
    double falseStart = 0.0125 * (awayOffense ? 1.35 : 0.85) * off;
    double delay = (hurry ? 0.002 : 0.0035);
    double offside = 0.0065 * def;
    double encroach = 0.0025 * def;
    double u = r.nextDouble();
    if ((u -= falseStart) < 0) return new Outcome.PenaltyCall(PenaltyCode.FALSE_START, false, pick(o.ol(), r), 0, true);
    if ((u -= delay) < 0) return new Outcome.PenaltyCall(PenaltyCode.DELAY_OF_GAME, false, o.qb(), 0, true);
    if ((u -= offside) < 0) return new Outcome.PenaltyCall(PenaltyCode.OFFSIDE, true, pick(d.dl(), r), 0, true);
    if ((u -= encroach) < 0) return new Outcome.PenaltyCall(PenaltyCode.ENCROACHMENT, true, pick(d.dl(), r), 0, true);
    return null;
  }

  /**
   * Falta durante a jogada (no máximo uma). {@code passEdge} é a vantagem do bloqueio sobre o pass rush (holding sobe quando a
   * linha perde o confronto).
   */
  static Outcome.PenaltyCall during(LiveMatch m, Outcome out, Lineups.Offense o, Lineups.Defense d, double passEdge, SplittableRandom r) {
    double off = discipline(o.ol());
    double def = discipline(d.backs());
    double u = r.nextDouble();
    double holdingO = (out.passAttempt || out.sack || out.scramble ? 0.019 * (passEdge < 0 ? 1.3 : 1.0) : 0.016) * off;
    if ((u -= holdingO) < 0) return new Outcome.PenaltyCall(PenaltyCode.HOLDING_OFFENSE, false, pick(o.ol(), r), 0, false);
    if ((u -= 0.003) < 0) return new Outcome.PenaltyCall(PenaltyCode.ILLEGAL_FORMATION, false, pick(o.receivers(), r), 0, false);
    if ((u -= 0.004) < 0) {
      boolean byDef = r.nextDouble() < 0.75;
      return new Outcome.PenaltyCall(PenaltyCode.UNNECESSARY_ROUGHNESS, byDef, byDef ? pick(d.all(), r) : pick(o.all(), r), 0, false);
    }
    if ((u -= 0.003) < 0) {
      boolean byDef = out.rush || out.completion || r.nextDouble() < 0.85;
      return new Outcome.PenaltyCall(PenaltyCode.FACEMASK, byDef, byDef ? (out.tackler != null ? out.tackler : pick(d.all(), r)) : pick(o.all(), r), 0, false);
    }
    if (out.passAttempt && !out.scramble) {
      boolean contested = out.incomplete || out.interception;
      double dpi = contested ? (out.concept == PlayConcept.DEEP_PASS ? 0.06 : 0.014) * def : 0;
      if ((u -= dpi) < 0) {
        PlayerState who = out.defender != null ? out.defender : pick(d.backs(), r);
        return new Outcome.PenaltyCall(PenaltyCode.PASS_INTERFERENCE_DEFENSE, true, who, m.yd + Math.max(1, out.airYards), false);
      }
      if ((u -= (contested ? 0.012 : 0.004) * def) < 0) return new Outcome.PenaltyCall(PenaltyCode.HOLDING_DEFENSE, true, pick(d.backs(), r), 0, false);
      if ((u -= 0.004) < 0) return new Outcome.PenaltyCall(PenaltyCode.PASS_INTERFERENCE_OFFENSE, false, out.receiver != null ? out.receiver : pick(o.receivers(), r), 0, false);
      if ((u -= 0.004) < 0) return new Outcome.PenaltyCall(PenaltyCode.ROUGHING_THE_PASSER, true, pick(d.dl(), r), 0, false);
    } else if (out.sack && (u -= 0.006) < 0) {
      return new Outcome.PenaltyCall(PenaltyCode.HOLDING_DEFENSE, true, pick(d.backs(), r), 0, false);
    }
    return null;
  }

  static PlayerState pick(List<PlayerState> ps, SplittableRandom r) { return ps.get(r.nextInt(ps.size())); }
}
