package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.engine.model.DecisionSide;
import br.com.velion.scoresaf.domain.engine.model.DecisionSituation;
import br.com.velion.scoresaf.domain.engine.model.EventKeys;
import br.com.velion.scoresaf.domain.engine.model.PlayOption;
import br.com.velion.scoresaf.domain.engine.sim.PlayCaller.DefenseCall;
import br.com.velion.scoresaf.domain.engine.sim.PlayCaller.Kind;
import br.com.velion.scoresaf.domain.engine.sim.PlayCaller.OffenseCall;
import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.Formation;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Momentos decisivos (RF62) e as opções oferecidas ao manager. {@code KEY_MOMENTS}: 4ª descida, últimos 2 minutos de cada tempo,
 * prorrogação e conversão depois de TD no 4º quarto; {@code ALL_PLAYS}: todo snap; {@code NONE} ou jogo sem humano: nunca. Vale
 * com a franquia do manager no ataque (opções de ataque) ou na defesa (opções de defesa).
 */
final class DecisiveMoments {
  private DecisiveMoments() {}

  static final String EXTRA_POINT = "EXTRA_POINT";
  static final String TWO_POINT_RUN = "TWO_POINT_RUN";
  static final String TWO_POINT_PASS = "TWO_POINT_PASS";
  static final String DEF_BASE_COVER_3 = "DEF_BASE_COVER_3";
  static final String DEF_NICKEL_COVER_2 = "DEF_NICKEL_COVER_2";
  static final String DEF_DIME_COVER_4 = "DEF_DIME_COVER_4";
  static final String DEF_BLITZ_COVER_1 = "DEF_BLITZ_COVER_1";
  static final String DEF_GOAL_LINE_COVER_0 = "DEF_GOAL_LINE_COVER_0";

  static Optional<DecisionSituation> detect(LiveMatch m, boolean conversion) {
    if (m.setup.userClubId() == null || m.setup.playCallingLevel() == PlayCallingLevel.NONE) return Optional.empty();
    if (!m.userIsHome() && !m.userIsAway()) return Optional.empty();
    if (m.setup.playCallingLevel() == PlayCallingLevel.ALL_PLAYS) return Optional.of(DecisionSituation.ALL_PLAYS);
    if (m.overtime()) return Optional.of(DecisionSituation.OVERTIME);
    if (conversion) return m.quarter >= 4 ? Optional.of(DecisionSituation.CONVERSION) : Optional.empty();
    if (m.down >= m.rules.downs()) return Optional.of(DecisionSituation.FOURTH_DOWN);
    if ((m.quarter == 2 || m.quarter == 4) && m.clock <= EngineConstants.TWO_MINUTES) return Optional.of(DecisionSituation.TWO_MINUTE);
    return Optional.empty();
  }

  static DecisionSide side(LiveMatch m) {
    boolean userHome = m.userIsHome();
    return userHome == m.homeOffense ? DecisionSide.OFFENSE : DecisionSide.DEFENSE;
  }

  // ---------------------------------------------------------------- opções
  static List<PlayOption> options(LiveMatch m, DecisionSide side, boolean conversion) {
    List<PlayOption> out = new ArrayList<>();
    if (side == DecisionSide.DEFENSE) {
      if (conversion) {
        out.add(def(DEF_GOAL_LINE_COVER_0, DefensePackage.GOAL_LINE, Coverage.COVER_0, false));
        out.add(def(DEF_NICKEL_COVER_2, DefensePackage.NICKEL, Coverage.COVER_2, false));
        out.add(def(DEF_BLITZ_COVER_1, m.defense().plan.baseDefense(), Coverage.COVER_1, true));
        return out;
      }
      out.add(def(DEF_BASE_COVER_3, m.defense().plan.baseDefense(), Coverage.COVER_3, false));
      out.add(def(DEF_NICKEL_COVER_2, DefensePackage.NICKEL, Coverage.COVER_2, false));
      out.add(def(DEF_DIME_COVER_4, DefensePackage.DIME, Coverage.COVER_4, false));
      out.add(def(DEF_BLITZ_COVER_1, m.defense().plan.baseDefense(), Coverage.COVER_1, true));
      if (m.yd >= 90 || m.distance <= 2) out.add(def(DEF_GOAL_LINE_COVER_0, DefensePackage.GOAL_LINE, Coverage.COVER_0, false));
      return out;
    }
    if (conversion) {
      boolean forcedTwo = m.collegeOvertime() && m.collegeRound >= 2;
      if (!forcedTwo) out.add(special(EXTRA_POINT, null));
      out.add(off(TWO_POINT_RUN, PlayConcept.INSIDE_RUN, Formation.GOAL_LINE));
      out.add(off(TWO_POINT_PASS, PlayConcept.SHORT_PASS, Formation.SHOTGUN));
      return out;
    }
    boolean inRange = PlayCaller.fieldGoalDistance(m.yd) <= PlayCaller.fieldGoalRange(m, m.offense()) + 3;
    if (m.down >= m.rules.downs()) {
      out.add(m.distance <= 1 ? off(PlayConcept.QB_SNEAK.name(), PlayConcept.QB_SNEAK, Formation.GOAL_LINE) : off(PlayConcept.INSIDE_RUN.name(), PlayConcept.INSIDE_RUN, Formation.I_FORM));
      out.add(off(PlayConcept.SHORT_PASS.name(), PlayConcept.SHORT_PASS, Formation.SHOTGUN));
      out.add(off(PlayConcept.MEDIUM_PASS.name(), PlayConcept.MEDIUM_PASS, Formation.SHOTGUN));
      if (!m.collegeOvertime()) out.add(special(PlayConcept.PUNT.name(), PlayConcept.PUNT));
      if (inRange) out.add(special(PlayConcept.FIELD_GOAL.name(), PlayConcept.FIELD_GOAL));
      return out;
    }
    if (PlayCaller.canKneelOut(m)) out.add(special(PlayConcept.KNEEL.name(), PlayConcept.KNEEL));
    out.add(off(PlayConcept.INSIDE_RUN.name(), PlayConcept.INSIDE_RUN, Formation.SINGLEBACK));
    if (out.size() < 2) out.add(off(PlayConcept.OUTSIDE_RUN.name(), PlayConcept.OUTSIDE_RUN, Formation.SINGLEBACK));
    out.add(off(PlayConcept.SHORT_PASS.name(), PlayConcept.SHORT_PASS, Formation.SHOTGUN));
    out.add(off(PlayConcept.MEDIUM_PASS.name(), PlayConcept.MEDIUM_PASS, Formation.SHOTGUN));
    if (m.timed() && inRange && m.clock <= 10) out.add(special(PlayConcept.FIELD_GOAL.name(), PlayConcept.FIELD_GOAL));
    else if (m.timed() && m.clockState == br.com.velion.scoresaf.domain.engine.model.EngineState.ClockState.RUNNING && m.clock <= 60 && m.diff(m.offense()) <= 0)
      out.add(special(PlayConcept.SPIKE.name(), PlayConcept.SPIKE));
    else out.add(off(PlayConcept.DEEP_PASS.name(), PlayConcept.DEEP_PASS, Formation.SHOTGUN));
    return out.size() > 5 ? out.subList(0, 5) : out;
  }

  private static PlayOption off(String code, PlayConcept c, Formation f) {
    return new PlayOption(code, DecisionSide.OFFENSE, c, f, null, null, false, EventKeys.option(code));
  }

  private static PlayOption special(String code, PlayConcept c) {
    return new PlayOption(code, DecisionSide.OFFENSE, c, null, null, null, false, EventKeys.option(code));
  }

  private static PlayOption def(String code, DefensePackage p, Coverage c, boolean blitz) {
    return new PlayOption(code, DecisionSide.DEFENSE, null, null, p, c, blitz, EventKeys.option(code));
  }

  /** Converte a opção escolhida na chamada do ataque. */
  static OffenseCall offenseCall(PlayOption o) {
    return switch (o.code()) {
      case EXTRA_POINT -> new OffenseCall(Kind.EXTRA_POINT, PlayConcept.FIELD_GOAL, null);
      case TWO_POINT_RUN -> PlayCaller.twoPoint(true);
      case TWO_POINT_PASS -> PlayCaller.twoPoint(false);
      default -> switch (o.concept()) {
        case PUNT -> OffenseCall.of(Kind.PUNT);
        case FIELD_GOAL -> OffenseCall.of(Kind.FIELD_GOAL);
        case KNEEL -> OffenseCall.of(Kind.KNEEL);
        case SPIKE -> OffenseCall.of(Kind.SPIKE);
        default -> new OffenseCall(Kind.PLAY, o.concept(), o.formation());
      };
    };
  }

  static DefenseCall defenseCall(PlayOption o, DefenseCall ai) {
    return new DefenseCall(o.defensePackage(), o.coverage(), o.blitz(), ai != null && ai.spy());
  }

  /** A opção mais próxima da chamada da IA (o que vai acontecer se o prazo vencer). */
  static String closest(List<PlayOption> options, OffenseCall oc, DefenseCall dc, DecisionSide side) {
    String want;
    if (side == DecisionSide.DEFENSE) {
      if (dc.pkg() == DefensePackage.GOAL_LINE) want = DEF_GOAL_LINE_COVER_0;
      else if (dc.blitz()) want = DEF_BLITZ_COVER_1;
      else if (dc.pkg() == DefensePackage.DIME) want = DEF_DIME_COVER_4;
      else if (dc.pkg() == DefensePackage.NICKEL) want = DEF_NICKEL_COVER_2;
      else want = DEF_BASE_COVER_3;
    } else {
      want = switch (oc.kind()) {
        case EXTRA_POINT -> EXTRA_POINT;
        case TWO_POINT -> oc.concept() == PlayConcept.INSIDE_RUN ? TWO_POINT_RUN : TWO_POINT_PASS;
        default -> switch (oc.concept()) {
          case PLAY_ACTION -> PlayConcept.MEDIUM_PASS.name();
          case SCREEN -> PlayConcept.SHORT_PASS.name();
          default -> oc.concept().name();
        };
      };
    }
    for (PlayOption o : options) if (o.code().equals(want)) return want;
    // fora da lista: corrida → a corrida oferecida; passe fundo → o médio
    List<String> alts = want.endsWith("RUN") || want.equals(PlayConcept.QB_SNEAK.name())
        ? List.of(PlayConcept.INSIDE_RUN.name(), PlayConcept.QB_SNEAK.name(), PlayConcept.OUTSIDE_RUN.name())
        : List.of(PlayConcept.MEDIUM_PASS.name(), PlayConcept.SHORT_PASS.name());
    for (String alt : alts) for (PlayOption o : options) if (o.code().equals(alt)) return alt;
    return options.get(0).code();
  }
}
