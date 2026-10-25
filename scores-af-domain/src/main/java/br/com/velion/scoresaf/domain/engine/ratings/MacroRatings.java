package br.com.velion.scoresaf.domain.engine.ratings;

import static br.com.velion.scoresaf.domain.catalog.AttributeKey.*;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import br.com.velion.scoresaf.domain.shared.PositionFamily;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Macros do card por família de posição (D-6, docs/01 §3.4): QB PRE BRA LEI MOB CAL LID; corredores e recebedores VEL AGI MAO ROT
 * QBT FOR; linha ofensiva BLP BLC FOR TEC AGI CON; defesa TAC IMP COB PRS VEL LEI; chutadores POT PRE CON. Cada macro é a média
 * ponderada (pesos inteiros em milésimos, soma 1000) de alguns subatributos, com o mesmo arredondamento inteiro do overall. As
 * definições moram aqui, junto da versão do overall ({@link #VERSION}); o front copia a tabela em {@code frontend/src/lib/ratings/macros.ts}.
 *
 * @param family família de posição
 * @param values código do macro → valor 0–100, na ordem de {@link PositionFamily#macroCodes()}
 */
public record MacroRatings(PositionFamily family, Map<String, Integer> values) {
  public static final String VERSION = OverallWeights.V1.version();

  private static final Map<PositionFamily, Map<String, Map<AttributeKey, Integer>>> DEFINITIONS = definitions();

  public MacroRatings {
    values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  public int get(String code) {
    Integer v = values.get(code);
    if (v == null) throw new IllegalArgumentException("macro " + code + " não existe na família " + family);
    return v;
  }

  public static MacroRatings of(PlayerAttributes attributes, PositionFamily family) {
    Map<String, Integer> out = new LinkedHashMap<>();
    for (Map.Entry<String, Map<AttributeKey, Integer>> e : definition(family).entrySet()) {
      out.put(e.getKey(), OverallCalculator.weighted(attributes, e.getValue()));
    }
    return new MacroRatings(family, out);
  }

  /** Definição dos macros da família: código → (subatributo → peso em milésimos). */
  public static Map<String, Map<AttributeKey, Integer>> definition(PositionFamily family) { return DEFINITIONS.get(family); }

  private static Map<AttributeKey, Integer> w(Object... kv) {
    Map<AttributeKey, Integer> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((AttributeKey) kv[i], (Integer) kv[i + 1]);
    return Collections.unmodifiableMap(new EnumMap<>(m));
  }

  private static Map<PositionFamily, Map<String, Map<AttributeKey, Integer>>> definitions() {
    Map<PositionFamily, Map<String, Map<AttributeKey, Integer>>> d = new EnumMap<>(PositionFamily.class);
    Map<String, Map<AttributeKey, Integer>> qb = new LinkedHashMap<>();
    qb.put("PRE", w(SHORT_ACCURACY, 350, MEDIUM_ACCURACY, 350, DEEP_ACCURACY, 300));
    qb.put("BRA", w(THROW_POWER, 800, DEEP_ACCURACY, 200));
    qb.put("LEI", w(READ_DEFENSE, 600, AWARENESS, 250, PLAY_ACTION, 150));
    qb.put("MOB", w(SPEED, 300, AGILITY, 300, ACCELERATION, 150, THROW_ON_RUN, 250));
    qb.put("CAL", w(POISE, 1000));
    qb.put("LID", w(LEADERSHIP, 700, AWARENESS, 300));
    d.put(PositionFamily.QUARTERBACK, qb);
    Map<String, Map<AttributeKey, Integer>> skill = new LinkedHashMap<>();
    skill.put("VEL", w(SPEED, 600, ACCELERATION, 400));
    skill.put("AGI", w(AGILITY, 500, JUKE, 300, SPIN, 200));
    skill.put("MAO", w(HANDS, 600, CATCH_IN_TRAFFIC, 250, SPECTACULAR_CATCH, 150));
    skill.put("ROT", w(SHORT_ROUTE, 300, MEDIUM_ROUTE, 300, DEEP_ROUTE, 200, RELEASE, 200));
    skill.put("QBT", w(BREAK_TACKLE, 500, STIFF_ARM, 250, CARRYING, 250));
    skill.put("FOR", w(STRENGTH, 500, RUN_BLOCK, 300, BALL_SECURITY, 200));
    d.put(PositionFamily.SKILL, skill);
    Map<String, Map<AttributeKey, Integer>> ol = new LinkedHashMap<>();
    ol.put("BLP", w(PASS_BLOCK, 1000));
    ol.put("BLC", w(RUN_BLOCK, 1000));
    ol.put("FOR", w(STRENGTH, 600, BLOCK_POWER, 400));
    ol.put("TEC", w(BLOCK_TECHNIQUE, 600, BLOCK_FINESSE, 400));
    ol.put("AGI", w(AGILITY, 500, ACCELERATION, 500));
    ol.put("CON", w(AWARENESS, 1000));
    d.put(PositionFamily.OFFENSIVE_LINE, ol);
    Map<String, Map<AttributeKey, Integer>> def = new LinkedHashMap<>();
    def.put("TAC", w(TACKLE, 1000));
    def.put("IMP", w(HIT_POWER, 700, STRENGTH, 300));
    def.put("COB", w(MAN_COVERAGE, 400, ZONE_COVERAGE, 400, PRESS, 200));
    def.put("PRS", w(FINESSE_MOVES, 350, POWER_MOVES, 350, BLOCK_SHEDDING, 300));
    def.put("VEL", w(SPEED, 500, ACCELERATION, 250, PURSUIT, 250));
    def.put("LEI", w(PLAY_RECOGNITION, 700, AWARENESS, 300));
    d.put(PositionFamily.DEFENSE, def);
    Map<String, Map<AttributeKey, Integer>> k = new LinkedHashMap<>();
    k.put("POT", w(KICK_POWER, 1000));
    k.put("PRE", w(KICK_ACCURACY, 1000));
    k.put("CON", w(POISE, 500, AWARENESS, 500));
    d.put(PositionFamily.KICKER, k);
    for (PositionFamily f : PositionFamily.values()) {
      if (!d.get(f).keySet().stream().toList().equals(f.macroCodes())) throw new IllegalStateException("macros da família " + f + " fora da ordem");
      d.put(f, Collections.unmodifiableMap(d.get(f)));
    }
    return Collections.unmodifiableMap(d);
  }
}
