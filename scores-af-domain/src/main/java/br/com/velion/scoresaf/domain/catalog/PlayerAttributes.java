package br.com.velion.scoresaf.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Subatributos do jogador (0–100), embutidos em {@code Player} e no "meu jogador" (bloco 9). Cada campo corresponde a uma
 * {@link AttributeKey}; {@link #get(AttributeKey)} e {@link #with(AttributeKey, int)} são o acesso genérico usado pelo overall, pelos
 * macros e pelo editor. Colunas {@code attr_<snake_case>}.
 */
@Embeddable
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PlayerAttributes {
  // PHYSICAL
  @Column(name = "attr_speed", nullable = false) @Builder.Default private int speed = 50;
  @Column(name = "attr_acceleration", nullable = false) @Builder.Default private int acceleration = 50;
  @Column(name = "attr_agility", nullable = false) @Builder.Default private int agility = 50;
  @Column(name = "attr_strength", nullable = false) @Builder.Default private int strength = 50;
  @Column(name = "attr_jumping", nullable = false) @Builder.Default private int jumping = 50;
  @Column(name = "attr_stamina", nullable = false) @Builder.Default private int stamina = 50;
  @Column(name = "attr_durability", nullable = false) @Builder.Default private int durability = 50;
  // PASSING
  @Column(name = "attr_short_accuracy", nullable = false) @Builder.Default private int shortAccuracy = 50;
  @Column(name = "attr_medium_accuracy", nullable = false) @Builder.Default private int mediumAccuracy = 50;
  @Column(name = "attr_deep_accuracy", nullable = false) @Builder.Default private int deepAccuracy = 50;
  @Column(name = "attr_throw_power", nullable = false) @Builder.Default private int throwPower = 50;
  @Column(name = "attr_throw_on_run", nullable = false) @Builder.Default private int throwOnRun = 50;
  @Column(name = "attr_play_action", nullable = false) @Builder.Default private int playAction = 50;
  @Column(name = "attr_read_defense", nullable = false) @Builder.Default private int readDefense = 50;
  @Column(name = "attr_poise", nullable = false) @Builder.Default private int poise = 50;
  // RUSHING
  @Column(name = "attr_vision", nullable = false) @Builder.Default private int vision = 50;
  @Column(name = "attr_break_tackle", nullable = false) @Builder.Default private int breakTackle = 50;
  @Column(name = "attr_juke", nullable = false) @Builder.Default private int juke = 50;
  @Column(name = "attr_spin", nullable = false) @Builder.Default private int spin = 50;
  @Column(name = "attr_stiff_arm", nullable = false) @Builder.Default private int stiffArm = 50;
  @Column(name = "attr_ball_security", nullable = false) @Builder.Default private int ballSecurity = 50;
  @Column(name = "attr_carrying", nullable = false) @Builder.Default private int carrying = 50;
  // RECEIVING
  @Column(name = "attr_hands", nullable = false) @Builder.Default private int hands = 50;
  @Column(name = "attr_catch_in_traffic", nullable = false) @Builder.Default private int catchInTraffic = 50;
  @Column(name = "attr_spectacular_catch", nullable = false) @Builder.Default private int spectacularCatch = 50;
  @Column(name = "attr_short_route", nullable = false) @Builder.Default private int shortRoute = 50;
  @Column(name = "attr_medium_route", nullable = false) @Builder.Default private int mediumRoute = 50;
  @Column(name = "attr_deep_route", nullable = false) @Builder.Default private int deepRoute = 50;
  @Column(name = "attr_release", nullable = false) @Builder.Default private int release = 50;
  // BLOCKING
  @Column(name = "attr_pass_block", nullable = false) @Builder.Default private int passBlock = 50;
  @Column(name = "attr_run_block", nullable = false) @Builder.Default private int runBlock = 50;
  @Column(name = "attr_block_technique", nullable = false) @Builder.Default private int blockTechnique = 50;
  @Column(name = "attr_block_finesse", nullable = false) @Builder.Default private int blockFinesse = 50;
  @Column(name = "attr_block_power", nullable = false) @Builder.Default private int blockPower = 50;
  // DEFENSE
  @Column(name = "attr_tackle", nullable = false) @Builder.Default private int tackle = 50;
  @Column(name = "attr_hit_power", nullable = false) @Builder.Default private int hitPower = 50;
  @Column(name = "attr_pursuit", nullable = false) @Builder.Default private int pursuit = 50;
  @Column(name = "attr_play_recognition", nullable = false) @Builder.Default private int playRecognition = 50;
  @Column(name = "attr_man_coverage", nullable = false) @Builder.Default private int manCoverage = 50;
  @Column(name = "attr_zone_coverage", nullable = false) @Builder.Default private int zoneCoverage = 50;
  @Column(name = "attr_press", nullable = false) @Builder.Default private int press = 50;
  @Column(name = "attr_finesse_moves", nullable = false) @Builder.Default private int finesseMoves = 50;
  @Column(name = "attr_power_moves", nullable = false) @Builder.Default private int powerMoves = 50;
  @Column(name = "attr_block_shedding", nullable = false) @Builder.Default private int blockShedding = 50;
  // KICKING
  @Column(name = "attr_kick_power", nullable = false) @Builder.Default private int kickPower = 50;
  @Column(name = "attr_kick_accuracy", nullable = false) @Builder.Default private int kickAccuracy = 50;
  @Column(name = "attr_kick_return", nullable = false) @Builder.Default private int kickReturn = 50;
  // MENTAL
  @Column(name = "attr_awareness", nullable = false) @Builder.Default private int awareness = 50;
  @Column(name = "attr_leadership", nullable = false) @Builder.Default private int leadership = 50;

  /** Valor do subatributo. */
  public int get(AttributeKey key) {
    return switch (key) {
      case SPEED -> speed;
      case ACCELERATION -> acceleration;
      case AGILITY -> agility;
      case STRENGTH -> strength;
      case JUMPING -> jumping;
      case STAMINA -> stamina;
      case DURABILITY -> durability;
      case SHORT_ACCURACY -> shortAccuracy;
      case MEDIUM_ACCURACY -> mediumAccuracy;
      case DEEP_ACCURACY -> deepAccuracy;
      case THROW_POWER -> throwPower;
      case THROW_ON_RUN -> throwOnRun;
      case PLAY_ACTION -> playAction;
      case READ_DEFENSE -> readDefense;
      case POISE -> poise;
      case VISION -> vision;
      case BREAK_TACKLE -> breakTackle;
      case JUKE -> juke;
      case SPIN -> spin;
      case STIFF_ARM -> stiffArm;
      case BALL_SECURITY -> ballSecurity;
      case CARRYING -> carrying;
      case HANDS -> hands;
      case CATCH_IN_TRAFFIC -> catchInTraffic;
      case SPECTACULAR_CATCH -> spectacularCatch;
      case SHORT_ROUTE -> shortRoute;
      case MEDIUM_ROUTE -> mediumRoute;
      case DEEP_ROUTE -> deepRoute;
      case RELEASE -> release;
      case PASS_BLOCK -> passBlock;
      case RUN_BLOCK -> runBlock;
      case BLOCK_TECHNIQUE -> blockTechnique;
      case BLOCK_FINESSE -> blockFinesse;
      case BLOCK_POWER -> blockPower;
      case TACKLE -> tackle;
      case HIT_POWER -> hitPower;
      case PURSUIT -> pursuit;
      case PLAY_RECOGNITION -> playRecognition;
      case MAN_COVERAGE -> manCoverage;
      case ZONE_COVERAGE -> zoneCoverage;
      case PRESS -> press;
      case FINESSE_MOVES -> finesseMoves;
      case POWER_MOVES -> powerMoves;
      case BLOCK_SHEDDING -> blockShedding;
      case KICK_POWER -> kickPower;
      case KICK_ACCURACY -> kickAccuracy;
      case KICK_RETURN -> kickReturn;
      case AWARENESS -> awareness;
      case LEADERSHIP -> leadership;
    };
  }

  /** Grava o subatributo já limitado a [0, 100] e devolve esta instância. */
  public PlayerAttributes with(AttributeKey key, int value) {
    int v = clamp(value);
    switch (key) {
      case SPEED -> speed = v;
      case ACCELERATION -> acceleration = v;
      case AGILITY -> agility = v;
      case STRENGTH -> strength = v;
      case JUMPING -> jumping = v;
      case STAMINA -> stamina = v;
      case DURABILITY -> durability = v;
      case SHORT_ACCURACY -> shortAccuracy = v;
      case MEDIUM_ACCURACY -> mediumAccuracy = v;
      case DEEP_ACCURACY -> deepAccuracy = v;
      case THROW_POWER -> throwPower = v;
      case THROW_ON_RUN -> throwOnRun = v;
      case PLAY_ACTION -> playAction = v;
      case READ_DEFENSE -> readDefense = v;
      case POISE -> poise = v;
      case VISION -> vision = v;
      case BREAK_TACKLE -> breakTackle = v;
      case JUKE -> juke = v;
      case SPIN -> spin = v;
      case STIFF_ARM -> stiffArm = v;
      case BALL_SECURITY -> ballSecurity = v;
      case CARRYING -> carrying = v;
      case HANDS -> hands = v;
      case CATCH_IN_TRAFFIC -> catchInTraffic = v;
      case SPECTACULAR_CATCH -> spectacularCatch = v;
      case SHORT_ROUTE -> shortRoute = v;
      case MEDIUM_ROUTE -> mediumRoute = v;
      case DEEP_ROUTE -> deepRoute = v;
      case RELEASE -> release = v;
      case PASS_BLOCK -> passBlock = v;
      case RUN_BLOCK -> runBlock = v;
      case BLOCK_TECHNIQUE -> blockTechnique = v;
      case BLOCK_FINESSE -> blockFinesse = v;
      case BLOCK_POWER -> blockPower = v;
      case TACKLE -> tackle = v;
      case HIT_POWER -> hitPower = v;
      case PURSUIT -> pursuit = v;
      case PLAY_RECOGNITION -> playRecognition = v;
      case MAN_COVERAGE -> manCoverage = v;
      case ZONE_COVERAGE -> zoneCoverage = v;
      case PRESS -> press = v;
      case FINESSE_MOVES -> finesseMoves = v;
      case POWER_MOVES -> powerMoves = v;
      case BLOCK_SHEDDING -> blockShedding = v;
      case KICK_POWER -> kickPower = v;
      case KICK_ACCURACY -> kickAccuracy = v;
      case KICK_RETURN -> kickReturn = v;
      case AWARENESS -> awareness = v;
      case LEADERSHIP -> leadership = v;
    }
    return this;
  }

  /** Cópia independente. */
  public PlayerAttributes copy() { return toBuilder().build(); }

  /** Cópia com todos os valores limitados a [0, 100]. */
  public PlayerAttributes normalized() {
    PlayerAttributes c = copy();
    for (AttributeKey k : AttributeKey.values()) c.with(k, get(k));
    return c;
  }

  /** Todos os subatributos iguais a {@code value} (limitado a [0, 100]). */
  public static PlayerAttributes uniform(int value) {
    PlayerAttributes a = new PlayerAttributes();
    for (AttributeKey k : AttributeKey.values()) a.with(k, value);
    return a;
  }

  /** Mapa por subatributo na ordem canônica. */
  public Map<AttributeKey, Integer> asMap() {
    Map<AttributeKey, Integer> m = new EnumMap<>(AttributeKey.class);
    for (AttributeKey k : AttributeKey.values()) m.put(k, get(k));
    return m;
  }

  /** Grupo → subatributo → valor, ordem estável (contrato JSON do editor e do card). */
  public Map<String, Map<String, Integer>> asGroupedMap() {
    Map<String, Map<String, Integer>> m = new LinkedHashMap<>();
    for (AttributeGroup g : AttributeGroup.values()) m.put(g.key(), new LinkedHashMap<>());
    for (AttributeKey k : AttributeKey.values()) m.get(k.group().key()).put(k.key(), get(k));
    return m;
  }

  public static int clamp(int v) { return Math.max(0, Math.min(100, v)); }
}
