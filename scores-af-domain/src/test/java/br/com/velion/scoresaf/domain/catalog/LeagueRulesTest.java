package br.com.velion.scoresaf.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.game.Ruleset;
import br.com.velion.scoresaf.domain.shared.OvertimeFormat;
import org.junit.jupiter.api.Test;

/** Regras PRO × COLLEGE (docs/01 §3.1 e §3.2, Q-1, Q-7). */
class LeagueRulesTest {

  @Test
  void padrao_profissional_segue_o_plano() {
    LeagueRules r = LeagueRules.pro();
    assertThat(r.getVersion()).isEqualTo(LeagueRules.CURRENT_VERSION).isEqualTo(1);
    // tempo
    assertThat(r.getQuarterMinutes()).isEqualTo(15);
    assertThat(r.getPlayClockSeconds()).isEqualTo(40);
    assertThat(r.getPlayClockAfterStoppageSeconds()).isEqualTo(25);
    assertThat(r.isTwoMinuteWarning()).isTrue();
    assertThat(r.getTimeoutsPerHalf()).isEqualTo(3);
    assertThat(r.isClockStopsOnFirstDown()).isFalse();
    // prorrogação: 10 min, as duas equipes têm a posse, empate permitido na temporada regular
    assertThat(r.getOvertimeFormat()).isEqualTo(OvertimeFormat.BOTH_POSSESS_THEN_SUDDEN_DEATH);
    assertThat(r.getOvertimeMinutes()).isEqualTo(10);
    assertThat(r.isRegularSeasonTiesAllowed()).isTrue();
    // campo, descidas e pontos
    assertThat(r.getDowns()).isEqualTo(4);
    assertThat(r.getYardsToGain()).isEqualTo(10);
    assertThat(r.getFieldLengthYards()).isEqualTo(100);
    assertThat(r.getEndZoneYards()).isEqualTo(10);
    assertThat(new int[] {r.getPointsTouchdown(), r.getPointsExtraPoint(), r.getPointsTwoPoint(), r.getPointsFieldGoal(), r.getPointsSafety()})
        .containsExactly(6, 1, 2, 3, 2);
    assertThat(r.getPatYardLine()).isEqualTo(15);
    assertThat(r.getKickoffYardLine()).isEqualTo(35);
    assertThat(r.isOneFootInbounds()).isFalse();
    // temporada: 17 jogos em 18 semanas (1 folga), 7 por conferência nos playoffs, 1º folga, final neutra, draft de 7 rodadas, teto rígido
    assertThat(r.getGamesPerTeam()).isEqualTo(17);
    assertThat(r.getRegularSeasonWeeks()).isEqualTo(18);
    assertThat(r.getByeWeeksPerTeam()).isEqualTo(1);
    assertThat(r.getPlayoffSeedsPerConference()).isEqualTo(7);
    assertThat(r.getPlayoffByeSeeds()).isEqualTo(1);
    assertThat(r.isNeutralSiteFinal()).isTrue();
    assertThat(r.getDraftRounds()).isEqualTo(7);
    assertThat(r.isHardCap()).isTrue();
    // elenco e Q-7
    assertThat(r.getRosterMax()).isEqualTo(53);
    assertThat(r.getGameDayActives()).isEqualTo(46);
    assertThat(r.getPracticeSquadMax()).isEqualTo(16);
    assertThat(r.getPracticeSquadProtectedMax()).isEqualTo(4);
    assertThat(r.getPracticeSquadElevations()).isEqualTo(3);
    assertThat(r.getOffseasonRosterMax()).isGreaterThan(53);
    assertThat(r.getIrMinWeeks()).isEqualTo(4);
    assertThat(r.getIrReturnsPerSeason()).isEqualTo(8);
    assertThat(r.getDlRotationMin()).isEqualTo(6);
    assertThat(r.getDlRotationMax()).isEqualTo(9);
    assertThat(r.violations()).isEmpty();
  }

  @Test
  void college_muda_so_o_que_a_variante_muda() {
    LeagueRules pro = LeagueRules.pro();
    LeagueRules col = LeagueRules.college();
    assertThat(col.isClockStopsOnFirstDown()).isTrue();
    assertThat(col.isOneFootInbounds()).isTrue();
    assertThat(col.getOvertimeFormat()).isEqualTo(OvertimeFormat.ALTERNATING_POSSESSIONS);
    assertThat(col.getOvertimeStartYardLine()).isEqualTo(25);
    assertThat(col.isRegularSeasonTiesAllowed()).isFalse();
    assertThat(col.getHashFromSidelineFeet()).isLessThan(pro.getHashFromSidelineFeet());
    assertThat(col.getPracticeSquadMax()).isZero();
    assertThat(col.isHardCap()).isFalse();
    // o jogo em si continua igual
    assertThat(col.getQuarterMinutes()).isEqualTo(pro.getQuarterMinutes());
    assertThat(col.getDowns()).isEqualTo(pro.getDowns());
    assertThat(col.getPointsTouchdown()).isEqualTo(pro.getPointsTouchdown());
    assertThat(col.getKickoffYardLine()).isEqualTo(pro.getKickoffYardLine());
    assertThat(col.getVersion()).isEqualTo(pro.getVersion());
    assertThat(col.violations()).isEmpty();
    assertThat(LeagueRules.defaults(Ruleset.COLLEGE).isClockStopsOnFirstDown()).isTrue();
    assertThat(LeagueRules.defaults(Ruleset.PRO).isClockStopsOnFirstDown()).isFalse();
  }

  @Test
  void violacoes_apontam_o_campo_fora_da_faixa() {
    LeagueRules r = LeagueRules.pro().toBuilder().quarterMinutes(0).gameDayActives(60).dlRotationMax(5).build();
    assertThat(r.violations()).containsExactlyInAnyOrder("quarterMinutes", "gameDayActives", "dlRotationMax");
    LeagueRules weeks = LeagueRules.pro().toBuilder().regularSeasonWeeks(17).build();
    assertThat(weeks.violations()).contains("byeWeeksPerTeam");
  }
}
