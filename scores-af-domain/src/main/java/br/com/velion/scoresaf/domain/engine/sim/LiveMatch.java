package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.CallSource;
import br.com.velion.scoresaf.domain.engine.model.DecisionRecord;
import br.com.velion.scoresaf.domain.engine.model.Drive;
import br.com.velion.scoresaf.domain.engine.model.EngineState.ClockState;
import br.com.velion.scoresaf.domain.engine.model.EngineState.Phase;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.MatchStatus;
import br.com.velion.scoresaf.domain.engine.model.PendingDecision;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.PlayOption;
import br.com.velion.scoresaf.domain.engine.model.RulesSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Estado vivo e mutável enquanto o motor trabalha. Nasce de um {@code MatchSnapshot} ({@link SnapshotCodec#load}) e volta a ele ao
 * fim de cada avanço. Jardas em {@link #yd} são relativas ao ataque (0 = própria linha do gol); o snapshot e os eventos usam a jarda
 * absoluta (0 = linha do gol do mandante).
 */
final class LiveMatch {
  final MatchSetup setup;
  final RulesSnapshot rules;
  final TeamState home;
  final TeamState away;

  MatchStatus status = MatchStatus.NOT_STARTED;
  Phase phase = Phase.KICKOFF;
  int quarter;
  int clock;
  int playClock;
  boolean homeOffense;
  int down = 1;
  int distance = 10;
  int yd = 25;
  ClockState clockState = ClockState.STOPPED;
  boolean twoMinuteWarningGiven;
  boolean openingReceiverHome;
  boolean kickingHome;
  int seq;
  int gameSeconds;
  long cursor;
  long version;

  // drive em andamento
  boolean driveActive;
  int driveCount;
  boolean driveHome;
  int driveQuarter;
  int driveStartClock;
  int driveStartYd;
  int drivePlays;
  int driveStartGame;
  boolean driveRedZone;

  // prorrogação
  int otPossessions;
  int otCurrent;
  int collegeRound;
  boolean collegeRoundFirstHome = true;
  int collegeStep;
  int otPeriods;

  // decisão
  PendingDecision pending;
  PlayOption forcedOption;
  CallSource forcedSource;
  boolean skipDecision;

  final List<PlayEvent> events = new ArrayList<>();
  final List<Drive> drives = new ArrayList<>();
  final List<DecisionRecord> decisions = new ArrayList<>();

  LiveMatch(MatchSetup setup) {
    this.setup = setup;
    this.rules = setup.rules();
    this.home = new TeamState(setup.home(), true);
    this.away = new TeamState(setup.away(), false);
    if (!setup.neutralSite()) home.homeBonus = EngineConstants.HOME_BONUS;
    for (int i = 0; i < 4; i++) { home.quarterPoints.add(0); away.quarterPoints.add(0); }
  }

  TeamState offense() { return homeOffense ? home : away; }
  TeamState defense() { return homeOffense ? away : home; }
  TeamState team(boolean isHome) { return isHome ? home : away; }
  TeamState opponent(TeamState t) { return t == home ? away : home; }

  /** Diferença no placar do ponto de vista de {@code t}. */
  int diff(TeamState t) { return t.score() - opponent(t).score(); }

  int absYd(int rel, boolean homeTeam) { return homeTeam ? rel : 100 - rel; }

  /** Período com relógio (a prorrogação COLLEGE não tem). */
  boolean timed() { return !(quarter >= 5 && rules.alternatingOvertime()); }

  boolean overtime() { return quarter >= 5; }

  boolean collegeOvertime() { return overtime() && rules.alternatingOvertime(); }

  int quarterIndex() { return quarter - 1; }

  /** Aviso dos 2 minutos vale no 2º e no 4º quarto e na prorrogação cronometrada. */
  boolean warningPeriod() { return rules.twoMinuteWarning() && (quarter == 2 || quarter == 4 || (overtime() && timed())); }

  /** Janela em que bola fora de campo para o relógio (últimos 2 min do 1º tempo, 5 do 2º e da prorrogação). */
  boolean lateWindow() {
    return (quarter == 2 && clock <= EngineConstants.OUT_OF_BOUNDS_STOP_Q2) || (quarter >= 4 && clock <= EngineConstants.OUT_OF_BOUNDS_STOP_Q4);
  }

  UUID possessionClubId() {
    if (phase == Phase.KICKOFF || phase == Phase.SAFETY_KICK) return team(kickingHome).clubId;
    return offense().clubId;
  }

  boolean userIsHome() { return setup.userClubId() != null && setup.userClubId().equals(home.clubId); }

  boolean userIsAway() { return setup.userClubId() != null && setup.userClubId().equals(away.clubId); }
}
