package br.com.velion.scoresaf.domain.engine.morale;

import br.com.velion.scoresaf.domain.match.ChartSlot;
import br.com.velion.scoresaf.domain.shared.Position;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Motor RF41 — moral do jogador, semana a semana (o {@code PlayerMoraleEngine} do basquete adaptado ao futebol americano, D-102).
 * Mesma arquitetura: um nível alvo alcançado por média móvel exponencial ({@link #EMA_ALPHA}) mais os impulsos da semana, com teto de
 * variação por semana. Puro e sem sorteio.
 *
 * <ul>
 *   <li><b>Nível alvo</b>: 50 + papel no depth chart (titular +6, rodízio +2, reserva −3, fora do depth chart −6, especialista +2) +
 *   último ano de contrato −5 + idade (até 24 anos +2, 31 ou mais −1).</li>
 *   <li><b>Impulsos</b> (só quando o time jogou): resultado (vitória +2,5, derrota −2,5, mais o saldo, até ±1); snaps × esperado para o
 *   papel na posição (até ±3; titular disponível que não jogou nenhum snap: −3; especialistas e quem está fora do depth chart não
 *   contam); sequência do time de 3 ou mais (±0,5 por jogo além de 2, até ±2).</li>
 *   <li><b>Teto</b>: a semana move no máximo −6 / +5; moral em 0–100.</li>
 *   <li><b>Pedido de troca</b>: moral abaixo de {@value #TRADE_THRESHOLD} por {@value #TRADE_WEEKS} semanas seguidas.</li>
 * </ul>
 * O {@link #performanceModifier(int)} (±5 %) fica calculado, mas não entra no motor de partida neste bloco (D-102).
 */
public final class PlayerMoraleEngine {
  private PlayerMoraleEngine() {}

  public static final double NEUTRAL = 50, MIN = 0, MAX = 100;
  public static final double EMA_ALPHA = 0.15;
  public static final double MAX_LOSS_PER_WEEK = -6, MAX_GAIN_PER_WEEK = 5;
  public static final int TRADE_THRESHOLD = 35, TRADE_WEEKS = 3;

  public static final String F_BASELINE = "BASELINE";
  public static final String F_ROLE = "ROLE";
  public static final String F_CONTRACT = "CONTRACT";
  public static final String F_AGE = "AGE";
  public static final String F_RESULT = "RESULT";
  public static final String F_SNAPS = "SNAPS";
  public static final String F_STREAK = "STREAK";
  public static final String F_WEEK_LIMIT = "WEEK_LIMIT";

  /** Papel do jogador no depth chart efetivo. */
  public enum Role {
    STARTER(6), ROTATION(2), BACKUP(-3), RESERVE(-6), SPECIALIST(2);
    private final double level;
    Role(double level) { this.level = level; }
    public double level() { return level; }
  }

  /** Perfil do jogador na temporada: papel, fatia de snaps esperada, idade e se está no último ano de contrato. */
  public record Profile(Role role, double expectedShare, int age, boolean contractFinalYear) {}

  /**
   * Semana do time: jogou, o jogador estava disponível, snaps dele e da unidade dele no jogo, resultado, saldo e sequência do time
   * depois do jogo (positiva = vitórias seguidas, negativa = derrotas).
   */
  public record Week(boolean teamPlayed, boolean available, int snaps, int unitSnaps, boolean won, boolean lost, int margin, int teamStreak) {
    public static Week bye() { return new Week(false, true, 0, 0, false, false, 0, 0); }
  }

  /** Estado depois da semana: moral (com casas, para não acumular arredondamento), variação, fatores e semanas abaixo de 35. */
  public record State(double morale, double delta, Map<String, Double> factors, int lowWeeks) {
    public int rounded() { return (int) Math.round(morale); }
    public boolean tradeRequest() { return lowWeeks >= TRADE_WEEKS; }
  }

  public static State start(int morale) { return new State(clamp(morale), 0, Map.of(), morale < TRADE_THRESHOLD ? 1 : 0); }

  public static State week(State prev, Profile p, Week w) {
    double m = prev.morale();
    Map<String, Double> f = new LinkedHashMap<>();
    double role = p.role().level();
    double contract = p.contractFinalYear() ? -5 : 0;
    double age = p.age() <= 24 ? 2 : p.age() >= 31 ? -1 : 0;
    double target = NEUTRAL + role + contract + age;
    f.put(F_BASELINE, round2(EMA_ALPHA * (NEUTRAL - m)));
    f.put(F_ROLE, round2(EMA_ALPHA * role));
    f.put(F_CONTRACT, round2(EMA_ALPHA * contract));
    f.put(F_AGE, round2(EMA_ALPHA * age));
    double pull = EMA_ALPHA * (target - m);
    double result = 0, snaps = 0, streak = 0;
    if (w.teamPlayed()) {
      result = (w.won() ? 2.5 : w.lost() ? -2.5 : 0) + Math.tanh(w.margin() / 14.0);
      boolean counts = w.available() && p.role() != Role.SPECIALIST && p.role() != Role.RESERVE && p.expectedShare() > 0;
      if (counts) {
        double share = w.unitSnaps() <= 0 ? 0 : Math.min(1.0, w.snaps() / (double) w.unitSnaps());
        double gap = (share - p.expectedShare()) / p.expectedShare();
        snaps = 3.0 * Math.tanh(gap / 0.5);
        if (w.snaps() == 0 && p.role() == Role.STARTER) snaps = -3.0;
      }
      int s = Math.abs(w.teamStreak());
      if (s >= 3) streak = Math.signum(w.teamStreak()) * Math.min(2.0, 0.5 * (s - 2));
    }
    f.put(F_RESULT, round2(result));
    f.put(F_SNAPS, round2(snaps));
    f.put(F_STREAK, round2(streak));
    double raw = pull + result + snaps + streak;
    double delta = Math.max(MAX_LOSS_PER_WEEK, Math.min(MAX_GAIN_PER_WEEK, raw));
    f.put(F_WEEK_LIMIT, round2(delta - raw));
    double next = clamp(m + delta);
    int low = (int) Math.round(next) < TRADE_THRESHOLD ? prev.lowWeeks() + 1 : 0;
    return new State(next, round2(next - m), Collections.unmodifiableMap(f), low);
  }

  /** Faixa da moral para a tela. */
  public static String label(int morale) {
    return morale >= 70 ? "HIGH" : morale >= 55 ? "GOOD" : morale >= 45 ? "NEUTRAL" : morale >= 30 ? "LOW" : "CRITICAL";
  }

  /** Modificador de desempenho (±5 % em torno de 50); calculado, mas fora do motor neste bloco (D-102). */
  public static double performanceModifier(int morale) {
    return Math.round(Math.max(-0.05, Math.min(0.05, (morale - NEUTRAL) / NEUTRAL * 0.05)) * 10000.0) / 10000.0;
  }

  /**
   * Fatia de snaps esperada para a vaga e a profundidade (RF41: QB titular 95 %, OL 95 %, DL em rodízio 60–70 %, RB 50–60 %, WR3
   * 45 %); 0 nos especialistas e nos papéis de times especiais.
   */
  public static double expectedShare(ChartSlot slot, int depth) {
    return switch (slot) {
      case QB -> depth == 1 ? 0.95 : 0.02;
      case RB -> depth == 1 ? 0.55 : depth == 2 ? 0.30 : 0.05;
      case FB -> depth == 1 ? 0.25 : 0.0;
      case WR -> depth == 1 ? 0.85 : depth == 2 ? 0.80 : depth == 3 ? 0.45 : depth == 4 ? 0.15 : 0.05;
      case TE -> depth == 1 ? 0.75 : depth == 2 ? 0.30 : 0.05;
      case LT, LG, C, RG, RT -> depth == 1 ? 0.95 : 0.05;
      case DE -> depth <= 2 ? 0.70 : depth <= 4 ? 0.30 : 0.05;
      case DT -> depth <= 2 ? 0.65 : depth <= 4 ? 0.35 : 0.05;
      case OLB -> depth <= 2 ? 0.75 : depth == 3 ? 0.20 : 0.05;
      case MLB -> depth == 1 ? 0.90 : 0.10;
      case CB -> depth <= 2 ? 0.90 : depth == 3 ? 0.55 : depth == 4 ? 0.15 : 0.05;
      case FS, SS -> depth == 1 ? 0.95 : 0.10;
      default -> 0.0;
    };
  }

  /** Papel pela fatia esperada (a melhor vaga do jogador) e pela posição natural. */
  public static Role role(Position natural, double bestExpectedShare, boolean inChart) {
    if (natural == Position.K || natural == Position.P || natural == Position.LS) return Role.SPECIALIST;
    if (!inChart) return Role.RESERVE;
    if (bestExpectedShare >= 0.6) return Role.STARTER;
    if (bestExpectedShare >= 0.25) return Role.ROTATION;
    return Role.BACKUP;
  }

  static double clamp(double v) { return Math.max(MIN, Math.min(MAX, v)); }

  static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
