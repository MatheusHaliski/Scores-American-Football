package br.com.velion.scoresaf.application.usecase.rf42;

import br.com.velion.scoresaf.application.seed.DefaultWorld;
import br.com.velion.scoresaf.application.seed.DefaultWorldFactory;
import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.PlayerSnapshot;
import br.com.velion.scoresaf.domain.engine.model.TeamSnapshot;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mundo semente (bloco 4) para os testes do motor na aplicação: franquias reais, força pelos 22 melhores do elenco ativo. */
final class SeedMatches {
  private SeedMatches() {}

  private static DefaultWorld world;

  static synchronized DefaultWorld world() {
    if (world == null) world = new DefaultWorldFactory().build();
    return world;
  }

  static List<Player> roster(Club c) { return world().players().stream().filter(p -> p.getClub() == c).toList(); }

  static double strength(Club c) {
    return roster(c).stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE).map(Player::getOverall).sorted(Comparator.reverseOrder()).limit(22)
        .mapToInt(Integer::intValue).average().orElse(0);
  }

  /** Franquias da mais forte para a mais fraca. */
  static List<Club> byStrength() {
    List<Club> clubs = new ArrayList<>(world().clubs());
    clubs.sort(Comparator.comparingDouble(SeedMatches::strength).reversed().thenComparing(Club::getShortName));
    return clubs;
  }

  static Club club(String shortName) { return world().clubs().stream().filter(c -> shortName.equals(c.getShortName())).findFirst().orElseThrow(); }

  static MatchSetup setup(Club home, Club away, long seed) {
    return MatchTeams.setup(new UUID(0x5EEDL, seed), home, roster(home), away, roster(away), seed);
  }

  /** Copia a equipe somando {@code delta} a todos os atributos (equipe "muito mais fraca" para o teste de força). */
  static TeamSnapshot shifted(TeamSnapshot t, int delta) {
    List<PlayerSnapshot> ps = new ArrayList<>();
    for (PlayerSnapshot p : t.players()) {
      Map<AttributeKey, Integer> a = new EnumMap<>(AttributeKey.class);
      p.attributes().forEach((k, v) -> a.put(k, v + delta));
      ps.add(new PlayerSnapshot(p.id(), p.name(), p.jerseyNumber(), p.position(), p.secondaryPosition(), a, p.condition(), p.morale()));
    }
    return t.withPlayers(ps);
  }
}
