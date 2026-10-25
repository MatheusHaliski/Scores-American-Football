package br.com.velion.scoresaf.domain.match;

import br.com.velion.scoresaf.domain.engine.model.DepthSlot;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.Unit;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Vagas do depth chart salvo (RF60): uma por posição (o long snapper é a posição LS) e os papéis que não são posição — retornador
 * de kickoff (KR), retornador de punt (PR), holder (H) e o back de 3ª descida (THIRD_DOWN_BACK). {@link #engineSlot()} liga a vaga
 * à do motor do bloco 5; o back de 3ª descida ainda não tem vaga no motor (D-49) e é só guardado.
 *
 * <p>{@link #minStarters()} é o mínimo de jogadores para o depth chart estar completo (titulares do pacote base: 3 WR, 2 DE e 2 DT,
 * 2 OLB, 2 CB; FB e back de 3ª descida são opcionais). {@link #naturalPositions()} são as posições que fazem o papel sem aviso de
 * fora de posição.
 */
public enum ChartSlot {
  QB(Position.QB, 1), RB(Position.RB, 1), FB(Position.FB, 0), WR(Position.WR, 3), TE(Position.TE, 1),
  LT(Position.LT, 1), LG(Position.LG, 1), C(Position.C, 1), RG(Position.RG, 1), RT(Position.RT, 1),
  DE(Position.DE, 2), DT(Position.DT, 2), OLB(Position.OLB, 2), MLB(Position.MLB, 1), CB(Position.CB, 2), FS(Position.FS, 1), SS(Position.SS, 1),
  K(Position.K, 1), P(Position.P, 1), LS(Position.LS, 1),
  KR(Unit.SPECIAL_TEAMS, DepthSlot.KR, 1, EnumSet.of(Position.RB, Position.WR, Position.CB, Position.FS, Position.SS)),
  PR(Unit.SPECIAL_TEAMS, DepthSlot.PR, 1, EnumSet.of(Position.RB, Position.WR, Position.CB, Position.FS, Position.SS)),
  H(Unit.SPECIAL_TEAMS, DepthSlot.H, 1, EnumSet.of(Position.P, Position.QB, Position.K)),
  THIRD_DOWN_BACK(Unit.OFFENSE, null, 0, EnumSet.of(Position.RB, Position.FB, Position.WR));

  /** Profundidade máxima aceita em qualquer vaga. */
  public static final int MAX_DEPTH = 10;

  private final Position position;
  private final Unit unit;
  private final DepthSlot engineSlot;
  private final int minStarters;
  private final Set<Position> naturalPositions;

  ChartSlot(Position position, int minStarters) {
    this(position.unit(), DepthSlot.of(position), minStarters, EnumSet.of(position), position);
  }

  ChartSlot(Unit unit, DepthSlot engineSlot, int minStarters, Set<Position> naturals) { this(unit, engineSlot, minStarters, naturals, null); }

  ChartSlot(Unit unit, DepthSlot engineSlot, int minStarters, Set<Position> naturals, Position position) {
    this.unit = unit; this.engineSlot = engineSlot; this.minStarters = minStarters; this.naturalPositions = naturals; this.position = position;
  }

  /** Posição da vaga; {@code null} nos papéis. */
  public Position position() { return position; }
  public Unit unit() { return unit; }
  /** Vaga correspondente no motor; {@code null} quando o motor ainda não usa o papel. */
  public DepthSlot engineSlot() { return engineSlot; }
  public int minStarters() { return minStarters; }
  public boolean isRole() { return position == null; }
  public Set<Position> naturalPositions() { return naturalPositions; }

  /** Converte o texto da API ({@code "qb"}, {@code "3rd_down_back"}); desconhecido devolve {@code null}. */
  public static ChartSlot parse(String raw) {
    if (raw == null) return null;
    String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    if (key.equals("3RD_DOWN_BACK") || key.equals("THIRD_DOWN")) return THIRD_DOWN_BACK;
    if (key.equals("HOLDER")) return H;
    if (key.equals("LONG_SNAPPER")) return LS;
    for (ChartSlot s : values()) if (s.name().equals(key)) return s;
    return null;
  }

  public static ChartSlot of(DepthSlot engine) {
    for (ChartSlot s : values()) if (s.engineSlot == engine) return s;
    throw new IllegalArgumentException("vaga do motor sem vaga no depth chart: " + engine);
  }
}
