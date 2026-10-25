package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.AdvanceMode;
import br.com.velion.scoresaf.domain.engine.model.CallSource;
import br.com.velion.scoresaf.domain.engine.model.DecisionRecord;
import br.com.velion.scoresaf.domain.engine.model.DecisionSide;
import br.com.velion.scoresaf.domain.engine.model.DecisionSituation;
import br.com.velion.scoresaf.domain.engine.model.Drive;
import br.com.velion.scoresaf.domain.engine.model.DriveResult;
import br.com.velion.scoresaf.domain.engine.model.EngineState.ClockState;
import br.com.velion.scoresaf.domain.engine.model.EngineState.Phase;
import br.com.velion.scoresaf.domain.engine.model.EventKeys;
import br.com.velion.scoresaf.domain.engine.model.MatchDecision;
import br.com.velion.scoresaf.domain.engine.model.MatchStatus;
import br.com.velion.scoresaf.domain.engine.model.Penalty;
import br.com.velion.scoresaf.domain.engine.model.PendingDecision;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.PlayOption;
import br.com.velion.scoresaf.domain.engine.model.PlayResult;
import br.com.velion.scoresaf.domain.engine.model.PlayType;
import br.com.velion.scoresaf.domain.engine.model.GamePlan.Tempo;
import br.com.velion.scoresaf.domain.engine.sim.PenaltyEnforcer.DownState;
import br.com.velion.scoresaf.domain.engine.sim.PenaltyEnforcer.Enforcement;
import br.com.velion.scoresaf.domain.engine.sim.PlayCaller.DefenseCall;
import br.com.velion.scoresaf.domain.engine.sim.PlayCaller.Kind;
import br.com.velion.scoresaf.domain.engine.sim.PlayCaller.OffenseCall;
import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.Formation;
import br.com.velion.scoresaf.domain.shared.PenaltyCode;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Fluxo da partida (RF42): kickoff, jogadas de scrimmage, chutes, conversões, relógio, quartos, intervalo, prorrogação PRO e COLLEGE,
 * momentos decisivos. Um {@link #step()} resolve uma jogada (ou um lance administrativo) e consome um cursor do gerador; abrir uma
 * decisão não consome — a mesma jogada é resolvida quando a decisão chega, com os mesmos números.
 */
final class GameEngine {
  private final LiveMatch m;
  private boolean gameOver;

  GameEngine(LiveMatch m) { this.m = m; }

  // ================================================================ entrada
  void apply(MatchDecision d) {
    if (m.status == MatchStatus.FINISHED) throw new IllegalStateException("partida encerrada");
    switch (d) {
      case MatchDecision.PlayCall pc -> {
        PendingDecision p = requirePending(pc.decisionId());
        PlayOption opt = p.options().stream().filter(o -> o.code().equals(pc.optionCode())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("opção " + pc.optionCode() + " não oferecida na decisão " + p.id()));
        m.forcedOption = opt;
        m.forcedSource = CallSource.USER;
        m.decisions.add(new DecisionRecord(p.id(), p.situation(), p.clubId(), opt.code(), false, m.seq));
        m.pending = null;
        m.status = MatchStatus.LIVE;
      }
      case MatchDecision.Auto a -> {
        PendingDecision p = requirePending(a.decisionId());
        m.skipDecision = true;
        m.decisions.add(new DecisionRecord(p.id(), p.situation(), p.clubId(), p.autoOptionCode(), true, m.seq));
        m.pending = null;
        m.status = MatchStatus.LIVE;
      }
      case MatchDecision.Timeout t -> {
        TeamState team = t.clubId().equals(m.home.clubId) ? m.home : t.clubId().equals(m.away.clubId) ? m.away : null;
        if (team == null) throw new IllegalArgumentException("franquia " + t.clubId() + " não está nesta partida");
        if (m.status == MatchStatus.NOT_STARTED || m.phase != Phase.SCRIMMAGE || !m.timed()) throw new IllegalStateException("tempo técnico só com a bola morta antes de um snap");
        if (team.timeoutsLeft() <= 0) throw new IllegalStateException("sem tempos técnicos neste tempo");
        callTimeout(team);
        m.decisions.add(new DecisionRecord(null, null, team.clubId, DecisionRecord.TIMEOUT, false, m.seq));
      }
    }
    m.version++;
  }

  private PendingDecision requirePending(String id) {
    if (m.pending == null || !m.pending.id().equals(id)) throw new IllegalArgumentException("decisão " + id + " não está aberta");
    return m.pending;
  }

  void advance(AdvanceMode mode) {
    if (m.status == MatchStatus.FINISHED || m.status == MatchStatus.AWAITING_DECISION) return;
    int drives = m.drives.size();
    long periods = periodEnds();
    boolean changed = false;
    while (m.status != MatchStatus.FINISHED && m.status != MatchStatus.AWAITING_DECISION) {
      int before = m.events.size();
      step();
      changed = true;
      if (m.events.size() > EngineConstants.MAX_EVENTS) throw new IllegalStateException("partida não terminou em " + EngineConstants.MAX_EVENTS + " lances");
      if (mode == AdvanceMode.PLAY && m.events.size() > before) break;
      if (mode == AdvanceMode.DRIVE && m.drives.size() > drives) break;
      if (mode == AdvanceMode.QUARTER && periodEnds() > periods) break;
    }
    if (changed) m.version++;
  }

  private long periodEnds() { return m.events.stream().filter(e -> e.type() == PlayType.END_OF_PERIOD).count(); }

  // ================================================================ passo
  void step() {
    if (m.status == MatchStatus.FINISHED || m.status == MatchStatus.AWAITING_DECISION) return;
    int clockBefore = m.clock;
    if (m.status == MatchStatus.NOT_STARTED) {
      startGame();
    } else {
      switch (m.phase) {
        case KICKOFF, SAFETY_KICK -> kick();
        case SCRIMMAGE -> { if (!scrimmage(false)) return; }
        case CONVERSION -> { if (!scrimmage(true)) return; }
        case FINISHED -> { return; }
      }
    }
    m.cursor++;
    m.forcedOption = null;
    m.forcedSource = null;
    m.skipDecision = false;
    afterStep(clockBefore);
  }

  private void afterStep(int clockBefore) {
    if (gameOver) { finishGame(); return; }
    if (m.status != MatchStatus.LIVE || !m.timed()) return;
    if (m.warningPeriod() && !m.twoMinuteWarningGiven && clockBefore > EngineConstants.TWO_MINUTES && m.clock <= EngineConstants.TWO_MINUTES && m.clock > 0) {
      twoMinuteWarning();
    }
    if (m.clock <= 0 && m.phase != Phase.CONVERSION) endPeriod();
    if (gameOver) finishGame();
  }

  // ================================================================ início, quartos, fim
  private void startGame() {
    m.status = MatchStatus.LIVE;
    m.quarter = 1;
    m.clock = m.rules.quarterSeconds();
    m.home.setTimeouts(m.rules.timeoutsPerHalf());
    m.away.setTimeouts(m.rules.timeoutsPerHalf());
    boolean homeReceives = Rng.of(m.setup.seed(), m.cursor, Rng.COIN).nextBoolean();
    m.openingReceiverHome = homeReceives;
    m.kickingHome = !homeReceives;
    m.phase = Phase.KICKOFF;
    m.clockState = ClockState.STOPPED;
    emit(coinToss(homeReceives));
  }

  private void endPeriod() {
    int q = m.quarter;
    m.clockState = ClockState.STOPPED;
    if (q == 1 || q == 3) {
      emit(admin(PlayType.END_OF_PERIOD, PlayResult.END_OF_QUARTER, EventKeys.END_OF_QUARTER).param("quarter", q));
      m.quarter++;
      m.clock = m.rules.quarterSeconds();
      restAll(EngineConstants.REST_QUARTER);
      return;
    }
    if (q == 2) {
      Ev ev = admin(PlayType.END_OF_PERIOD, PlayResult.END_OF_HALF, EventKeys.END_OF_HALF);
      endDrive(DriveResult.END_HALF, m.yd, ev);
      emit(ev);
      m.quarter = 3;
      m.clock = m.rules.quarterSeconds();
      m.home.setTimeouts(m.rules.timeoutsPerHalf());
      m.away.setTimeouts(m.rules.timeoutsPerHalf());
      m.twoMinuteWarningGiven = false;
      m.phase = Phase.KICKOFF;
      m.kickingHome = m.openingReceiverHome;
      restAll(EngineConstants.REST_HALF);
      return;
    }
    boolean tied = m.home.score() == m.away.score();
    if (q == 4) {
      Ev ev = admin(PlayType.END_OF_PERIOD, PlayResult.END_OF_REGULATION, EventKeys.END_OF_REGULATION);
      endDrive(DriveResult.END_HALF, m.yd, ev);
      emit(ev);
      if (!tied) { gameOver = true; return; }
      startOvertime();
      return;
    }
    // fim de um período de prorrogação PRO
    boolean ends = !tied || (m.rules.regularSeasonTiesAllowed() && !m.setup.knockout());
    Ev ev = admin(PlayType.END_OF_PERIOD, PlayResult.END_OF_OVERTIME, EventKeys.END_OF_OVERTIME).param("period", q - 4);
    if (ends) endDrive(DriveResult.END_HALF, m.yd, ev);
    emit(ev);
    if (ends) { gameOver = true; return; }
    m.quarter++;
    m.otPeriods++;
    m.clock = m.rules.overtimeSeconds();
    m.twoMinuteWarningGiven = false;
    m.home.quarterPoints.add(0);
    m.away.quarterPoints.add(0);
  }

  private void startOvertime() {
    m.quarter = 5;
    m.otPeriods = 1;
    m.home.quarterPoints.add(0);
    m.away.quarterPoints.add(0);
    m.twoMinuteWarningGiven = false;
    boolean homeFirst = Rng.of(m.setup.seed(), m.cursor, Rng.COIN).nextBoolean();
    Ev toss = coinToss(homeFirst);
    if (m.rules.alternatingOvertime()) {
      m.clock = 0;
      m.collegeRound = 1;
      m.collegeStep = 0;
      m.collegeRoundFirstHome = homeFirst;
      emit(toss);
      startCollegePossession(homeFirst);
      return;
    }
    m.clock = m.rules.overtimeSeconds();
    int to = Math.min(2, m.rules.timeoutsPerHalf());
    m.home.setTimeouts(to);
    m.away.setTimeouts(to);
    m.otPossessions = 0;
    m.otCurrent = 0;
    m.phase = Phase.KICKOFF;
    m.kickingHome = !homeFirst;
    emit(toss);
  }

  private void startCollegePossession(boolean home) {
    m.homeOffense = home;
    m.clockState = ClockState.STOPPED;
    if (m.collegeRound >= 3) {
      // a partir da 3ª rodada: só tentativas de 2 pontos alternadas
      m.phase = Phase.CONVERSION;
      m.yd = 100 - m.rules.twoPointYardLine();
      m.down = 1;
      m.distance = m.rules.twoPointYardLine();
      return;
    }
    m.yd = 100 - m.rules.overtimeStartYardLine();
    m.down = 1;
    m.distance = Math.min(m.rules.yardsToGain(), 100 - m.yd);
    m.phase = Phase.SCRIMMAGE;
    openDrive();
  }

  /** Fim de uma posse na prorrogação COLLEGE: a segunda posse da rodada, ou a comparação e a próxima rodada. */
  private void collegeNext() {
    if (m.collegeStep == 0) {
      m.collegeStep = 1;
      startCollegePossession(!m.collegeRoundFirstHome);
      return;
    }
    if (m.home.score() != m.away.score()) { gameOver = true; return; }
    emit(admin(PlayType.END_OF_PERIOD, PlayResult.END_OF_OVERTIME, EventKeys.END_OF_OVERTIME).param("period", m.collegeRound));
    m.collegeRound++;
    m.quarter++;
    m.otPeriods++;
    m.home.quarterPoints.add(0);
    m.away.quarterPoints.add(0);
    m.collegeStep = 0;
    m.collegeRoundFirstHome = !m.collegeRoundFirstHome;
    startCollegePossession(m.collegeRoundFirstHome);
  }

  /** Cara ou coroa: sem posse nem descida; a bola no meio do campo. */
  private Ev coinToss(boolean homeReceives) {
    Ev ev = admin(PlayType.COIN_TOSS, homeReceives ? PlayResult.HOME_RECEIVES : PlayResult.AWAY_RECEIVES, EventKeys.COIN_TOSS);
    ev.possession = null;
    ev.down = 0;
    ev.distance = 0;
    ev.drive = 0;
    ev.ballOnAbsYd = 50;
    ev.param("receiving", m.team(homeReceives).name());
    return ev;
  }

  private void finishGame() {
    gameOver = false;
    if (m.status == MatchStatus.FINISHED) return;
    Ev ev = admin(PlayType.END_OF_PERIOD, PlayResult.END_OF_GAME, EventKeys.FINAL);
    endDrive(DriveResult.END_HALF, m.yd, ev);
    emit(ev);
    m.status = MatchStatus.FINISHED;
    m.phase = Phase.FINISHED;
    m.pending = null;
  }

  private boolean proOvertime() { return m.overtime() && !m.rules.alternatingOvertime(); }

  /** PRO: TD ou safety na primeira posse encerra; da segunda posse em diante, qualquer pontuação que desempate encerra. */
  private void overtimeAfterScore(boolean touchdownOrSafety) {
    if (!proOvertime()) return;
    boolean differ = m.home.score() != m.away.score();
    if (m.otPossessions <= 1 && touchdownOrSafety && differ) gameOver = true;
    else if (m.otPossessions >= 2 && differ) gameOver = true;
  }

  /** PRO: terminada a segunda posse (ou depois) sem empate, acabou. */
  private void overtimeAfterPossession() {
    if (proOvertime() && m.otPossessions >= 2 && m.home.score() != m.away.score()) gameOver = true;
  }

  // ================================================================ chutes de saída
  private void kick() {
    Ev ev = capture();
    TeamState kicker = m.team(m.kickingHome), receiver = m.opponent(kicker);
    SplittableRandom r = Rng.of(m.setup.seed(), m.cursor, Rng.PLAY);
    boolean safetyKick = m.phase == Phase.SAFETY_KICK;
    Outcome o = safetyKick ? KickResolver.safetyKick(m, kicker, receiver, r)
        : PlayCaller.wantsOnside(m, kicker) ? KickResolver.onside(m, kicker, receiver, r) : KickResolver.kickoff(m, kicker, receiver, r);
    o.apply();
    advanceClock(o.seconds);
    ev.ballOnAbsYd = m.absYd(safetyKick ? EngineConstants.SAFETY_KICK_YARD_LINE : m.rules.kickoffYardLine(), kicker.home);
    fill(ev, o);
    ev.down = 0;
    ev.distance = 0;
    if (o.returnTd) {
      ev.points = scoreTouchdown(receiver, ev);
      ev.clockStopped = true;
      emit(ev);
      afterTouchdown(receiver);
    } else if (o.kickingTeamKeeps) {
      ev.turnover = true;
      ev.clockStopped = true;
      if (o.fumbleLost) { receiver.add(TeamStat.FUMBLES, 1); receiver.add(TeamStat.FUMBLES_LOST, 1); }
      emit(ev);
      newPossession(kicker.home, o.newYd);
    } else {
      ev.clockStopped = true;
      emit(ev);
      newPossession(receiver.home, o.newYd);
    }
  }

  private void newPossession(boolean home, int yd) {
    m.homeOffense = home;
    m.yd = yd;
    m.down = 1;
    m.distance = Math.min(m.rules.yardsToGain(), 100 - yd);
    m.phase = Phase.SCRIMMAGE;
    m.clockState = ClockState.STOPPED;
    openDrive();
  }

  /** Troca de posse sem pontuação (turnover, descidas, punt, FG errado), com as regras da prorrogação. */
  private void giveBall(boolean home, int yd) {
    if (m.collegeOvertime()) { collegeNext(); return; }
    overtimeAfterPossession();
    if (gameOver) return;
    newPossession(home, yd);
  }

  private void afterTouchdown(TeamState scorer) {
    if (gameOver) return;
    m.phase = Phase.CONVERSION;
    m.homeOffense = scorer.home;
    m.yd = 100 - m.rules.patYardLine();
    m.down = 1;
    m.distance = m.rules.patYardLine();
    m.clockState = ClockState.STOPPED;
  }

  private void afterFieldGoal(TeamState scorer) {
    if (gameOver) return;
    if (m.collegeOvertime()) { collegeNext(); return; }
    m.phase = Phase.KICKOFF;
    m.kickingHome = scorer.home;
    m.clockState = ClockState.STOPPED;
  }

  private void afterSafety(TeamState conceded) {
    if (gameOver) return;
    if (m.collegeOvertime()) { collegeNext(); return; }
    m.phase = Phase.SAFETY_KICK;
    m.kickingHome = conceded.home;
    m.clockState = ClockState.STOPPED;
  }

  private void afterConversion(TeamState team) {
    if (gameOver) return;
    if (m.collegeOvertime()) { collegeNext(); return; }
    m.phase = Phase.KICKOFF;
    m.kickingHome = team.home;
    m.clockState = ClockState.STOPPED;
  }

  private int scoreTouchdown(TeamState t, Ev ev) {
    int pts = m.rules.pointsTouchdown();
    t.addPoints(m.quarterIndex(), pts);
    ev.scoringClubId = t.clubId;
    overtimeAfterScore(true);
    return pts;
  }

  private int scoreSafety(TeamState defense, Ev ev) {
    int pts = m.rules.pointsSafety();
    defense.addPoints(m.quarterIndex(), pts);
    ev.scoringClubId = defense.clubId;
    overtimeAfterScore(true);
    return pts;
  }

  // ================================================================ scrimmage
  /** @return {@code false} se abriu uma decisão (o passo não aconteceu) */
  private boolean scrimmage(boolean conversion) {
    if (m.forcedOption == null && !m.skipDecision) {
      Optional<DecisionSituation> s = DecisiveMoments.detect(m, conversion);
      if (s.isPresent()) { openDecision(s.get(), conversion); return false; }
    }
    OffenseCall aiOffense = aiOffense(conversion);
    PlayOption f = m.forcedOption;
    OffenseCall oc = f != null && f.side() == DecisionSide.OFFENSE ? DecisiveMoments.offenseCall(f) : aiOffense;
    DefenseCall aiDefense = PlayCaller.defense(m, oc, Rng.of(m.setup.seed(), m.cursor, Rng.CALL_DEF));
    DefenseCall dc = f != null && f.side() == DecisionSide.DEFENSE ? DecisiveMoments.defenseCall(f, aiDefense) : aiDefense;
    CallSource src = m.forcedSource != null ? m.forcedSource : m.skipDecision ? CallSource.AUTO : CallSource.AI;

    if (!conversion && m.timed()) {
      boolean userPlays = f != null && f.side() == DecisionSide.OFFENSE && oc.kind() != Kind.KNEEL;
      if (!preSnap(oc, userPlays)) return true;
    }
    SplittableRandom r = Rng.of(m.setup.seed(), m.cursor, Rng.PLAY);
    if (conversion) { conversion(oc, dc, src, r); return true; }
    switch (oc.kind()) {
      case PUNT, FIELD_GOAL -> {
        if (preSnapFoul(oc, dc, src, r)) return true;
        if (oc.kind() == Kind.PUNT) punt(src, r); else fieldGoal(src, r);
      }
      case KNEEL, SPIKE -> play(oc, dc, src, r);
      default -> {
        if (preSnapFoul(oc, dc, src, r)) return true;
        play(oc, dc, src, r);
      }
    }
    return true;
  }

  private OffenseCall aiOffense(boolean conversion) {
    SplittableRandom rc = Rng.of(m.setup.seed(), m.cursor, Rng.CALL);
    return conversion ? PlayCaller.conversion(m, rc) : PlayCaller.offense(m, rc);
  }

  private void openDecision(DecisionSituation s, boolean conversion) {
    DecisionSide side = DecisiveMoments.side(m);
    List<PlayOption> options = DecisiveMoments.options(m, side, conversion);
    OffenseCall oc = aiOffense(conversion);
    DefenseCall dc = PlayCaller.defense(m, oc, Rng.of(m.setup.seed(), m.cursor, Rng.CALL_DEF));
    String auto = DecisiveMoments.closest(options, oc, dc, side);
    TeamState user = m.userIsHome() ? m.home : m.away;
    m.pending = new PendingDecision("dec-" + m.cursor, s, side, user.clubId, m.quarter, m.clock, m.down, m.distance, m.absYd(m.yd, m.homeOffense),
        m.home.score(), m.away.score(), options, auto, m.seq);
    m.status = MatchStatus.AWAITING_DECISION;
  }

  /** Tempo entre a jogada anterior e o snap: tempo técnico, aviso dos 2 minutos ou fim do período. {@code false} = período acabou. */
  private boolean preSnap(OffenseCall oc, boolean userPlays) {
    if (m.clockState == ClockState.STOPPED) return true;
    SplittableRandom r = Rng.of(m.setup.seed(), m.cursor, Rng.PRE_SNAP);
    Tempo tempo = oc.kind() == Kind.KNEEL ? Tempo.MILK_CLOCK : PlayCaller.tempo(m, m.offense());
    int runoff = ClockModel.runoff(m.clockState, tempo, m.rules, r);
    TeamState caller = oc.kind() == Kind.KNEEL && m.defense().timeoutsLeft() == 0 ? null : PlayCaller.timeoutBeforeSnap(m);
    if (caller != null) { callTimeout(caller); return true; }
    if (m.warningPeriod() && !m.twoMinuteWarningGiven && m.clock > EngineConstants.TWO_MINUTES && m.clock - runoff <= EngineConstants.TWO_MINUTES) {
      advanceClock(m.clock - EngineConstants.TWO_MINUTES);
      twoMinuteWarning();
      return true;
    }
    if (runoff >= m.clock) {
      if (oc.kind() == Kind.KNEEL || PlayCaller.happyToExpire(m, userPlays)) { advanceClock(m.clock); return false; }
      runoff = Math.max(0, m.clock - 1);
    }
    advanceClock(runoff);
    return true;
  }

  private void callTimeout(TeamState team) {
    team.setTimeouts(team.timeoutsLeft() - 1);
    team.add(TeamStat.TIMEOUTS_USED, 1);
    m.clockState = ClockState.STOPPED;
    Ev ev = admin(PlayType.TIMEOUT, PlayResult.TIMEOUT, EventKeys.TIMEOUT);
    ev.param("team", team.name());
    ev.param("remaining", team.timeoutsLeft());
    emit(ev);
  }

  private void twoMinuteWarning() {
    m.twoMinuteWarningGiven = true;
    m.clockState = ClockState.STOPPED;
    emit(admin(PlayType.TWO_MINUTE_WARNING, PlayResult.TWO_MINUTE_WARNING, EventKeys.TWO_MINUTE_WARNING));
  }

  /** Falta antes do snap (sempre aceita): a descida é repetida. */
  private boolean preSnapFoul(OffenseCall oc, DefenseCall dc, CallSource src, SplittableRandom r) {
    Formation f = oc.formation() == null ? Formation.SINGLEBACK : oc.formation();
    Lineups.Offense lo = Lineups.offense(m.offense(), f);
    Lineups.Defense ld = Lineups.defense(m.defense(), dc.pkg());
    Outcome.PenaltyCall pc = PenaltyModel.preSnap(m, lo, ld, PlayCaller.hurry(m, m.offense()), r);
    if (pc == null) return false;
    Ev ev = capture();
    ev.type = PlayType.NO_PLAY;
    ev.formation = oc.formation();
    ev.result = PlayResult.PENALTY;
    ev.key = EventKeys.PENALTY;
    ev.source = src;
    DownState before = new DownState(m.down, m.distance, m.yd);
    Enforcement e = PenaltyEnforcer.enforce(before, pc.code(), pc.byDefense(), 0, m.rules.downs(), m.rules.yardsToGain());
    ev.penalty = penaltyRecord(pc, e, true);
    ev.yards = e.yards();
    ev.firstDown = e.firstDown();
    ev.params.put("code", pc.code().name());
    ev.named("player", pc.player());
    chargePenalty(pc, e);
    applyDownState(e.next());
    if (e.firstDown()) firstDown(m.offense(), TeamStat.FD_PENALTY);
    m.clockState = m.clockState == ClockState.STOPPED ? ClockState.STOPPED : ClockState.RESTART_ON_READY;
    ev.clockStopped = true;
    emit(ev);
    return true;
  }

  private void play(OffenseCall oc, DefenseCall dc, CallSource src, SplittableRandom r) {
    TeamState off = m.offense(), def = m.defense();
    Lineups.Offense lo = Lineups.offense(off, oc.formation() == null ? Formation.SINGLEBACK : oc.formation());
    Lineups.Defense ld = Lineups.defense(def, dc.pkg());
    Ev ev = capture();
    ev.source = src;
    Outcome o = PlayResolver.resolve(m, oc, dc, lo, ld, false, r);
    snaps(lo, ld);
    advanceClock(o.seconds);
    fill(ev, o);
    DownState before = new DownState(m.down, m.distance, m.yd);

    Outcome.PenaltyCall pc = o.penalty;
    boolean personalAfterPlay = pc != null && pc.byDefense() && personal(pc.code()) && standsWithYards(o);
    boolean accepted = false;
    Enforcement enf = null;
    if (pc != null) {
      if (personalAfterPlay) {
        accepted = true;
      } else {
        enf = PenaltyEnforcer.enforce(before, pc.code(), pc.byDefense(), pc.spotYd(), m.rules.downs(), m.rules.yardsToGain());
        double withPenalty = value(enf);
        double withoutPenalty = valueOfPlay(o, before);
        accepted = pc.byDefense() ? withPenalty > withoutPenalty : withPenalty < withoutPenalty;
      }
    }

    if (accepted && !personalAfterPlay) {
      // a jogada não vale: só a falta
      ev.result = PlayResult.PENALTY;
      ev.key = EventKeys.PENALTY;
      ev.yards = enf.yards();
      ev.params.put("code", pc.code().name());
      ev.named("player", pc.player());
      ev.penalty = penaltyRecord(pc, enf, false);
      chargePenalty(pc, enf);
      m.drivePlays++;
      if (enf.safety()) {
        ev.points = scoreSafety(def, ev);
        endDrive(DriveResult.SAFETY, 0, ev);
        ev.clockStopped = true;
        emit(ev);
        afterSafety(off);
        return;
      }
      if (before.down() >= 3 && pc.byDefense() && enf.firstDown()) downConversionStat(off, before.down(), true);
      if (enf.turnoverOnDowns()) {
        ev.turnover = true;
        endDrive(DriveResult.DOWNS, enf.next().yd(), ev);
        ev.clockStopped = true;
        emit(ev);
        giveBall(def.home, 100 - enf.next().yd());
        return;
      }
      applyDownState(enf.next());
      if (enf.firstDown()) { ev.firstDown = true; firstDown(off, TeamStat.FD_PENALTY); }
      m.clockState = ClockState.RESTART_ON_READY;
      ev.clockStopped = true;
      redZoneCheck();
      emit(ev);
      return;
    }

    // a jogada vale
    o.apply();
    teamStats(off, def, o);
    m.drivePlays++;
    if (pc != null && !accepted) ev.penalty = new Penalty(pc.code(), teamOf(pc).clubId, pc.player() == null ? null : pc.player().id, 0, false,
        pc.code().automaticFirstDown() && pc.byDefense(), pc.code().lossOfDown(), false);
    int newYd = m.yd + o.yards;

    if (o.result == PlayResult.TOUCHDOWN) {
      ev.points = scoreTouchdown(off, ev);
      if (m.driveRedZone) off.add(TeamStat.RZ_TD, 1);
      if (before.down() >= 3) downConversionStat(off, before.down(), true);
      endDrive(DriveResult.TD, 100, ev);
      ev.clockStopped = true;
      emit(ev);
      afterTouchdown(off);
      return;
    }
    if (o.result == PlayResult.SAFETY) {
      ev.points = scoreSafety(def, ev);
      if (before.down() >= 3) downConversionStat(off, before.down(), false);
      endDrive(DriveResult.SAFETY, 0, ev);
      ev.clockStopped = true;
      emit(ev);
      afterSafety(off);
      return;
    }
    if (o.interception || o.fumbleLost) {
      ev.turnover = true;
      if (before.down() >= 3) downConversionStat(off, before.down(), false);
      int spot = Math.max(0, Math.min(100, o.turnoverSpot));
      endDrive(DriveResult.TURNOVER, Math.max(0, Math.min(100, newYd)), ev);
      ev.clockStopped = true;
      if (spot <= 0 && !m.collegeOvertime()) {
        ev.points = scoreTouchdown(def, ev);
        ev.key = o.interception ? EventKeys.INTERCEPTION_TD : ev.key;
        if (o.defender != null) o.defender.add(Stat.DEF_TD, 1);
        emit(ev);
        afterTouchdown(def);
        return;
      }
      emit(ev);
      giveBall(def.home, 100 - spot);
      return;
    }

    boolean first = o.yards >= m.distance && o.type != PlayType.KNEEL;
    // falta pessoal da defesa depois da jogada: primeira descida automática — vale antes de qualquer turnover on downs
    boolean personalFirst = personalAfterPlay && pc.code().automaticFirstDown();
    if (before.down() >= 3 && o.type != PlayType.KNEEL && o.type != PlayType.SPIKE) downConversionStat(off, before.down(), first || personalFirst);
    if (first) {
      ev.firstDown = true;
      firstDown(off, o.rush ? TeamStat.FD_RUSH : TeamStat.FD_PASS);
      m.yd = newYd;
      m.down = 1;
      m.distance = Math.min(m.rules.yardsToGain(), 100 - newYd);
    } else if (m.down >= m.rules.downs() && !personalFirst) {
      ev.turnover = true;
      endDrive(DriveResult.DOWNS, newYd, ev);
      ev.clockStopped = true;
      emit(ev);
      giveBall(def.home, 100 - newYd);
      return;
    } else {
      m.distance -= o.yards;
      m.down = Math.min(m.down + 1, m.rules.downs());
      m.yd = newYd;
    }
    m.clockState = ClockModel.after(o, first, false, false, m);
    if (personalAfterPlay) {
      // falta pessoal da defesa depois da jogada: soma a partir de onde a bola parou, com primeira descida automática
      Enforcement e = PenaltyEnforcer.enforce(new DownState(m.down, m.distance, m.yd), pc.code(), true, 0, m.rules.downs(), m.rules.yardsToGain());
      ev.penalty = penaltyRecord(pc, e, false);
      ev.yards = o.yards + e.yards();
      chargePenalty(pc, e);
      if (!first && e.firstDown()) firstDown(off, TeamStat.FD_PENALTY);
      ev.firstDown = ev.firstDown || e.firstDown();
      applyDownState(e.next());
      m.clockState = ClockState.RESTART_ON_READY;
    }
    ev.clockStopped = m.clockState != ClockState.RUNNING;
    redZoneCheck();
    emit(ev);
  }

  /** Avaliação de uma situação para o time que ataca (quanto maior, melhor): base da decisão de aceitar ou recusar a falta. */
  private double value(Enforcement e) {
    if (e.safety()) return -700;
    if (e.turnoverOnDowns()) return -400 - e.next().yd() * 0.5;
    DownState s = e.next();
    return situation(s.down(), s.distance(), s.yd());
  }

  private double valueOfPlay(Outcome o, DownState before) {
    if (o.result == PlayResult.TOUCHDOWN) return 1000;
    if (o.result == PlayResult.SAFETY) return -700;
    if (o.interception || o.fumbleLost) return -500 - (100 - o.turnoverSpot) * 0.5;
    int newYd = before.yd() + o.yards;
    if (o.yards >= before.distance()) return situation(1, Math.min(10, 100 - newYd), newYd);
    if (before.down() >= m.rules.downs()) return -400 - newYd * 0.5;
    return situation(before.down() + 1, before.distance() - o.yards, newYd);
  }

  private static double situation(int down, int distance, int yd) {
    return yd - 4.0 * (down - 1) - 1.2 * distance + (down == 1 ? 8 : 0);
  }

  private static boolean personal(PenaltyCode c) {
    return c == PenaltyCode.ROUGHING_THE_PASSER || c == PenaltyCode.UNNECESSARY_ROUGHNESS || c == PenaltyCode.FACEMASK;
  }

  /** A jogada terminou com a bola em campo e com o ataque (corrida ou passe completo, sem turnover nem pontuação). */
  private static boolean standsWithYards(Outcome o) {
    return (o.completion || o.rush) && !o.interception && !o.fumbleLost && o.result != PlayResult.TOUCHDOWN && o.result != PlayResult.SAFETY;
  }

  private TeamState teamOf(Outcome.PenaltyCall pc) { return pc.byDefense() ? m.defense() : m.offense(); }

  private Penalty penaltyRecord(Outcome.PenaltyCall pc, Enforcement e, boolean preSnap) {
    return new Penalty(pc.code(), teamOf(pc).clubId, pc.player() == null ? null : pc.player().id, Math.abs(e.yards()), true,
        pc.byDefense() && pc.code().automaticFirstDown(), !pc.byDefense() && pc.code().lossOfDown(), preSnap);
  }

  private void chargePenalty(Outcome.PenaltyCall pc, Enforcement e) {
    TeamState t = teamOf(pc);
    t.add(TeamStat.PENALTIES, 1);
    t.add(TeamStat.PENALTY_YDS, Math.abs(e.yards()));
  }

  private void applyDownState(DownState s) {
    m.down = s.down();
    m.distance = Math.max(1, s.distance());
    m.yd = s.yd();
  }

  private void firstDown(TeamState t, TeamStat kind) {
    t.add(TeamStat.FIRST_DOWNS, 1);
    t.add(kind, 1);
  }

  private void downConversionStat(TeamState t, int down, boolean converted) {
    if (down == 3) { t.add(TeamStat.THIRD_ATT, 1); if (converted) t.add(TeamStat.THIRD_CONV, 1); }
    else if (down >= 4) { t.add(TeamStat.FOURTH_ATT, 1); if (converted) t.add(TeamStat.FOURTH_CONV, 1); }
  }

  private void teamStats(TeamState off, TeamState def, Outcome o) {
    off.add(TeamStat.PLAYS, 1);
    if (o.rush) { off.add(TeamStat.RUSH_ATT, 1); off.add(TeamStat.RUSH_YDS, o.yards); }
    if (o.passAttempt) off.add(TeamStat.PASS_ATT, 1);
    if (o.completion) { off.add(TeamStat.PASS_CMP, 1); off.add(TeamStat.PASS_YDS_GROSS, o.yards); }
    if (o.sack) { off.add(TeamStat.SACKS_ALLOWED, 1); off.add(TeamStat.SACK_YDS, -o.yards); def.add(TeamStat.DEF_SACKS, 1); }
    if (o.interception) off.add(TeamStat.INT_THROWN, 1);
    if (o.fumble) off.add(TeamStat.FUMBLES, 1);
    if (o.fumbleLost) off.add(TeamStat.FUMBLES_LOST, 1);
  }

  private void redZoneCheck() {
    if (m.driveActive && m.phase == Phase.SCRIMMAGE && m.yd >= 80 && !m.driveRedZone) {
      m.driveRedZone = true;
      m.offense().add(TeamStat.RZ_TRIPS, 1);
    }
  }

  private void snaps(Lineups.Offense lo, Lineups.Defense ld) {
    List<PlayerState> on = new ArrayList<>(lo.all());
    on.addAll(ld.all());
    for (PlayerState p : on) { p.add(Stat.SNAPS, 1); p.tire(); }
    for (TeamState t : List.of(m.home, m.away)) for (PlayerState p : t.players) if (!on.contains(p)) p.rest(EngineConstants.REST_PER_PLAY);
  }

  private void restAll(double amount) {
    for (TeamState t : List.of(m.home, m.away)) for (PlayerState p : t.players) p.rest(amount);
  }

  // ================================================================ chutes de scrimmage
  private void punt(CallSource src, SplittableRandom r) {
    TeamState off = m.offense(), def = m.defense();
    Ev ev = capture();
    ev.source = src;
    Outcome o = KickResolver.punt(m, off, def, r);
    o.apply();
    off.add(TeamStat.PUNTS, 1);
    advanceClock(o.seconds);
    fill(ev, o);
    m.drivePlays++;
    endDrive(DriveResult.PUNT, m.yd, ev);
    ev.clockStopped = true;
    if (o.returnTd) {
      ev.points = scoreTouchdown(def, ev);
      emit(ev);
      afterTouchdown(def);
    } else if (o.kickingTeamKeeps) {
      ev.turnover = true;
      def.add(TeamStat.FUMBLES, 1);
      def.add(TeamStat.FUMBLES_LOST, 1);
      emit(ev);
      giveBall(off.home, o.newYd);
    } else {
      emit(ev);
      giveBall(def.home, o.newYd);
    }
  }

  private void fieldGoal(CallSource src, SplittableRandom r) {
    TeamState off = m.offense(), def = m.defense();
    Ev ev = capture();
    ev.source = src;
    Outcome o = KickResolver.fieldGoal(m, off, r);
    o.apply();
    off.add(TeamStat.FG_ATT, 1);
    advanceClock(o.seconds);
    fill(ev, o);
    m.drivePlays++;
    ev.clockStopped = true;
    if (o.good) {
      off.add(TeamStat.FG_MADE, 1);
      int pts = m.rules.pointsFieldGoal();
      off.addPoints(m.quarterIndex(), pts);
      ev.points = pts;
      ev.scoringClubId = off.clubId;
      endDrive(DriveResult.FG, m.yd, ev);
      overtimeAfterScore(false);
      emit(ev);
      afterFieldGoal(off);
    } else {
      endDrive(DriveResult.MISSED_FG, m.yd, ev);
      emit(ev);
      giveBall(def.home, o.newYd);
    }
  }

  // ================================================================ conversão
  private void conversion(OffenseCall oc, DefenseCall dc, CallSource src, SplittableRandom r) {
    TeamState off = m.offense(), def = m.defense();
    if (oc.kind() == Kind.EXTRA_POINT) {
      m.yd = 100 - m.rules.patYardLine();
      Ev ev = capture();
      ev.source = src;
      Outcome o = KickResolver.extraPoint(m, off, r);
      o.apply();
      fill(ev, o);
      ev.down = 0;
      ev.distance = 0;
      if (o.good) {
        int pts = m.rules.pointsExtraPoint();
        off.addPoints(m.quarterIndex(), pts);
        ev.points = pts;
        ev.scoringClubId = off.clubId;
      }
      ev.clockStopped = true;
      emit(ev);
      afterConversion(off);
      return;
    }
    m.yd = 100 - m.rules.twoPointYardLine();
    m.distance = m.rules.twoPointYardLine();
    Ev ev = capture();
    ev.source = src;
    Lineups.Offense lo = Lineups.offense(off, oc.formation());
    Lineups.Defense ld = Lineups.defense(def, dc.pkg());
    Outcome o = PlayResolver.resolve(m, oc, dc, lo, ld, true, r);
    snaps(lo, ld);
    fill(ev, o);
    ev.type = PlayType.TWO_POINT;
    ev.down = 0;
    ev.distance = m.rules.twoPointYardLine();
    boolean good = o.result == PlayResult.TOUCHDOWN;
    ev.result = good ? PlayResult.TWO_POINT_GOOD : PlayResult.TWO_POINT_FAILED;
    ev.key = good ? EventKeys.TWO_POINT_GOOD : EventKeys.TWO_POINT_FAILED;
    if (good) {
      int pts = m.rules.pointsTwoPoint();
      off.addPoints(m.quarterIndex(), pts);
      ev.points = pts;
      ev.scoringClubId = off.clubId;
      PlayerState scorer = o.receiver != null ? o.receiver : o.carrier;
      if (scorer != null) scorer.add(Stat.TWO_POINT, 1);
    }
    ev.clockStopped = true;
    emit(ev);
    afterConversion(off);
  }

  // ================================================================ drive e relógio
  private void openDrive() {
    m.driveActive = true;
    m.driveCount++;
    m.driveHome = m.homeOffense;
    m.driveQuarter = m.quarter;
    m.driveStartClock = m.clock;
    m.driveStartYd = m.yd;
    m.drivePlays = 0;
    m.driveStartGame = m.gameSeconds;
    m.driveRedZone = false;
    if (proOvertime()) { m.otPossessions++; m.otCurrent = m.otPossessions; }
    redZoneCheck();
  }

  private void endDrive(DriveResult result, int endYd, Ev ev) {
    if (!m.driveActive) return;
    TeamState t = m.team(m.driveHome);
    int end = Math.max(0, Math.min(100, endYd));
    m.drives.add(new Drive(m.driveCount, t.clubId, m.driveQuarter, m.driveStartClock, m.absYd(m.driveStartYd, m.driveHome), m.absYd(end, m.driveHome),
        m.drivePlays, end - m.driveStartYd, m.gameSeconds - m.driveStartGame, m.driveRedZone, result));
    t.add(TeamStat.DRIVES, 1);
    m.driveActive = false;
    if (ev != null) ev.driveEnded = result;
  }

  private void advanceClock(int seconds) {
    if (!m.timed() || seconds <= 0) return;
    int s = Math.min(seconds, m.clock);
    m.clock -= s;
    m.gameSeconds += s;
    TeamState holder = m.phase == Phase.KICKOFF || m.phase == Phase.SAFETY_KICK ? m.team(!m.kickingHome) : m.offense();
    holder.add(TeamStat.TOP_SECONDS, s);
  }

  // ================================================================ eventos
  /** Lance em construção: situação capturada antes do snap, resto preenchido pelo resultado. */
  final class Ev {
    int drive = m.driveActive ? m.driveCount : 0;
    int quarter = m.quarter;
    int clock = m.clock;
    UUID possession = m.status == MatchStatus.NOT_STARTED ? null : m.possessionClubId();
    int down = m.phase == Phase.SCRIMMAGE ? m.down : 0;
    int distance = m.phase == Phase.SCRIMMAGE ? m.distance : 0;
    int ballOnAbsYd = m.absYd(m.yd, m.homeOffense);
    PlayType type;
    PlayConcept concept;
    Formation formation;
    DefensePackage pkg;
    Coverage coverage;
    boolean blitz;
    int yards;
    PlayResult result;
    boolean firstDown;
    boolean turnover;
    int points;
    UUID scoringClubId;
    Penalty penalty;
    final List<UUID> players = new ArrayList<>();
    String key;
    final Map<String, String> params = new LinkedHashMap<>();
    boolean clockStopped;
    br.com.velion.scoresaf.domain.engine.model.DriveResult driveEnded;
    CallSource source;

    Ev param(String k, Object v) { params.put(k, String.valueOf(v)); return this; }

    void named(String k, PlayerState p) {
      if (p == null) return;
      params.put(k, p.name());
      if (!players.contains(p.id)) players.add(p.id);
    }
  }

  private Ev capture() { return new Ev(); }

  private Ev admin(PlayType type, PlayResult result, String key) {
    Ev ev = new Ev();
    ev.type = type;
    ev.result = result;
    ev.key = key;
    ev.clockStopped = true;
    return ev;
  }

  private void fill(Ev ev, Outcome o) {
    ev.type = o.type;
    ev.concept = o.concept;
    ev.formation = o.formation;
    ev.pkg = o.pkg;
    ev.coverage = o.coverage;
    ev.blitz = o.blitz;
    ev.yards = o.yards;
    ev.result = o.result;
    ev.key = o.key;
    ev.params.putAll(o.params);
    for (PlayerState p : o.involved) if (!ev.players.contains(p.id)) ev.players.add(p.id);
  }

  private void emit(Ev ev) {
    m.seq++;
    m.playClock = m.clockState == ClockState.STOPPED ? m.rules.playClockAfterStoppageSeconds() : m.rules.playClockSeconds();
    m.events.add(new PlayEvent(m.seq, ev.drive, ev.quarter, ev.clock, ev.possession, ev.down, ev.distance, ev.ballOnAbsYd, ev.type, ev.concept,
        ev.formation, ev.pkg, ev.coverage, ev.blitz, ev.yards, ev.result, ev.firstDown, ev.turnover, ev.points, ev.scoringClubId, ev.penalty,
        ev.players, ev.key, ev.params, m.home.score(), m.away.score(), ev.clockStopped, ev.driveEnded, ev.source));
  }
}
