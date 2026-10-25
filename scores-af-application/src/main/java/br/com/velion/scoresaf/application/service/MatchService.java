package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.application.dto.MatchDtos;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchReportRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerMatchStatsRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore;
import br.com.velion.scoresaf.domain.engine.sim.DefaultMatchSimulator;
import br.com.velion.scoresaf.domain.engine.sim.MatchSimulator;
import br.com.velion.scoresaf.domain.match.MatchReport;
import br.com.velion.scoresaf.domain.match.PlayerMatchStats;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.shared.FixtureStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Simulação e persistência de partidas (RF27/RF31/RF42), como o {@code MatchService} do basquete: simula os jogos da IA inteiros e, ao
 * fim de qualquer partida, grava o placar no {@link Fixture} (a tabela da divisão é calculada dos jogos encerrados, D-48), o
 * {@link MatchReport} com o box score completo, as linhas de {@link PlayerMatchStats}, a campanha dos head coaches e a auditoria
 * {@code MATCH_SIMULATED} (semente e placar). Público e renda entram com o RF31 (bloco 11).
 */
@Service
public class MatchService {
  private final MatchSimulator simulator = new DefaultMatchSimulator();
  private final MatchSetupAssembler assembler; private final FixtureRepository fixtures; private final PlayerRepository players;
  private final PlayerMatchStatsRepository stats; private final MatchReportRepository reports; private final AuditService audit;

  public MatchService(MatchSetupAssembler assembler, FixtureRepository fixtures, PlayerRepository players, PlayerMatchStatsRepository stats,
                      MatchReportRepository reports, AuditService audit) {
    this.assembler = assembler; this.fixtures = fixtures; this.players = players; this.stats = stats; this.reports = reports; this.audit = audit;
  }

  public MatchSimulator simulator() { return simulator; }

  /** Jogo da IA: simula inteiro e grava. */
  public MatchResult simulateFull(CareerSave save, Fixture f) {
    MatchSetup setup = assembler.build(save, f);
    MatchResult r = simulator.simulate(setup);
    persistResult(save, f, setup, r, false);
    return r;
  }

  public MatchReport persistResult(CareerSave save, Fixture f, MatchSetup setup, MatchResult r, boolean userGame) {
    f.setStatus(FixtureStatus.FINISHED);
    f.setHomeScore(r.homeScore());
    f.setAwayScore(r.awayScore());
    f.setOvertime(r.overtime());
    fixtures.save(f);

    List<PlayerBoxScore> all = new ArrayList<>(r.homePlayers());
    all.addAll(r.awayPlayers());
    Map<UUID, Player> byId = players.findAllById(all.stream().map(PlayerBoxScore::playerId).toList()).stream()
        .collect(Collectors.toMap(Player::getId, Function.identity()));
    List<PlayerMatchStats> rows = new ArrayList<>();
    for (PlayerBoxScore b : all) {
      Player p = byId.get(b.playerId());
      if (p == null) continue;
      rows.add(row(save, f, p, b.clubId().equals(f.getHomeClub().getId()) ? f.getHomeClub() : f.getAwayClub(), b));
    }
    stats.saveAll(rows);

    record(f.getHomeClub(), r.homeScore(), r.awayScore());
    record(f.getAwayClub(), r.awayScore(), r.homeScore());

    MatchReport rep = new MatchReport();
    rep.setFixture(f); rep.setCareer(save); rep.setHomeScore(r.homeScore()); rep.setAwayScore(r.awayScore()); rep.setOvertime(r.overtime());
    rep.setWinnerClubId(r.winnerClubId()); rep.setSeed(r.seed()); rep.setEngineVersion(r.engineVersion()); rep.setRulesVersion(r.rulesVersion());
    rep.setPlayCount((int) r.playCount()); rep.setUserGame(userGame);
    MatchDtos.ReportData data = new MatchDtos.ReportData(setup.weather().name(), r.homeQuarters(), r.awayQuarters(), r.homeTeam(), r.awayTeam(),
        r.homePlayers(), r.awayPlayers(), r.drives(), r.events().stream().filter(e -> e.points() > 0).toList(), r.decisionsLog());
    try {
      rep.setReportJson(Jsons.MAPPER.writeValueAsString(data));
    } catch (Exception e) {
      throw new IllegalStateException("relatório da partida não serializou", e);
    }
    reports.save(rep);
    audit.ok(AuditActions.MATCH_SIMULATED, "Fixture", f.getId(), "seed=" + r.seed() + " placar=" + f.getHomeClub().getShortName() + " " + r.homeScore()
        + "x" + r.awayScore() + " " + f.getAwayClub().getShortName() + (userGame ? " (usuário)" : ""));
    return rep;
  }

  /** Campanha do head coach da franquia (empate conta jogo, não vitória). */
  private static void record(Club club, int pointsFor, int pointsAgainst) {
    ManagerProfile m = club.getManager();
    if (m == null) return;
    m.setCareerGames(m.getCareerGames() + 1);
    if (pointsFor > pointsAgainst) m.setCareerWins(m.getCareerWins() + 1);
  }

  static PlayerMatchStats row(CareerSave save, Fixture f, Player p, Club club, PlayerBoxScore b) {
    PlayerMatchStats s = new PlayerMatchStats();
    s.setFixture(f); s.setCareer(save); s.setPlayer(p); s.setClub(club); s.setSnaps(b.snaps());
    if (b.passing() != null) {
      s.setPassCompletions(b.passing().completions()); s.setPassAttempts(b.passing().attempts()); s.setPassYards(b.passing().yards());
      s.setPassTouchdowns(b.passing().touchdowns()); s.setInterceptionsThrown(b.passing().interceptions()); s.setSacked(b.passing().sacked());
    }
    if (b.rushing() != null) {
      s.setRushAttempts(b.rushing().attempts()); s.setRushYards(b.rushing().yards()); s.setRushTouchdowns(b.rushing().touchdowns());
      s.setFumblesLost(b.rushing().fumblesLost());
    }
    if (b.receiving() != null) {
      s.setTargets(b.receiving().targets()); s.setReceptions(b.receiving().receptions()); s.setReceivingYards(b.receiving().yards());
      s.setReceivingTouchdowns(b.receiving().touchdowns());
    }
    if (b.defense() != null) {
      s.setSoloTackles(b.defense().soloTackles()); s.setTacklesForLoss(b.defense().tacklesForLoss()); s.setSacks(b.defense().sacks());
      s.setInterceptions(b.defense().interceptions()); s.setPassesDefended(b.defense().passesDefended()); s.setForcedFumbles(b.defense().forcedFumbles());
      s.setFumbleRecoveries(b.defense().fumbleRecoveries());
    }
    if (b.kicking() != null) {
      s.setFieldGoalsMade(b.kicking().fgMade()); s.setFieldGoalAttempts(b.kicking().fgAttempts()); s.setExtraPointsMade(b.kicking().patMade());
      s.setExtraPointAttempts(b.kicking().patAttempts());
    }
    if (b.punting() != null) { s.setPunts(b.punting().punts()); s.setPuntYards(b.punting().yards()); }
    return s;
  }
}
