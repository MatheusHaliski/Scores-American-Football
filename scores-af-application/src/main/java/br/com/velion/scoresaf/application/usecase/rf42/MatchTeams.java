package br.com.velion.scoresaf.application.usecase.rf42;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.Stadium;
import br.com.velion.scoresaf.domain.engine.model.DepthChart;
import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.PlayerSnapshot;
import br.com.velion.scoresaf.domain.engine.model.RulesSnapshot;
import br.com.velion.scoresaf.domain.engine.model.TeamSnapshot;
import br.com.velion.scoresaf.domain.engine.model.Weather;
import br.com.velion.scoresaf.domain.game.Ruleset;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.shared.CoachStyle;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Do catálogo (bloco 4) para o motor (bloco 5): franquia + elenco → {@link TeamSnapshot}, liga → {@link RulesSnapshot}, estádio →
 * clima. Os ativos do dia de jogo são os {@code gameDayActives} (46) melhores do elenco ativo, sem suspensos, sempre com K, P e LS;
 * o depth chart sai automático do elenco, ou do depth chart salvo da franquia quando houver (RF60, bloco 6a). O plano de jogo vem
 * do estilo do head coach.
 */
public final class MatchTeams {
  private MatchTeams() {}

  public static TeamSnapshot team(Club club, List<Player> roster, int gameDayActives) { return team(club, roster, gameDayActives, null); }

  /**
   * Com depth chart salvo (RF60, bloco 6a): os jogadores do depth chart entram primeiro nos ativos do dia de jogo (na ordem das vagas)
   * e o depth chart vai para o motor, que completa pelo automático só as vagas vazias ({@link DepthChart#resolve}). Jogador do depth
   * chart que deixou de estar disponível (dispensado, IR, suspenso) é ignorado.
   */
  public static TeamSnapshot team(Club club, List<Player> roster, int gameDayActives, DepthChart saved) {
    List<Player> eligible = roster.stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getSuspensionWeeks() == 0)
        .sorted(Comparator.comparingInt(Player::getOverall).reversed().thenComparing(Player::getJerseyNumber)).toList();
    int limit = Math.max(gameDayActives, 22);
    List<Player> actives = new ArrayList<>();
    if (saved != null) {
      Map<UUID, Player> byId = new LinkedHashMap<>();
      eligible.forEach(p -> byId.put(p.getId(), p));
      for (List<UUID> ids : saved.slots().values()) {
        for (UUID id : ids) {
          Player p = byId.get(id);
          if (p != null && !actives.contains(p) && actives.size() < limit) actives.add(p);
        }
      }
    }
    for (Player p : eligible) {
      boolean specialist = p.getPosition() == Position.K || p.getPosition() == Position.P || p.getPosition() == Position.LS;
      if (specialist && !actives.contains(p) && actives.size() < limit) actives.add(p);
    }
    for (Player p : eligible) if (!actives.contains(p) && actives.size() < limit) actives.add(p);
    List<PlayerSnapshot> players = actives.stream().map(PlayerSnapshot::of).toList();
    return new TeamSnapshot(club.getId(), club.getName(), club.getShortName(), players, saved, plan(club.getManager()));
  }

  /** Plano de jogo padrão pelo estilo do head coach: ofensivo passa e arrisca mais; defensivo corre, segura e pressiona. */
  public static GamePlan plan(ManagerProfile manager) {
    GamePlan base = GamePlan.defaults();
    if (manager == null) return base;
    base = base.withBaseDefense(manager.getPreferredDefense());
    CoachStyle style = manager.getCoachStyle();
    if (style == CoachStyle.OFFENSIVE) {
      return new GamePlan(58, GamePlan.Aggression.AGGRESSIVE, base.tempo(), base.defensiveFocus(), base.blitzFrequency(), base.baseDefense());
    }
    if (style == CoachStyle.DEFENSIVE) {
      return new GamePlan(52, GamePlan.Aggression.CONSERVATIVE, base.tempo(), base.defensiveFocus(), GamePlan.Emphasis.HIGH, base.baseDefense());
    }
    return base;
  }

  public static RulesSnapshot rules(League league) {
    if (league == null) return RulesSnapshot.pro();
    LeagueRules r = league.getRules() == null ? LeagueRules.defaults(league.getRuleset()) : league.getRules();
    return RulesSnapshot.of(r, league.getRuleset() == null ? Ruleset.PRO : league.getRuleset());
  }

  public static Weather weather(Stadium stadium, long seed) {
    return stadium == null ? Weather.CLEAR : Weather.roll(stadium.getRoof(), stadium.getClimateZone(), seed);
  }

  /** Partida IA × IA entre duas franquias, com as regras da liga do mandante e o clima do estádio dele. */
  public static MatchSetup setup(UUID fixtureId, Club home, List<Player> homeRoster, Club away, List<Player> awayRoster, long seed) {
    return setup(fixtureId, home, homeRoster, null, away, awayRoster, null, seed);
  }

  /** Igual, com o depth chart salvo de cada lado ({@code null} = automático). */
  public static MatchSetup setup(UUID fixtureId, Club home, List<Player> homeRoster, DepthChart homeChart, Club away, List<Player> awayRoster,
                                 DepthChart awayChart, long seed) {
    RulesSnapshot rules = rules(home.getLeague());
    int actives = home.getLeague() == null || home.getLeague().getRules() == null ? 46 : home.getLeague().getRules().getGameDayActives();
    return new MatchSetup(fixtureId, team(home, homeRoster, actives, homeChart), team(away, awayRoster, actives, awayChart), rules,
        weather(home.getStadium(), seed), seed, PlayCallingLevel.NONE, null, false, false);
  }
}
