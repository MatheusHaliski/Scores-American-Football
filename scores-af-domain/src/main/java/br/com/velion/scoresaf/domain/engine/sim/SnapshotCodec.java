package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.EngineState;
import br.com.velion.scoresaf.domain.engine.model.EngineState.DriveState;
import br.com.velion.scoresaf.domain.engine.model.EngineState.OvertimeState;
import br.com.velion.scoresaf.domain.engine.model.EngineState.PlayerRuntime;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.MatchStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversão sem perdas {@code MatchSnapshot} ⇄ {@link LiveMatch}: é o que garante que parar em qualquer ponto, serializar,
 * restaurar e continuar dá o mesmo jogo que avançar direto (teste de determinismo).
 */
final class SnapshotCodec {
  private SnapshotCodec() {}

  static MatchSnapshot initial(MatchSetup setup) { return save(new LiveMatch(setup)); }

  static MatchSnapshot save(LiveMatch m) {
    List<PlayerRuntime> players = new ArrayList<>();
    for (TeamState t : List.of(m.home, m.away)) for (PlayerState p : t.players) players.add(new PlayerRuntime(p.id, p.energy, ints(p.stats)));
    EngineState es = new EngineState(m.phase, m.homeOffense, m.clockState, m.twoMinuteWarningGiven, m.openingReceiverHome, m.kickingHome, m.seq,
        m.gameSeconds,
        new DriveState(m.driveActive, m.driveCount, m.driveHome, m.driveQuarter, m.driveStartClock, m.driveStartYd, m.drivePlays, m.driveStartGame, m.driveRedZone),
        new OvertimeState(m.otPossessions, m.otCurrent, m.collegeRound, m.collegeRoundFirstHome, m.collegeStep, m.otPeriods),
        m.forcedOption, m.forcedSource, m.skipDecision, players, ints(m.home.stats), ints(m.away.stats));
    return new MatchSnapshot(m.setup, EngineConstants.ENGINE_VERSION, m.version, m.status, m.quarter, m.clock, m.playClock,
        m.status == MatchStatus.NOT_STARTED ? null : m.possessionClubId(), m.down, m.distance, m.absYd(m.yd, m.homeOffense), m.home.score(), m.away.score(),
        m.home.quarterPoints, m.away.quarterPoints, m.home.timeoutsLeft(), m.away.timeoutsLeft(), m.pending, m.events, m.drives, m.decisions, m.cursor, es);
  }

  static LiveMatch load(MatchSnapshot s) {
    if (s.engineVersion() != EngineConstants.ENGINE_VERSION) {
      throw new IllegalArgumentException("snapshot do motor v" + s.engineVersion() + "; este motor é v" + EngineConstants.ENGINE_VERSION);
    }
    LiveMatch m = new LiveMatch(s.setup());
    EngineState e = s.engine();
    m.status = s.status();
    m.quarter = s.quarter();
    m.clock = s.clockSeconds();
    m.playClock = s.playClockSeconds();
    m.down = s.down();
    m.distance = s.distance();
    m.cursor = s.rngCursor();
    m.version = s.version();
    m.phase = e.phase();
    m.homeOffense = e.homeOffense();
    m.yd = m.homeOffense ? s.ballOnAbsYd() : 100 - s.ballOnAbsYd();
    m.clockState = e.clockState();
    m.twoMinuteWarningGiven = e.twoMinuteWarningGiven();
    m.openingReceiverHome = e.openingReceiverHome();
    m.kickingHome = e.kickingHome();
    m.seq = e.seq();
    m.gameSeconds = e.gameSeconds();
    DriveState d = e.drive();
    m.driveActive = d.active(); m.driveCount = d.count(); m.driveHome = d.home(); m.driveQuarter = d.quarter(); m.driveStartClock = d.startClock();
    m.driveStartYd = d.startYd(); m.drivePlays = d.plays(); m.driveStartGame = d.startGameSeconds(); m.driveRedZone = d.redZone();
    OvertimeState o = e.overtime();
    m.otPossessions = o.possessions(); m.otCurrent = o.currentPossession(); m.collegeRound = o.collegeRound();
    m.collegeRoundFirstHome = o.collegeRoundFirstHome(); m.collegeStep = o.collegeStep(); m.otPeriods = o.periods();
    m.forcedOption = e.forcedOption();
    m.forcedSource = e.forcedSource();
    m.skipDecision = e.skipDecision();
    m.pending = s.pendingDecision();
    copy(e.homeStats(), m.home.stats);
    copy(e.awayStats(), m.away.stats);
    m.home.quarterPoints.clear(); m.home.quarterPoints.addAll(s.homeQuarters());
    m.away.quarterPoints.clear(); m.away.quarterPoints.addAll(s.awayQuarters());
    for (PlayerRuntime pr : e.players()) {
      PlayerState p = m.home.byId.containsKey(pr.id()) ? m.home.byId.get(pr.id()) : m.away.byId.get(pr.id());
      if (p == null) continue;
      p.energy = pr.energy();
      copy(pr.stats(), p.stats);
    }
    m.events.addAll(s.events());
    m.drives.addAll(s.drives());
    m.decisions.addAll(s.decisionsLog());
    return m;
  }

  private static List<Integer> ints(int[] a) {
    List<Integer> out = new ArrayList<>(a.length);
    for (int v : a) out.add(v);
    return out;
  }

  private static void copy(List<Integer> from, int[] to) {
    for (int i = 0; i < Math.min(from.size(), to.length); i++) to[i] = from.get(i);
  }
}
