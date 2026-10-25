package br.com.velion.scoresaf.domain.engine.model;

import java.util.List;
import java.util.Map;

/**
 * Chaves i18n dos textos do play-by-play (RF54). O motor grava a chave e os parâmetros (nomes e números); o texto fica no catálogo
 * {@code match} do front nos três idiomas, no formato {@code namespace:chave} das referências de mensagem do front ({@code MessageRef}). {@link #ALL} é o
 * catálogo fechado: o teste do motor falha se um lance usar chave fora dele, e o teste do front formata um exemplo real de cada chave
 * (amostra dourada {@code match-i18n-keys.json}) nos três idiomas.
 */
public final class EventKeys {
  private EventKeys() {}

  private static final String P = "match:event.";
  public static final String COIN_TOSS = P + "coinToss";
  public static final String KICKOFF_TOUCHBACK = P + "kickoff.touchback";
  public static final String KICKOFF_RETURN = P + "kickoff.return";
  public static final String KICKOFF_RETURN_TD = P + "kickoff.returnTouchdown";
  public static final String KICKOFF_FUMBLE = P + "kickoff.fumble";
  public static final String ONSIDE_RECOVERED = P + "onside.recovered";
  public static final String ONSIDE_LOST = P + "onside.lost";
  public static final String FREE_KICK_RETURN = P + "freeKick.return";
  public static final String RUN = P + "run.gain";
  public static final String RUN_TD = P + "run.touchdown";
  public static final String RUN_FUMBLE = P + "run.fumble";
  public static final String RUN_SAFETY = P + "run.safety";
  public static final String PASS_COMPLETE = P + "pass.complete";
  public static final String PASS_TD = P + "pass.touchdown";
  public static final String PASS_INCOMPLETE = P + "pass.incomplete";
  public static final String PASS_DROP = P + "pass.drop";
  public static final String PASS_DEFENDED = P + "pass.defended";
  public static final String PASS_FUMBLE = P + "pass.fumble";
  public static final String INTERCEPTION = P + "pass.interception";
  public static final String INTERCEPTION_TD = P + "pass.interceptionTouchdown";
  public static final String SACK = P + "sack";
  public static final String SACK_FUMBLE = P + "sack.fumble";
  public static final String SACK_SAFETY = P + "sack.safety";
  public static final String SCRAMBLE = P + "scramble";
  public static final String PUNT_RETURN = P + "punt.return";
  public static final String PUNT_RETURN_TD = P + "punt.returnTouchdown";
  public static final String PUNT_FAIR_CATCH = P + "punt.fairCatch";
  public static final String PUNT_TOUCHBACK = P + "punt.touchback";
  public static final String PUNT_DOWNED = P + "punt.downed";
  public static final String PUNT_BLOCKED = P + "punt.blocked";
  public static final String PUNT_MUFFED = P + "punt.muffed";
  public static final String FIELD_GOAL_GOOD = P + "fieldGoal.good";
  public static final String FIELD_GOAL_MISSED = P + "fieldGoal.missed";
  public static final String FIELD_GOAL_BLOCKED = P + "fieldGoal.blocked";
  public static final String EXTRA_POINT_GOOD = P + "extraPoint.good";
  public static final String EXTRA_POINT_MISSED = P + "extraPoint.missed";
  public static final String TWO_POINT_GOOD = P + "twoPoint.good";
  public static final String TWO_POINT_FAILED = P + "twoPoint.failed";
  public static final String KNEEL = P + "kneel";
  public static final String SPIKE = P + "spike";
  public static final String PENALTY = P + "penalty";
  public static final String TIMEOUT = P + "timeout";
  public static final String TWO_MINUTE_WARNING = P + "twoMinuteWarning";
  public static final String END_OF_QUARTER = P + "endOfQuarter";
  public static final String END_OF_HALF = P + "endOfHalf";
  public static final String END_OF_REGULATION = P + "endOfRegulation";
  public static final String END_OF_OVERTIME = P + "endOfOvertime";
  public static final String FINAL = P + "final";

  public static final List<String> ALL = List.of(COIN_TOSS, KICKOFF_TOUCHBACK, KICKOFF_RETURN, KICKOFF_RETURN_TD, KICKOFF_FUMBLE, ONSIDE_RECOVERED,
      ONSIDE_LOST, FREE_KICK_RETURN, RUN, RUN_TD, RUN_FUMBLE, RUN_SAFETY, PASS_COMPLETE, PASS_TD, PASS_INCOMPLETE, PASS_DROP, PASS_DEFENDED,
      PASS_FUMBLE, INTERCEPTION, INTERCEPTION_TD, SACK, SACK_FUMBLE, SACK_SAFETY, SCRAMBLE, PUNT_RETURN, PUNT_RETURN_TD, PUNT_FAIR_CATCH,
      PUNT_TOUCHBACK, PUNT_DOWNED, PUNT_BLOCKED, PUNT_MUFFED, FIELD_GOAL_GOOD, FIELD_GOAL_MISSED, FIELD_GOAL_BLOCKED, EXTRA_POINT_GOOD,
      EXTRA_POINT_MISSED, TWO_POINT_GOOD, TWO_POINT_FAILED, KNEEL, SPIKE, PENALTY, TIMEOUT, TWO_MINUTE_WARNING, END_OF_QUARTER, END_OF_HALF,
      END_OF_REGULATION, END_OF_OVERTIME, FINAL);

  /**
   * Parâmetros que o motor sempre manda em cada chave (os textos do catálogo só podem usar estes; o teste do motor confere que todo
   * evento os traz e o do front, que nenhuma mensagem pede outro).
   */
  public static final Map<String, List<String>> REQUIRED_PARAMS = Map.ofEntries(
      Map.entry(COIN_TOSS, List.of("receiving")), Map.entry(KICKOFF_TOUCHBACK, List.of("kicker")),
      Map.entry(KICKOFF_RETURN, List.of("returner", "yards")), Map.entry(KICKOFF_RETURN_TD, List.of("returner")),
      Map.entry(KICKOFF_FUMBLE, List.of("returner")), Map.entry(ONSIDE_RECOVERED, List.of("kicker")), Map.entry(ONSIDE_LOST, List.of("kicker")),
      Map.entry(FREE_KICK_RETURN, List.of("returner", "yards")), Map.entry(RUN, List.of("rusher", "yards")), Map.entry(RUN_TD, List.of("rusher", "yards")),
      Map.entry(RUN_FUMBLE, List.of("rusher", "recoveredBy")), Map.entry(RUN_SAFETY, List.of()),
      Map.entry(PASS_COMPLETE, List.of("passer", "receiver", "yards")), Map.entry(PASS_TD, List.of("passer", "receiver", "yards")),
      Map.entry(PASS_INCOMPLETE, List.of("passer")), Map.entry(PASS_DROP, List.of("passer", "receiver")),
      Map.entry(PASS_DEFENDED, List.of("passer", "receiver", "defender")), Map.entry(PASS_FUMBLE, List.of("receiver", "recoveredBy")),
      Map.entry(INTERCEPTION, List.of("passer", "interceptedBy")), Map.entry(INTERCEPTION_TD, List.of("passer", "interceptedBy")),
      Map.entry(SACK, List.of("passer", "sackedBy", "yards")), Map.entry(SACK_FUMBLE, List.of("passer", "sackedBy", "recoveredBy")),
      Map.entry(SACK_SAFETY, List.of("passer", "sackedBy")), Map.entry(SCRAMBLE, List.of("rusher", "yards")),
      Map.entry(PUNT_RETURN, List.of("punter", "yards", "returner", "returnYards")), Map.entry(PUNT_RETURN_TD, List.of("returner")),
      Map.entry(PUNT_FAIR_CATCH, List.of("punter", "yards", "returner")), Map.entry(PUNT_TOUCHBACK, List.of("punter", "yards")),
      Map.entry(PUNT_DOWNED, List.of("punter", "yards")), Map.entry(PUNT_BLOCKED, List.of("punter")), Map.entry(PUNT_MUFFED, List.of("returner")),
      Map.entry(FIELD_GOAL_GOOD, List.of("kicker", "distance")), Map.entry(FIELD_GOAL_MISSED, List.of("kicker", "distance")),
      Map.entry(FIELD_GOAL_BLOCKED, List.of("kicker", "distance")), Map.entry(EXTRA_POINT_GOOD, List.of("kicker")),
      Map.entry(EXTRA_POINT_MISSED, List.of("kicker")), Map.entry(TWO_POINT_GOOD, List.of()), Map.entry(TWO_POINT_FAILED, List.of()),
      Map.entry(KNEEL, List.of("rusher")), Map.entry(SPIKE, List.of("passer")), Map.entry(PENALTY, List.of("code")),
      Map.entry(TIMEOUT, List.of("team", "remaining")), Map.entry(TWO_MINUTE_WARNING, List.of()), Map.entry(END_OF_QUARTER, List.of("quarter")),
      Map.entry(END_OF_HALF, List.of()), Map.entry(END_OF_REGULATION, List.of()), Map.entry(END_OF_OVERTIME, List.of("period")), Map.entry(FINAL, List.of()));

  /** Códigos de opção que uma decisão pode oferecer (RF62): conceitos de ataque, chutes, conversões e chamadas de defesa. */
  public static final List<String> OPTION_CODES = List.of("INSIDE_RUN", "OUTSIDE_RUN", "QB_SNEAK", "SHORT_PASS", "MEDIUM_PASS", "DEEP_PASS", "PUNT",
      "FIELD_GOAL", "KNEEL", "SPIKE", "EXTRA_POINT", "TWO_POINT_RUN", "TWO_POINT_PASS", "DEF_BASE_COVER_3", "DEF_NICKEL_COVER_2", "DEF_DIME_COVER_4",
      "DEF_BLITZ_COVER_1", "DEF_GOAL_LINE_COVER_0");

  /** Rótulo i18n de uma opção de chamada de jogada ({@code match:play.<CODE>}). */
  public static String option(String code) { return "match:play." + code; }
}
