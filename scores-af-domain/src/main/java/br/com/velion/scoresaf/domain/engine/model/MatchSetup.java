package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import java.util.UUID;

/**
 * Tudo o que a partida precisa para começar (RF42). Mesma {@code MatchSetup} + mesmo log de decisões ⇒ mesmo jogo.
 *
 * @param userClubId franquia do manager humano; {@code null} em jogo IA × IA (nenhuma decisão é aberta)
 * @param playCallingLevel nível de chamada de jogada do manager (RF10/RF62)
 * @param knockout jogo eliminatório: não termina empatado (prorrogações até sair o vencedor)
 */
public record MatchSetup(
    UUID fixtureId,
    TeamSnapshot home,
    TeamSnapshot away,
    RulesSnapshot rules,
    Weather weather,
    long seed,
    PlayCallingLevel playCallingLevel,
    UUID userClubId,
    boolean neutralSite,
    boolean knockout) {

  public MatchSetup {
    if (rules == null) rules = RulesSnapshot.pro();
    if (weather == null) weather = Weather.CLEAR;
    if (playCallingLevel == null) playCallingLevel = PlayCallingLevel.NONE;
  }

  /** Partida IA × IA. */
  public static MatchSetup aiOnly(UUID fixtureId, TeamSnapshot home, TeamSnapshot away, RulesSnapshot rules, Weather weather, long seed) {
    return new MatchSetup(fixtureId, home, away, rules, weather, seed, PlayCallingLevel.NONE, null, false, false);
  }

  public MatchSetup withUser(UUID clubId, PlayCallingLevel level) {
    return new MatchSetup(fixtureId, home, away, rules, weather, seed, level, clubId, neutralSite, knockout);
  }

  public MatchSetup withRules(RulesSnapshot r) { return new MatchSetup(fixtureId, home, away, r, weather, seed, playCallingLevel, userClubId, neutralSite, knockout); }

  public MatchSetup withSeed(long s) { return new MatchSetup(fixtureId, home, away, rules, weather, s, playCallingLevel, userClubId, neutralSite, knockout); }

  /**
   * Troca o plano de jogo (com o playbook) de uma das franquias (RF29, ajustes no intervalo): o motor relê o plano do setup a cada
   * avanço, então vale a partir do próximo snap.
   */
  public MatchSetup withTeamPlan(UUID clubId, GamePlan plan) {
    if (home.clubId().equals(clubId)) return new MatchSetup(fixtureId, home.withGamePlan(plan), away, rules, weather, seed, playCallingLevel, userClubId, neutralSite, knockout);
    if (away.clubId().equals(clubId)) return new MatchSetup(fixtureId, home, away.withGamePlan(plan), rules, weather, seed, playCallingLevel, userClubId, neutralSite, knockout);
    throw new IllegalArgumentException("franquia " + clubId + " não está nesta partida");
  }

  /** Plano de jogo vigente da franquia nesta partida. */
  public GamePlan planOf(UUID clubId) {
    if (home.clubId().equals(clubId)) return home.gamePlan();
    if (away.clubId().equals(clubId)) return away.gamePlan();
    throw new IllegalArgumentException("franquia " + clubId + " não está nesta partida");
  }

  public MatchSetup asKnockout() { return new MatchSetup(fixtureId, home, away, rules, weather, seed, playCallingLevel, userClubId, neutralSite, true); }
}
