package br.com.velion.scoresaf.domain.game;

import java.util.Objects;

/**
 * Identidade do jogo exposta em {@code GET /api/v1/meta}: nome, esporte, versão e o conjunto de regras padrão das ligas novas.
 *
 * @param name nome do jogo na interface
 * @param sport esporte (código estável)
 * @param version versão da build (do ambiente; {@code dev} sem variável)
 * @param defaultRuleset regras das ligas criadas sem escolha explícita (D-4, Q-1)
 */
public record GameInfo(String name, String sport, String version, Ruleset defaultRuleset) {
  public static final String NAME = "Scores: American Football";
  public static final String SPORT = "AMERICAN_FOOTBALL";

  public GameInfo {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(sport, "sport");
    Objects.requireNonNull(defaultRuleset, "defaultRuleset");
    version = version == null || version.isBlank() ? "dev" : version.trim();
  }

  public static GameInfo of(String version) { return new GameInfo(NAME, SPORT, version, Ruleset.PRO); }
}
