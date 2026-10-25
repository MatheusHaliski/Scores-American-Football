package br.com.velion.scoresaf.application.usecase.rf07;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.dto.MatchDtos;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSettingsRepository;
import br.com.velion.scoresaf.application.service.DepthChartService;
import br.com.velion.scoresaf.application.service.GamePlanService;
import br.com.velion.scoresaf.application.usecase.rf42.MatchTeams;
import br.com.velion.scoresaf.application.usecase.rf60.DepthChartUseCases;
import br.com.velion.scoresaf.application.usecase.rf61.GamePlanUseCases;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.account.UserSettings;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.Stadium;
import br.com.velion.scoresaf.domain.engine.model.DepthChart;
import br.com.velion.scoresaf.domain.engine.model.DepthSlot;
import br.com.velion.scoresaf.domain.engine.model.PlayerSnapshot;
import br.com.velion.scoresaf.domain.engine.model.TeamSnapshot;
import br.com.velion.scoresaf.domain.engine.ratings.CardTier;
import br.com.velion.scoresaf.domain.match.DepthChartPolicy;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.FixtureStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF7 — preparar o time para o próximo jogo ({@code TacticsController.preparation} do basquete): o jogo, o adversário e as campanhas,
 * o clima previsto (o mesmo sorteio da partida), a média dos titulares por unidade, os confrontos-chave dos dois lados (WR × CB, OL × DL,
 * QB × pass rush), os jogadores-chave, a prontidão do depth chart e o resumo do plano de jogo e do playbook. Os titulares são os do
 * depth chart que o motor vai usar (o salvo da franquia, completado pelo automático; o automático no adversário), entre os ativos do
 * dia de jogo. Sem próximo jogo na temporada: 409 NO_NEXT_GAME.
 */
@Service
public class GetPreparationUseCase {
  /** Diferença de média a partir da qual um lado leva o confronto. */
  static final int EDGE = 3;
  private static final int KEY_PLAYERS = 3;

  private final CareerSupport support; private final FixtureRepository fixtures; private final PlayerRepository players; private final DepthChartService depthCharts;
  private final GamePlanService plans; private final MatchSessionRepository sessions; private final UserSettingsRepository settings;

  public GetPreparationUseCase(CareerSupport support, FixtureRepository fixtures, PlayerRepository players, DepthChartService depthCharts,
                               GamePlanService plans, MatchSessionRepository sessions, UserSettingsRepository settings) {
    this.support = support; this.fixtures = fixtures; this.players = players; this.depthCharts = depthCharts; this.plans = plans; this.sessions = sessions;
    this.settings = settings;
  }

  @Transactional
  public MatchDtos.PreparationDto execute(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    Club club = support.userClub(save);
    Season season = save.getSeason();
    if (season == null) throw new BusinessRuleException("NO_SEASON", "Carreira sem temporada");
    List<Fixture> all = fixtures.findBySeasonId(season.getId()).stream().sorted(CareerAssembler.FIXTURE_ORDER).toList();
    Fixture next = all.stream().filter(f -> f.involves(club.getId()) && f.getStatus() != FixtureStatus.FINISHED).findFirst()
        .orElseThrow(() -> new ConflictException("NO_NEXT_GAME", "Não há próximo jogo nesta temporada"));
    boolean home = next.getHomeClub().getId().equals(club.getId());
    Club opp = home ? next.getAwayClub() : next.getHomeClub();
    Map<UUID, CareerAssembler.Tally> tallies = CareerAssembler.tallies(all);
    LeagueRules rules = CareerSupport.rules(save);
    int actives = rules.getGameDayActives();

    List<Player> ourRoster = players.findByClubIdOrderByOverallDesc(club.getId());
    var dc = depthCharts.getOrCreate(save, club, actives);
    Map<br.com.velion.scoresaf.domain.match.ChartSlot, List<UUID>> chart = depthCharts.read(dc);
    TeamSnapshot ours = MatchTeams.team(club, ourRoster, actives, DepthChartPolicy.toEngine(chart));
    TeamSnapshot theirs = MatchTeams.team(opp, players.findByClubIdOrderByOverallDesc(opp.getId()), actives, depthCharts.forClub(opp.getId()));
    Lineup us = new Lineup(ours), them = new Lineup(theirs);

    List<MatchDtos.UnitComparisonDto> units = List.of(unit("OFFENSE", us.offense(), them.offense()), unit("DEFENSE", us.defense(), them.defense()),
        unit("SPECIAL_TEAMS", us.special(), them.special()));
    List<MatchDtos.MatchupDto> matchups = List.of(
        matchup("WR_VS_CB", "OUR_OFFENSE", us.wr(), them.cb()),
        matchup("OL_VS_DL", "OUR_OFFENSE", us.ol(), them.dl()),
        matchup("QB_VS_PASS_RUSH", "OUR_OFFENSE", us.qb(), them.rush()),
        matchup("WR_VS_CB", "THEIR_OFFENSE", us.cb(), them.wr()),
        matchup("OL_VS_DL", "THEIR_OFFENSE", us.dl(), them.ol()),
        matchup("QB_VS_PASS_RUSH", "THEIR_OFFENSE", us.rush(), them.qb()));

    DepthChartPolicy.Result r = DepthChartPolicy.validate(chart, DepthChartService.byId(ourRoster), club.getId(), actives);
    MatchDtos.ReadinessDto readiness = new MatchDtos.ReadinessDto(r.valid(), dc.currentVersion(), DepthChartUseCases.issues(r.errors()),
        DepthChartUseCases.issues(r.warnings()));
    Stadium st = next.getHomeClub().getStadium();
    MatchDtos.WeatherDto weather = new MatchDtos.WeatherDto(MatchTeams.weather(st, next.getSeed()).name(), st == null ? null : st.getName(),
        st == null ? null : st.getRoof().name(), st == null ? null : st.getSurface().name());
    PlayCallingLevel level = settings.findByUserId(userId).map(UserSettings::getPlayCallingLevel).orElse(PlayCallingLevel.KEY_MOMENTS);
    UUID matchId = sessions.findByFixtureId(next.getId()).map(s -> s.getId()).orElse(null);
    return new MatchDtos.PreparationDto(CareerAssembler.fixture(next, club.getId()), home, CareerAssembler.club(club), CareerAssembler.club(opp),
        CareerAssembler.tally(tallies, club.getId()).record(), CareerAssembler.tally(tallies, opp.getId()).record(), weather, units, matchups,
        keyPlayers(us), keyPlayers(them), readiness, GamePlanUseCases.planDto(plans.planOrCreate(save, club), club, save.isActive()),
        GamePlanService.customWeights(plans.read(plans.playbookOrCreate(save, club))), level.name(), matchId);
  }

  /** Titulares por vaga no depth chart efetivo (sem repetir jogador entre as vagas da mesma conta). */
  record Lineup(TeamSnapshot team, DepthChart chart, Map<UUID, PlayerSnapshot> byId) {
    Lineup(TeamSnapshot team) { this(team, team.resolvedDepthChart(), index(team)); }

    private static Map<UUID, PlayerSnapshot> index(TeamSnapshot t) {
      Map<UUID, PlayerSnapshot> m = new HashMap<>();
      t.players().forEach(p -> m.put(p.id(), p));
      return m;
    }

    List<Slotted> pick(Object... slotsAndCounts) {
      List<Slotted> out = new ArrayList<>();
      Set<UUID> used = new LinkedHashSet<>();
      for (int i = 0; i < slotsAndCounts.length; i += 2) {
        DepthSlot slot = (DepthSlot) slotsAndCounts[i];
        int n = (Integer) slotsAndCounts[i + 1];
        int taken = 0;
        for (UUID id : chart.get(slot)) {
          if (taken >= n) break;
          PlayerSnapshot p = byId.get(id);
          if (p == null || !used.add(id)) continue;
          out.add(new Slotted(p, slot.position() == null ? p.position() : slot.position()));
          taken++;
        }
      }
      return out;
    }

    List<Slotted> qb() { return pick(DepthSlot.QB, 1); }
    List<Slotted> wr() { return pick(DepthSlot.WR, 3); }
    List<Slotted> ol() { return pick(DepthSlot.LT, 1, DepthSlot.LG, 1, DepthSlot.C, 1, DepthSlot.RG, 1, DepthSlot.RT, 1); }
    List<Slotted> dl() { return pick(DepthSlot.DE, 2, DepthSlot.DT, 2); }
    List<Slotted> rush() { return pick(DepthSlot.DE, 2, DepthSlot.OLB, 1); }
    List<Slotted> cb() { return pick(DepthSlot.CB, 3); }
    List<Slotted> offense() { return pick(DepthSlot.QB, 1, DepthSlot.RB, 1, DepthSlot.WR, 3, DepthSlot.TE, 1, DepthSlot.LT, 1, DepthSlot.LG, 1, DepthSlot.C, 1,
        DepthSlot.RG, 1, DepthSlot.RT, 1); }
    List<Slotted> defense() { return pick(DepthSlot.DE, 2, DepthSlot.DT, 2, DepthSlot.OLB, 2, DepthSlot.MLB, 1, DepthSlot.CB, 2, DepthSlot.FS, 1, DepthSlot.SS, 1); }
    List<Slotted> special() { return pick(DepthSlot.K, 1, DepthSlot.P, 1, DepthSlot.LS, 1); }
  }

  /** Jogador na vaga e o overall dele naquela posição. */
  record Slotted(PlayerSnapshot player, br.com.velion.scoresaf.domain.shared.Position at) {
    int overall() { return player.overallAt(at); }
    MatchDtos.PlayerRefDto ref() {
      int ovr = overall();
      return new MatchDtos.PlayerRefDto(player.id(), player.name(), at.name(), player.jerseyNumber(), ovr, CardTier.of(ovr).name());
    }
  }

  static int average(List<Slotted> ps) { return ps.isEmpty() ? 0 : (int) Math.round(ps.stream().mapToInt(Slotted::overall).average().orElse(0)); }

  static MatchDtos.UnitComparisonDto unit(String unit, List<Slotted> ours, List<Slotted> theirs) {
    int a = average(ours), b = average(theirs);
    return new MatchDtos.UnitComparisonDto(unit, a, b, a - b);
  }

  static MatchDtos.MatchupDto matchup(String key, String side, List<Slotted> ours, List<Slotted> theirs) {
    int a = average(ours), b = average(theirs), edge = a - b;
    String adv = edge >= EDGE ? "OURS" : edge <= -EDGE ? "THEIRS" : "EVEN";
    return new MatchDtos.MatchupDto(key, side, a, b, edge, adv, ours.stream().map(Slotted::ref).toList(), theirs.stream().map(Slotted::ref).toList());
  }

  /** Os {@value #KEY_PLAYERS} melhores titulares (ataque e defesa) pelo overall na vaga. */
  static List<MatchDtos.PlayerRefDto> keyPlayers(Lineup l) {
    List<Slotted> starters = new ArrayList<>(l.offense());
    starters.addAll(l.defense());
    return starters.stream().sorted(Comparator.comparingInt(Slotted::overall).reversed().thenComparingInt(s -> s.player().jerseyNumber()))
        .limit(KEY_PLAYERS).map(Slotted::ref).toList();
  }
}
