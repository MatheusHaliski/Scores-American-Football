package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.Formation;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Um lance do play-by-play (RF54). Situação ({@code down}, {@code distance}, {@code ballOnAbsYd}, relógio) é a de antes do snap;
 * placar é o de depois. O texto é uma chave i18n ({@link EventKeys}) com parâmetros — o front traduz nas três línguas; o motor
 * nunca grava frase pronta.
 *
 * @param ballOnAbsYd jarda absoluta 0–100: 0 é a linha do gol do mandante, 100 a do visitante (o mandante ataca para o 100)
 * @param yards jardas líquidas do lance para o ataque (com a falta aceita, as jardas da falta)
 * @param points pontos do lance (0 se nenhum)
 * @param driveEnded resultado do drive que terminou neste lance, ou {@code null}
 * @param clockStopped o relógio ficou parado depois do lance
 */
public record PlayEvent(
    int seq,
    int drive,
    int quarter,
    int clockSeconds,
    UUID possessionClubId,
    int down,
    int distance,
    int ballOnAbsYd,
    PlayType type,
    PlayConcept concept,
    Formation formation,
    DefensePackage defensePackage,
    Coverage coverage,
    boolean blitz,
    int yards,
    PlayResult result,
    boolean firstDown,
    boolean turnover,
    int points,
    UUID scoringClubId,
    Penalty penalty,
    List<UUID> playerIds,
    String textKey,
    Map<String, String> params,
    int homeScore,
    int awayScore,
    boolean clockStopped,
    DriveResult driveEnded,
    CallSource callSource) {

  public PlayEvent {
    playerIds = List.copyOf(playerIds);
    params = params == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(params));
  }
}
