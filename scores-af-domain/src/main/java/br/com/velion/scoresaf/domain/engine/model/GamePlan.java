package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.shared.DefensePackage;

/**
 * Plano de jogo da franquia (docs/01 §3.5, Q-2): é o que o PlayCaller da IA segue quando ninguém chama a jogada. Os padrões
 * ({@link #defaults()}) reproduzem a média da liga.
 *
 * @param passRatio tendência de passe em situação neutra, 0–100 (55 = levemente pró-passe, a média da liga)
 * @param fourthDownAggression quanto arrisca na 4ª descida
 * @param tempo ritmo entre jogadas fora das situações de relógio
 * @param defensiveFocus onde a defesa carrega os jogadores
 * @param blitzFrequency frequência de blitz
 * @param baseDefense pacote base (4-3 ou 3-4)
 */
public record GamePlan(int passRatio, Aggression fourthDownAggression, Tempo tempo, DefensiveFocus defensiveFocus, Emphasis blitzFrequency,
                       DefensePackage baseDefense) {

  public enum Aggression { CONSERVATIVE, NORMAL, AGGRESSIVE }

  public enum Tempo { HURRY_UP, NORMAL, MILK_CLOCK }

  public enum DefensiveFocus { STOP_RUN, BALANCED, STOP_PASS }

  public enum Emphasis { LOW, NORMAL, HIGH }

  public GamePlan {
    passRatio = Math.max(20, Math.min(80, passRatio));
    if (fourthDownAggression == null) fourthDownAggression = Aggression.NORMAL;
    if (tempo == null) tempo = Tempo.NORMAL;
    if (defensiveFocus == null) defensiveFocus = DefensiveFocus.BALANCED;
    if (blitzFrequency == null) blitzFrequency = Emphasis.NORMAL;
    if (baseDefense != DefensePackage.BASE_3_4) baseDefense = DefensePackage.BASE_4_3;
  }

  public static GamePlan defaults() {
    return new GamePlan(55, Aggression.NORMAL, Tempo.NORMAL, DefensiveFocus.BALANCED, Emphasis.NORMAL, DefensePackage.BASE_4_3);
  }

  public GamePlan withBaseDefense(DefensePackage pkg) { return new GamePlan(passRatio, fourthDownAggression, tempo, defensiveFocus, blitzFrequency, pkg); }
}
