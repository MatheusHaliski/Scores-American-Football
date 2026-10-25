package br.com.velion.scoresaf.domain.catalog;

import java.util.Locale;

/**
 * Os 49 subatributos do jogador (docs/01 §3.4), 0–100, na ordem canônica: é a ordem das colunas, do JSON do editor e da soma
 * ponderada do overall e dos macros (a mesma ordem no port do front, {@code frontend/src/lib/ratings/overall.ts}). O {@code key} é o nome
 * no contrato JSON; a coluna é {@code attr_<snake_case>} (o prefixo evita palavras reservadas do MySQL, como {@code release}).
 */
public enum AttributeKey {
  /** Velocidade */
  SPEED(AttributeGroup.PHYSICAL, "speed"),
  /** Aceleração */
  ACCELERATION(AttributeGroup.PHYSICAL, "acceleration"),
  /** Agilidade */
  AGILITY(AttributeGroup.PHYSICAL, "agility"),
  /** Força */
  STRENGTH(AttributeGroup.PHYSICAL, "strength"),
  /** Impulsão */
  JUMPING(AttributeGroup.PHYSICAL, "jumping"),
  /** Resistência */
  STAMINA(AttributeGroup.PHYSICAL, "stamina"),
  /** Durabilidade */
  DURABILITY(AttributeGroup.PHYSICAL, "durability"),
  /** Precisão curta */
  SHORT_ACCURACY(AttributeGroup.PASSING, "shortAccuracy"),
  /** Precisão média */
  MEDIUM_ACCURACY(AttributeGroup.PASSING, "mediumAccuracy"),
  /** Precisão longa */
  DEEP_ACCURACY(AttributeGroup.PASSING, "deepAccuracy"),
  /** Força de braço */
  THROW_POWER(AttributeGroup.PASSING, "throwPower"),
  /** Passe em movimento */
  THROW_ON_RUN(AttributeGroup.PASSING, "throwOnRun"),
  /** Play-action */
  PLAY_ACTION(AttributeGroup.PASSING, "playAction"),
  /** Leitura de defesa */
  READ_DEFENSE(AttributeGroup.PASSING, "readDefense"),
  /** Calma sob pressão */
  POISE(AttributeGroup.PASSING, "poise"),
  /** Visão de corrida */
  VISION(AttributeGroup.RUSHING, "vision"),
  /** Quebra de tackle */
  BREAK_TACKLE(AttributeGroup.RUSHING, "breakTackle"),
  /** Juke */
  JUKE(AttributeGroup.RUSHING, "juke"),
  /** Spin */
  SPIN(AttributeGroup.RUSHING, "spin"),
  /** Stiff arm */
  STIFF_ARM(AttributeGroup.RUSHING, "stiffArm"),
  /** Proteção da bola */
  BALL_SECURITY(AttributeGroup.RUSHING, "ballSecurity"),
  /** Carregar (ganho após o contato) */
  CARRYING(AttributeGroup.RUSHING, "carrying"),
  /** Mãos */
  HANDS(AttributeGroup.RECEIVING, "hands"),
  /** Recepção em tráfego */
  CATCH_IN_TRAFFIC(AttributeGroup.RECEIVING, "catchInTraffic"),
  /** Recepção espetacular */
  SPECTACULAR_CATCH(AttributeGroup.RECEIVING, "spectacularCatch"),
  /** Rota curta */
  SHORT_ROUTE(AttributeGroup.RECEIVING, "shortRoute"),
  /** Rota média */
  MEDIUM_ROUTE(AttributeGroup.RECEIVING, "mediumRoute"),
  /** Rota funda */
  DEEP_ROUTE(AttributeGroup.RECEIVING, "deepRoute"),
  /** Soltura */
  RELEASE(AttributeGroup.RECEIVING, "release"),
  /** Bloqueio de passe */
  PASS_BLOCK(AttributeGroup.BLOCKING, "passBlock"),
  /** Bloqueio de corrida */
  RUN_BLOCK(AttributeGroup.BLOCKING, "runBlock"),
  /** Técnica de bloqueio */
  BLOCK_TECHNIQUE(AttributeGroup.BLOCKING, "blockTechnique"),
  /** Bloqueio de finesse */
  BLOCK_FINESSE(AttributeGroup.BLOCKING, "blockFinesse"),
  /** Bloqueio de potência */
  BLOCK_POWER(AttributeGroup.BLOCKING, "blockPower"),
  /** Tackle */
  TACKLE(AttributeGroup.DEFENSE, "tackle"),
  /** Poder de impacto */
  HIT_POWER(AttributeGroup.DEFENSE, "hitPower"),
  /** Perseguição */
  PURSUIT(AttributeGroup.DEFENSE, "pursuit"),
  /** Leitura de jogada */
  PLAY_RECOGNITION(AttributeGroup.DEFENSE, "playRecognition"),
  /** Cobertura individual */
  MAN_COVERAGE(AttributeGroup.DEFENSE, "manCoverage"),
  /** Cobertura por zona */
  ZONE_COVERAGE(AttributeGroup.DEFENSE, "zoneCoverage"),
  /** Press */
  PRESS(AttributeGroup.DEFENSE, "press"),
  /** Pass rush de finesse */
  FINESSE_MOVES(AttributeGroup.DEFENSE, "finesseMoves"),
  /** Pass rush de potência */
  POWER_MOVES(AttributeGroup.DEFENSE, "powerMoves"),
  /** Desvencilhar-se do bloqueio */
  BLOCK_SHEDDING(AttributeGroup.DEFENSE, "blockShedding"),
  /** Potência de chute */
  KICK_POWER(AttributeGroup.KICKING, "kickPower"),
  /** Precisão de chute */
  KICK_ACCURACY(AttributeGroup.KICKING, "kickAccuracy"),
  /** Retorno de chute */
  KICK_RETURN(AttributeGroup.KICKING, "kickReturn"),
  /** Consciência */
  AWARENESS(AttributeGroup.MENTAL, "awareness"),
  /** Liderança */
  LEADERSHIP(AttributeGroup.MENTAL, "leadership");

  private final AttributeGroup group;
  private final String key;

  AttributeKey(AttributeGroup group, String key) { this.group = group; this.key = key; }

  public AttributeGroup group() { return group; }
  public String key() { return key; }

  /** Pelo nome do contrato ({@code shortAccuracy}) ou da constante ({@code SHORT_ACCURACY}); desconhecido devolve {@code null}. */
  public static AttributeKey parse(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    for (AttributeKey a : values()) if (a.key.equals(s) || a.name().equals(s.toUpperCase(Locale.ROOT))) return a;
    return null;
  }
}
