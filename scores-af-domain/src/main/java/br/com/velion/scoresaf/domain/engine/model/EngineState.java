package br.com.velion.scoresaf.domain.engine.model;

import java.util.List;
import java.util.UUID;

/**
 * Estado interno do motor que não aparece nos campos públicos de {@link MatchSnapshot}: fase do lance seguinte, situação do relógio
 * entre as jogadas, drive em andamento, prorrogação, energia e contadores de estatística de cada jogador. Só tipos simples (records,
 * listas, enums), para a aplicação serializar em JSON sem perda e o motor retomar bit a bit ({@code SnapshotCodec}).
 */
public record EngineState(
    Phase phase,
    boolean homeOffense,
    ClockState clockState,
    boolean twoMinuteWarningGiven,
    boolean openingReceiverHome,
    boolean kickingHome,
    int seq,
    int gameSeconds,
    DriveState drive,
    OvertimeState overtime,
    PlayOption forcedOption,
    CallSource forcedSource,
    boolean skipDecision,
    List<PlayerRuntime> players,
    List<Integer> homeStats,
    List<Integer> awayStats) {

  /** O que o próximo passo do motor executa. */
  public enum Phase { KICKOFF, SAFETY_KICK, SCRIMMAGE, CONVERSION, FINISHED }

  /** Relógio entre as jogadas: correndo, parado até o snap, ou parado e reiniciado quando a bola fica pronta. */
  public enum ClockState { RUNNING, STOPPED, RESTART_ON_READY }

  /** Drive em andamento ({@code active = false} entre posses). {@code startYd} é relativo ao ataque (0 = própria linha do gol). */
  public record DriveState(boolean active, int count, boolean home, int quarter, int startClock, int startYd, int plays, int startGameSeconds,
                           boolean redZone) {}

  /**
   * Prorrogação. PRO: {@code possessions} iniciadas e o número da posse atual. COLLEGE: rodada, quem começou a rodada e se a
   * segunda posse da rodada já está em campo. {@code periods} conta os períodos extras jogados.
   */
  public record OvertimeState(int possessions, int currentPossession, int collegeRound, boolean collegeRoundFirstHome, int collegeStep,
                              int periods) {
    public static OvertimeState none() { return new OvertimeState(0, 0, 0, true, 0, 0); }
  }

  /** Energia (0–100) e contadores de estatística do jogador, na ordem dos contadores do motor. */
  public record PlayerRuntime(UUID id, double energy, List<Integer> stats) {}
}
