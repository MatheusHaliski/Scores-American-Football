package br.com.velion.scoresaf.application.seed;

import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.catalog.Division;
import br.com.velion.scoresaf.domain.catalog.HelmetDesign;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.Stadium;
import br.com.velion.scoresaf.domain.catalog.StadiumSector;
import br.com.velion.scoresaf.domain.catalog.Uniform;
import br.com.velion.scoresaf.domain.catalog.World;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Grafo completo de um mundo (o padrão, do {@link DefaultWorldFactory}, ou uma cópia), na ordem de gravação das FKs. */
public record DefaultWorld(
    World world,
    List<League> leagues,
    List<Conference> conferences,
    List<Division> divisions,
    List<ManagerProfile> managers,
    List<Stadium> stadiums,
    List<StadiumSector> sectors,
    List<Club> clubs,
    List<Uniform> uniforms,
    List<HelmetDesign> helmets,
    List<Player> players) {

  public Map<String, Integer> counts() {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put("leagues", leagues.size()); m.put("conferences", conferences.size()); m.put("divisions", divisions.size());
    m.put("clubs", clubs.size()); m.put("stadiums", stadiums.size()); m.put("sectors", sectors.size()); m.put("uniforms", uniforms.size());
    m.put("helmets", helmets.size()); m.put("managers", managers.size()); m.put("players", players.size());
    return m;
  }
}
