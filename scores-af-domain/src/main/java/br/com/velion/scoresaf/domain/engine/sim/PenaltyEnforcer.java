package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.shared.PenaltyCode;

/**
 * Aplicação das faltas do catálogo (docs/01 §3.1, RF54). Tudo em jardas relativas ao ataque (0 = própria linha do gol, 100 = gol do
 * adversário) e a partir do ponto anterior ao snap — exceto as faltas no local ({@link PenaltyCode#spotFoul()}): pass interference
 * defensiva marca no ponto da falta (no máximo a linha de 1) e intentional grounding no ponto do passe (na end zone, safety).
 *
 * <ul>
 *   <li>Falta da defesa: o ataque avança as jardas (no máximo metade da distância até o gol); primeira descida se a falta der primeira
 *   descida automática ({@code automaticFirstDown}) ou se as jardas alcançarem a linha a conquistar; senão, mesma descida com a
 *   distância menor.
 *   <li>Falta do ataque: recua as jardas (no máximo metade da distância até a própria linha do gol) e repete a descida — ou perde a
 *   descida ({@code lossOfDown}); perder a 4ª descida entrega a bola. A primeira descida automática das faltas de lado
 *   {@code EITHER} só vale quando a defesa comete.
 * </ul>
 */
public final class PenaltyEnforcer {
  private PenaltyEnforcer() {}

  /** Situação de descida: {@code yd} relativo ao ataque. */
  public record DownState(int down, int distance, int yd) {
    public int lineToGain() { return yd + distance; }
  }

  /**
   * Resultado da aplicação.
   *
   * @param yards jardas aplicadas, com sinal (positivo = ataque avança)
   */
  public record Enforcement(DownState next, int yards, boolean firstDown, boolean turnoverOnDowns, boolean safety) {}

  /** Aplica com as regras padrão (4 descidas, 10 jardas). */
  public static Enforcement enforce(DownState s, PenaltyCode code, boolean byDefense, int spotYd) { return enforce(s, code, byDefense, spotYd, 4, 10); }

  /**
   * @param spotYd ponto da falta nas faltas no local (alvo do passe na DPI, posição do QB no grounding); ignorado nas demais
   */
  public static Enforcement enforce(DownState s, PenaltyCode code, boolean byDefense, int spotYd, int downs, int yardsToGain) {
    if (byDefense) {
      int gain;
      if (code.spotFoul()) {
        int target = Math.min(99, Math.max(spotYd, s.yd() + 1));
        gain = Math.max(code.yards(), target - s.yd());
        gain = Math.min(gain, 99 - s.yd());
      } else {
        gain = Math.min(code.yards(), halfTo(100 - s.yd()));
      }
      int newYd = s.yd() + gain;
      boolean first = code.automaticFirstDown() || newYd >= s.lineToGain();
      DownState next = first
          ? new DownState(1, Math.min(yardsToGain, 100 - newYd), newYd)
          : new DownState(s.down(), s.lineToGain() - newYd, newYd);
      return new Enforcement(next, gain, first, false, false);
    }
    int loss;
    if (code.spotFoul()) {
      if (spotYd <= 0) return new Enforcement(s, -s.yd(), false, false, true);
      loss = Math.max(code.yards(), s.yd() - spotYd);
    } else {
      loss = code.yards();
    }
    loss = Math.min(loss, halfTo(s.yd()));
    int newYd = s.yd() - loss;
    int down = code.lossOfDown() ? s.down() + 1 : s.down();
    DownState next = new DownState(Math.min(down, downs), s.lineToGain() - newYd, newYd);
    return new Enforcement(next, -loss, false, down > downs, false);
  }

  /** Metade da distância (jardas inteiras, para baixo). */
  static int halfTo(int distance) { return Math.max(0, distance / 2); }
}
