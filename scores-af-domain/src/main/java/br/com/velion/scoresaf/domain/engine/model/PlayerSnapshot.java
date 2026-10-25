package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import br.com.velion.scoresaf.domain.engine.ratings.OverallCalculator;
import br.com.velion.scoresaf.domain.shared.Position;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Jogador congelado para a partida: posição, os 49 subatributos (0–100), condição física e moral no início do jogo. O motor não lê
 * a entidade {@link Player}; o snapshot vai inteiro no estado serializado da partida.
 */
public record PlayerSnapshot(
    UUID id,
    String name,
    int jerseyNumber,
    Position position,
    Position secondaryPosition,
    Map<AttributeKey, Integer> attributes,
    int condition,
    int morale) {

  public PlayerSnapshot {
    Map<AttributeKey, Integer> copy = new EnumMap<>(AttributeKey.class);
    for (AttributeKey k : AttributeKey.values()) copy.put(k, clamp(attributes == null ? 50 : attributes.getOrDefault(k, 50)));
    attributes = Collections.unmodifiableMap(copy); // EnumMap: ordem canônica também no JSON
    condition = clamp(condition);
    morale = clamp(morale);
  }

  public static PlayerSnapshot of(Player p) {
    Map<AttributeKey, Integer> attrs = new EnumMap<>(AttributeKey.class);
    for (AttributeKey k : AttributeKey.values()) attrs.put(k, p.getAttributes().get(k));
    return new PlayerSnapshot(p.getId(), p.getName(), p.getJerseyNumber(), p.getPosition(), p.getSecondaryPosition(), attrs, p.getCondition(), p.getMorale());
  }

  public int attr(AttributeKey key) { return attributes.get(key); }

  /** Overall do jogador jogando na posição dada (pesos oficiais vigentes). */
  public int overallAt(Position playing) {
    PlayerAttributes a = new PlayerAttributes();
    for (AttributeKey k : AttributeKey.values()) a.with(k, attributes.get(k));
    return OverallCalculator.overall(a, playing);
  }

  private static int clamp(int v) { return Math.max(0, Math.min(100, v)); }
}
