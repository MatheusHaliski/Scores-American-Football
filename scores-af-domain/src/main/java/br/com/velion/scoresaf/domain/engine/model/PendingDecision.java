package br.com.velion.scoresaf.domain.engine.model;

import java.util.List;
import java.util.UUID;

/**
 * Decisão aberta para o manager (RF62): a situação de jogo, 3 a 5 opções e a que a IA tomaria ({@code autoOptionCode}) se o prazo
 * vencer. O prazo em si (UserSettings.decisionTimeoutSeconds) é da aplicação: o motor só recebe {@link MatchDecision.Auto}.
 */
public record PendingDecision(
    String id,
    DecisionSituation situation,
    DecisionSide side,
    UUID clubId,
    int quarter,
    int clockSeconds,
    int down,
    int distance,
    int ballOnAbsYd,
    int homeScore,
    int awayScore,
    List<PlayOption> options,
    String autoOptionCode,
    int openedAtSeq) {

  public PendingDecision { options = List.copyOf(options); }

  public boolean offers(String code) { return options.stream().anyMatch(o -> o.code().equals(code)); }
}
