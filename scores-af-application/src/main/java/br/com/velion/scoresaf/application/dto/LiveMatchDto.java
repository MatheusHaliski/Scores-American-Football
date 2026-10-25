package br.com.velion.scoresaf.application.dto;

import br.com.velion.scoresaf.domain.engine.model.Drive;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.MatchStatus;
import br.com.velion.scoresaf.domain.engine.model.PendingDecision;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore;
import br.com.velion.scoresaf.domain.engine.model.TeamBoxScore;
import br.com.velion.scoresaf.domain.engine.sim.BoxScores;
import java.util.List;
import java.util.UUID;

/**
 * Estado ao vivo da partida como a API vai entregar ao front (RF42 {@code GET /matches/{mid}}, bloco 6): placar, relógio, descida e
 * distância, bola na jarda absoluta, tempos técnicos, decisão aberta, os últimos lances e o box score parcial. A amostra dourada
 * {@code docs/validation/golden/live-match.json} é gravada com este DTO e o mesmo JSON da API ({@code Jsons}).
 */
public record LiveMatchDto(
    UUID fixtureId,
    long version,
    int engineVersion,
    int rulesVersion,
    String ruleset,
    String weather,
    MatchStatus status,
    int quarter,
    int clockSeconds,
    int playClockSeconds,
    UUID possessionClubId,
    int down,
    int distance,
    int ballOnAbsYd,
    TeamLine home,
    TeamLine away,
    PendingDecision decision,
    List<PlayEvent> lastEvents,
    Drive lastDrive,
    int driveCount) {

  /** Lado da partida: identidade, placar por quarto, tempos técnicos e box score parcial. */
  public record TeamLine(UUID clubId, String name, String shortName, int score, List<Integer> quarters, int timeouts, TeamBoxScore stats,
                         List<PlayerBoxScore> players) {}

  public static LiveMatchDto of(MatchSnapshot s, int lastEvents) {
    var setup = s.setup();
    TeamLine home = new TeamLine(setup.home().clubId(), setup.home().name(), setup.home().shortName(), s.homeScore(), s.homeQuarters(), s.homeTimeouts(),
        BoxScores.team(s, true), BoxScores.players(s, true));
    TeamLine away = new TeamLine(setup.away().clubId(), setup.away().name(), setup.away().shortName(), s.awayScore(), s.awayQuarters(), s.awayTimeouts(),
        BoxScores.team(s, false), BoxScores.players(s, false));
    List<Drive> drives = s.drives();
    return new LiveMatchDto(setup.fixtureId(), s.version(), s.engineVersion(), setup.rules().version(), setup.rules().ruleset().name(), setup.weather().name(),
        s.status(), s.quarter(), s.clockSeconds(), s.playClockSeconds(), s.possessionClubId(), s.down(), s.distance(), s.ballOnAbsYd(), home, away,
        s.pendingDecision(), s.lastEvents(lastEvents), drives.isEmpty() ? null : drives.get(drives.size() - 1), drives.size());
  }
}
