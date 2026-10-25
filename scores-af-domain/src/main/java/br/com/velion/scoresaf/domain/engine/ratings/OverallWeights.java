package br.com.velion.scoresaf.domain.engine.ratings;

import static br.com.velion.scoresaf.domain.catalog.AttributeKey.*;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.shared.Position;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pesos oficiais do overall por posição, versionados ({@link #V1} = "OVR v1"). É o único lugar dos pesos: o port do front
 * ({@code frontend/src/lib/ratings/overall.ts}) copia esta tabela e o vetor de ouro ({@code docs/validation/golden/overall-vectors.json})
 * prende os dois. Mudar um peso é publicar uma versão nova (v2), nunca editar a v1 — os cards antigos dizem com que versão foram
 * calculados.
 *
 * <p>Os pesos são inteiros em milésimos e somam 1000 em cada posição (= 1,0): a conta do overall é inteira e dá o mesmo resultado em
 * Java e em TypeScript, sem diferença de arredondamento de ponto flutuante.
 */
public final class OverallWeights {
  public static final int SCALE = 1000;

  public static final OverallWeights V1 = new OverallWeights("OVR v1", v1());

  private final String version;
  private final Map<Position, Map<AttributeKey, Integer>> weights;

  private OverallWeights(String version, Map<Position, Map<AttributeKey, Integer>> weights) {
    this.version = version;
    Map<Position, Map<AttributeKey, Integer>> copy = new EnumMap<>(Position.class);
    weights.forEach((p, w) -> copy.put(p, Collections.unmodifiableMap(new EnumMap<>(w))));
    this.weights = Collections.unmodifiableMap(copy);
  }

  public String version() { return version; }

  /** Pesos da posição em milésimos (só os diferentes de zero), na ordem canônica de {@link AttributeKey}. */
  public Map<AttributeKey, Integer> of(Position position) {
    Map<AttributeKey, Integer> w = weights.get(position);
    if (w == null) throw new IllegalArgumentException("posição sem pesos: " + position);
    return w;
  }

  public Map<Position, Map<AttributeKey, Integer>> all() { return weights; }

  private static Map<AttributeKey, Integer> w(Object... kv) {
    Map<AttributeKey, Integer> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((AttributeKey) kv[i], (Integer) kv[i + 1]);
    return m;
  }

  private static Map<Position, Map<AttributeKey, Integer>> v1() {
    Map<Position, Map<AttributeKey, Integer>> m = new EnumMap<>(Position.class);
    m.put(Position.QB, w(SHORT_ACCURACY, 150, MEDIUM_ACCURACY, 150, DEEP_ACCURACY, 120, THROW_POWER, 120, THROW_ON_RUN, 60, PLAY_ACTION, 50,
        READ_DEFENSE, 130, POISE, 90, AWARENESS, 60, LEADERSHIP, 30, SPEED, 20, AGILITY, 20));
    m.put(Position.RB, w(SPEED, 120, ACCELERATION, 110, AGILITY, 100, VISION, 120, BREAK_TACKLE, 100, JUKE, 80, SPIN, 50, STIFF_ARM, 50,
        BALL_SECURITY, 80, CARRYING, 60, HANDS, 50, SHORT_ROUTE, 30, PASS_BLOCK, 20, AWARENESS, 30));
    m.put(Position.FB, w(RUN_BLOCK, 200, PASS_BLOCK, 100, BLOCK_POWER, 120, STRENGTH, 110, CARRYING, 100, BALL_SECURITY, 70, BREAK_TACKLE, 60,
        HANDS, 70, SHORT_ROUTE, 40, VISION, 40, AWARENESS, 50, SPEED, 40));
    m.put(Position.WR, w(SPEED, 140, ACCELERATION, 90, AGILITY, 70, HANDS, 140, CATCH_IN_TRAFFIC, 70, SPECTACULAR_CATCH, 50, SHORT_ROUTE, 90,
        MEDIUM_ROUTE, 100, DEEP_ROUTE, 90, RELEASE, 80, JUMPING, 30, AWARENESS, 50));
    m.put(Position.TE, w(HANDS, 130, CATCH_IN_TRAFFIC, 100, SHORT_ROUTE, 80, MEDIUM_ROUTE, 70, RELEASE, 40, RUN_BLOCK, 140, PASS_BLOCK, 100,
        BLOCK_POWER, 50, STRENGTH, 90, SPEED, 60, BREAK_TACKLE, 40, AWARENESS, 70, SPECTACULAR_CATCH, 30));
    m.put(Position.LT, w(PASS_BLOCK, 260, RUN_BLOCK, 140, BLOCK_TECHNIQUE, 130, BLOCK_FINESSE, 120, BLOCK_POWER, 60, STRENGTH, 110, AGILITY, 70,
        AWARENESS, 80, DURABILITY, 30));
    m.put(Position.LG, w(PASS_BLOCK, 160, RUN_BLOCK, 250, BLOCK_TECHNIQUE, 110, BLOCK_FINESSE, 60, BLOCK_POWER, 150, STRENGTH, 140, AGILITY, 40,
        AWARENESS, 70, DURABILITY, 20));
    m.put(Position.C, w(PASS_BLOCK, 180, RUN_BLOCK, 200, BLOCK_TECHNIQUE, 160, BLOCK_FINESSE, 60, BLOCK_POWER, 100, STRENGTH, 110, AWARENESS, 150,
        LEADERSHIP, 20, DURABILITY, 20));
    m.put(Position.RG, w(PASS_BLOCK, 170, RUN_BLOCK, 240, BLOCK_TECHNIQUE, 110, BLOCK_FINESSE, 60, BLOCK_POWER, 150, STRENGTH, 140, AGILITY, 40,
        AWARENESS, 70, DURABILITY, 20));
    m.put(Position.RT, w(PASS_BLOCK, 220, RUN_BLOCK, 180, BLOCK_TECHNIQUE, 120, BLOCK_FINESSE, 90, BLOCK_POWER, 100, STRENGTH, 120, AGILITY, 60,
        AWARENESS, 80, DURABILITY, 30));
    m.put(Position.DE, w(FINESSE_MOVES, 150, POWER_MOVES, 140, BLOCK_SHEDDING, 140, SPEED, 90, ACCELERATION, 90, STRENGTH, 100, TACKLE, 90,
        PURSUIT, 70, PLAY_RECOGNITION, 70, HIT_POWER, 60));
    m.put(Position.DT, w(POWER_MOVES, 170, BLOCK_SHEDDING, 190, STRENGTH, 170, FINESSE_MOVES, 80, TACKLE, 110, PLAY_RECOGNITION, 80, HIT_POWER, 70,
        ACCELERATION, 60, PURSUIT, 40, STAMINA, 30));
    m.put(Position.OLB, w(TACKLE, 130, PURSUIT, 120, SPEED, 100, ACCELERATION, 70, PLAY_RECOGNITION, 110, BLOCK_SHEDDING, 90, FINESSE_MOVES, 70,
        POWER_MOVES, 50, ZONE_COVERAGE, 80, MAN_COVERAGE, 50, HIT_POWER, 80, AWARENESS, 50));
    m.put(Position.MLB, w(TACKLE, 160, PLAY_RECOGNITION, 160, PURSUIT, 120, HIT_POWER, 90, BLOCK_SHEDDING, 90, ZONE_COVERAGE, 90, MAN_COVERAGE, 40,
        SPEED, 70, STRENGTH, 50, AWARENESS, 80, LEADERSHIP, 50));
    m.put(Position.CB, w(MAN_COVERAGE, 180, ZONE_COVERAGE, 140, PRESS, 110, SPEED, 150, ACCELERATION, 80, AGILITY, 80, PLAY_RECOGNITION, 80,
        HANDS, 40, JUMPING, 40, TACKLE, 50, AWARENESS, 50));
    m.put(Position.FS, w(ZONE_COVERAGE, 190, MAN_COVERAGE, 90, SPEED, 130, PLAY_RECOGNITION, 140, PURSUIT, 90, TACKLE, 80, HANDS, 50,
        ACCELERATION, 60, HIT_POWER, 40, AWARENESS, 90, JUMPING, 40));
    m.put(Position.SS, w(TACKLE, 140, HIT_POWER, 110, ZONE_COVERAGE, 140, MAN_COVERAGE, 90, PLAY_RECOGNITION, 120, PURSUIT, 100, SPEED, 100,
        STRENGTH, 50, BLOCK_SHEDDING, 50, AWARENESS, 70, ACCELERATION, 30));
    m.put(Position.K, w(KICK_POWER, 420, KICK_ACCURACY, 450, AWARENESS, 70, POISE, 60));
    m.put(Position.P, w(KICK_POWER, 450, KICK_ACCURACY, 400, AWARENESS, 80, POISE, 70));
    m.put(Position.LS, w(BLOCK_TECHNIQUE, 300, AWARENESS, 250, PASS_BLOCK, 150, STRENGTH, 120, TACKLE, 100, SPEED, 40, DURABILITY, 40));
    return m;
  }
}
