package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.catalog.AttributeGroup;
import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.PlayerSnapshot;
import br.com.velion.scoresaf.domain.engine.model.RulesSnapshot;
import br.com.velion.scoresaf.domain.engine.model.TeamSnapshot;
import br.com.velion.scoresaf.domain.engine.model.Weather;
import br.com.velion.scoresaf.domain.engine.ratings.OverallCalculator;
import br.com.velion.scoresaf.domain.engine.ratings.OverallWeights;
import br.com.velion.scoresaf.domain.shared.Position;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Elencos sintéticos para os testes do domínio, gerados como o mundo semente (DefaultWorldFactory, na aplicação): 53 jogadores na
 * mesma distribuição de posições, alvo de overall por profundidade e o mesmo perfil de atributos por posição. O teste de
 * plausibilidade com as franquias do mundo semente de verdade fica na aplicação.
 */
final class TestTeams {
  private TestTeams() {}

  static final Map<Position, Integer> ROSTER = new EnumMap<>(Map.ofEntries(
      Map.entry(Position.QB, 3), Map.entry(Position.RB, 3), Map.entry(Position.FB, 1), Map.entry(Position.WR, 6), Map.entry(Position.TE, 3),
      Map.entry(Position.LT, 2), Map.entry(Position.LG, 2), Map.entry(Position.C, 2), Map.entry(Position.RG, 1), Map.entry(Position.RT, 2),
      Map.entry(Position.DE, 5), Map.entry(Position.DT, 4), Map.entry(Position.OLB, 4), Map.entry(Position.MLB, 2), Map.entry(Position.CB, 6),
      Map.entry(Position.FS, 2), Map.entry(Position.SS, 2), Map.entry(Position.K, 1), Map.entry(Position.P, 1), Map.entry(Position.LS, 1)));

  /** Equipe com "prestígio" {@code prestige} (o mundo semente vai de ~55 a ~85): titulares por volta de 60 + 0,2 × prestígio + 5. */
  static TeamSnapshot team(String name, int prestige, long seed) {
    SplittableRandom rng = new SplittableRandom(seed);
    double base = 60 + prestige * 0.2;
    List<PlayerSnapshot> players = new ArrayList<>();
    int n = 0;
    for (Position pos : Position.values()) {
      int count = ROSTER.getOrDefault(pos, 0);
      for (int rank = 0; rank < count; rank++) {
        int offset = rank == 0 ? 5 : rank == 1 ? 0 : rank == 2 ? -5 : -8;
        int star = rank == 0 && rng.nextInt(100) < 12 ? rng.nextInt(3, 10) : 0;
        int target = clamp((int) Math.round(base + offset + star + rng.nextInt(-4, 5)), 45, 97);
        int age = 21 + rng.nextInt(0, 14);
        players.add(new PlayerSnapshot(uuid(rng), name + " " + pos + (rank + 1), ++n, pos, null, attributes(pos, target, age, rng),
            88 + rng.nextInt(0, 13), 50 + rng.nextInt(0, 31)));
      }
    }
    return new TeamSnapshot(uuid(rng), name, name.substring(0, Math.min(3, name.length())).toUpperCase(), players, null, null);
  }

  /** Mesmo perfil do mundo semente: muito peso no overall da posição → acima do alvo; nenhum peso → bem abaixo. */
  static Map<AttributeKey, Integer> attributes(Position pos, int target, int age, SplittableRandom rng) {
    Map<AttributeKey, Integer> w = OverallWeights.V1.of(pos);
    PlayerAttributes a = new PlayerAttributes();
    for (AttributeKey k : AttributeKey.values()) {
      int weight = w.getOrDefault(k, 0);
      int offset;
      if (weight >= 100) offset = 4;
      else if (weight >= 50) offset = 1;
      else if (weight > 0) offset = -3;
      else if (k.group() == AttributeGroup.PHYSICAL) offset = -8;
      else if (k.group() == AttributeGroup.MENTAL) offset = -6;
      else offset = -24;
      a.with(k, target + offset + rng.nextInt(-5, 6));
    }
    if (age <= 23) { a.with(AttributeKey.AWARENESS, a.getAwareness() - 5); a.with(AttributeKey.SPEED, a.getSpeed() + 2); }
    else if (age >= 32) { a.with(AttributeKey.SPEED, a.getSpeed() - 5); a.with(AttributeKey.AWARENESS, a.getAwareness() + 5); }
    for (int i = 0; i < 6; i++) {
      int delta = target - OverallCalculator.overall(a, pos);
      if (delta == 0) break;
      for (AttributeKey k : AttributeKey.values()) a.with(k, a.get(k) + delta);
    }
    Map<AttributeKey, Integer> out = new EnumMap<>(AttributeKey.class);
    for (AttributeKey k : AttributeKey.values()) out.put(k, a.get(k));
    return out;
  }

  /** Copia a equipe somando {@code delta} a todos os atributos de todos os jogadores. */
  static TeamSnapshot shifted(TeamSnapshot t, int delta) {
    List<PlayerSnapshot> ps = new ArrayList<>();
    for (PlayerSnapshot p : t.players()) {
      Map<AttributeKey, Integer> a = new EnumMap<>(AttributeKey.class);
      p.attributes().forEach((k, v) -> a.put(k, v + delta));
      ps.add(new PlayerSnapshot(p.id(), p.name(), p.jerseyNumber(), p.position(), p.secondaryPosition(), a, p.condition(), p.morale()));
    }
    return t.withPlayers(ps);
  }

  static MatchSetup setup(TeamSnapshot home, TeamSnapshot away, RulesSnapshot rules, long seed) {
    return new MatchSetup(new UUID(0, seed), home, away, rules, Weather.CLEAR, seed, PlayCallingLevel.NONE, null, false, false);
  }

  static MatchSetup pro(long seed) {
    return setup(team("Trovoes", 70, 1001), team("Corsarios", 70, 2002), RulesSnapshot.pro(), seed);
  }

  private static UUID uuid(SplittableRandom rng) {
    long msb = (rng.nextLong() & 0xffffffffffff0fffL) | 0x0000000000004000L;
    long lsb = (rng.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }

  private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
