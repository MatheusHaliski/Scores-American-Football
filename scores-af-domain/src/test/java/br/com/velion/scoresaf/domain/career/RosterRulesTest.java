package br.com.velion.scoresaf.domain.career;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regras de movimento do elenco (RF4/RF56) sobre {@link LeagueRules}: 53, practice squad de 16 com veteranos limitados e IR. */
class RosterRulesTest {
  private static final LeagueRules PRO = LeagueRules.pro();

  private static Player player(RosterStatus status, int experience, int injury) {
    Player p = new Player();
    p.setPosition(Position.WR);
    p.setRosterStatus(status);
    p.setExperienceYears(experience);
    p.setInjuryWeeksOut(injury);
    return p;
  }

  private static RosterRules.Counts counts(int roster, int ps, int psVets, int ir) {
    List<Player> all = new ArrayList<>();
    for (int i = 0; i < roster; i++) all.add(player(RosterStatus.ACTIVE, 5, 0));
    for (int i = 0; i < ps; i++) all.add(player(RosterStatus.PRACTICE_SQUAD, i < psVets ? 6 : 1, 0));
    for (int i = 0; i < ir; i++) all.add(player(RosterStatus.IR, 5, 6));
    return RosterRules.Counts.of(all);
  }

  private static String code(RosterRules.Violation v) { return v == null ? null : v.code(); }

  @Test
  void elenco_de_53_e_practice_squad_de_16() {
    Player ps = player(RosterStatus.PRACTICE_SQUAD, 1, 0);
    assertThat(code(RosterRules.checkMove(ps, RosterStatus.ACTIVE, counts(53, 16, 0, 0), PRO, 0, 0))).isEqualTo("ROSTER_FULL");
    assertThat(RosterRules.checkMove(ps, RosterStatus.ACTIVE, counts(52, 16, 0, 0), PRO, 0, 0)).isNull();
    Player young = player(RosterStatus.ACTIVE, 2, 0);
    RosterRules.Violation full = RosterRules.checkMove(young, RosterStatus.PRACTICE_SQUAD, counts(53, 16, 0, 0), PRO, 0, 0);
    assertThat(full.code()).isEqualTo("PRACTICE_SQUAD_FULL");
    assertThat(full.args()).containsExactly(16);
    assertThat(RosterRules.checkMove(young, RosterStatus.PRACTICE_SQUAD, counts(53, 15, 0, 0), PRO, 0, 0)).isNull();
    Player veteran = player(RosterStatus.ACTIVE, 7, 0);
    assertThat(code(RosterRules.checkMove(veteran, RosterStatus.PRACTICE_SQUAD, counts(53, 10, 6, 0), PRO, 0, 0))).isEqualTo("PRACTICE_SQUAD_INELIGIBLE");
    assertThat(RosterRules.checkMove(veteran, RosterStatus.PRACTICE_SQUAD, counts(53, 10, 5, 0), PRO, 0, 0)).isNull();
    assertThat(RosterRules.checkMove(young, RosterStatus.INACTIVE, counts(53, 16, 0, 0), PRO, 0, 0)).as("ativo ↔ inativo não muda os 53").isNull();
    assertThat(code(RosterRules.checkMove(young, RosterStatus.PRACTICE_SQUAD, counts(53, 0, 0, 0), LeagueRules.college(), 0, 0)))
        .as("college não tem practice squad").isEqualTo("PRACTICE_SQUAD_FULL");
  }

  @Test
  void lista_de_lesionados_exige_lesao_semanas_minimas_recuperacao_e_retorno_disponivel() {
    assertThat(code(RosterRules.checkMove(player(RosterStatus.ACTIVE, 3, 3), RosterStatus.IR, counts(53, 16, 0, 0), PRO, 0, 0))).isEqualTo("IR_NOT_ALLOWED");
    assertThat(RosterRules.checkMove(player(RosterStatus.ACTIVE, 3, 4), RosterStatus.IR, counts(53, 16, 0, 0), PRO, 0, 0)).isNull();
    assertThat(code(RosterRules.checkMove(player(RosterStatus.PRACTICE_SQUAD, 1, 8), RosterStatus.IR, counts(53, 16, 0, 0), PRO, 0, 0)))
        .isEqualTo("ROSTER_MOVE_NOT_ALLOWED");
    Player ir = player(RosterStatus.IR, 3, 0);
    assertThat(code(RosterRules.checkMove(ir, RosterStatus.ACTIVE, counts(50, 16, 0, 1), PRO, 3, 0))).isEqualTo("IR_RETURN_TOO_EARLY");
    assertThat(code(RosterRules.checkMove(player(RosterStatus.IR, 3, 1), RosterStatus.ACTIVE, counts(50, 16, 0, 1), PRO, 4, 0))).isEqualTo("IR_STILL_INJURED");
    assertThat(code(RosterRules.checkMove(ir, RosterStatus.ACTIVE, counts(50, 16, 0, 1), PRO, 4, 8))).isEqualTo("IR_RETURNS_EXHAUSTED");
    assertThat(code(RosterRules.checkMove(ir, RosterStatus.ACTIVE, counts(53, 16, 0, 1), PRO, 4, 0))).isEqualTo("ROSTER_FULL");
    assertThat(RosterRules.checkMove(ir, RosterStatus.ACTIVE, counts(52, 16, 0, 1), PRO, 4, 0)).isNull();
    assertThat(code(RosterRules.checkMove(ir, RosterStatus.PRACTICE_SQUAD, counts(52, 10, 0, 1), PRO, 9, 0))).isEqualTo("ROSTER_MOVE_NOT_ALLOWED");
  }

  @Test
  void dispensado_e_agente_livre_nao_se_movem_por_aqui() {
    assertThat(code(RosterRules.checkMove(player(RosterStatus.ACTIVE, 1, 0), RosterStatus.WAIVED, counts(53, 16, 0, 0), PRO, 0, 0)))
        .isEqualTo("ROSTER_MOVE_NOT_ALLOWED");
    assertThat(code(RosterRules.checkMove(player(RosterStatus.WAIVED, 1, 0), RosterStatus.ACTIVE, counts(50, 16, 0, 0), PRO, 0, 0)))
        .isEqualTo("ROSTER_MOVE_NOT_ALLOWED");
  }

  /**
   * Q-23: dos 53 para o practice squad o jogador vai a waivers (PRACTICE_SQUAD_WAIVERS), que não conta nos 53 nem no practice squad,
   * mas reserva a vaga (e a de veterano); quem está em waivers não se move por aqui.
   */
  @Test
  void practice_squad_passa_por_waivers_com_a_vaga_reservada() {
    assertThat(RosterRules.landingStatus(RosterStatus.ACTIVE, RosterStatus.PRACTICE_SQUAD)).isEqualTo(RosterStatus.PRACTICE_SQUAD_WAIVERS);
    assertThat(RosterRules.landingStatus(RosterStatus.INACTIVE, RosterStatus.PRACTICE_SQUAD)).isEqualTo(RosterStatus.PRACTICE_SQUAD_WAIVERS);
    assertThat(RosterRules.landingStatus(RosterStatus.PRACTICE_SQUAD, RosterStatus.ACTIVE)).as("subir do practice squad não passa por waivers")
        .isEqualTo(RosterStatus.ACTIVE);
    assertThat(RosterRules.landingStatus(RosterStatus.ACTIVE, RosterStatus.IR)).isEqualTo(RosterStatus.IR);

    List<Player> club = new ArrayList<>();
    for (int i = 0; i < 52; i++) club.add(player(RosterStatus.ACTIVE, 5, 0));
    for (int i = 0; i < 15; i++) club.add(player(RosterStatus.PRACTICE_SQUAD, i < 5 ? 6 : 1, 0));
    club.add(player(RosterStatus.PRACTICE_SQUAD_WAIVERS, 7, 0));
    RosterRules.Counts c = RosterRules.Counts.of(club);
    assertThat(c.roster()).isEqualTo(52);
    assertThat(c.practiceSquad()).isEqualTo(15);
    assertThat(c.waivers()).isEqualTo(1);
    assertThat(c.practiceSquadReserved()).isEqualTo(16);
    assertThat(c.veteransReserved()).isEqualTo(6);
    Player young = player(RosterStatus.ACTIVE, 1, 0);
    assertThat(code(RosterRules.checkMove(young, RosterStatus.PRACTICE_SQUAD, c, PRO, 0, 0))).as("vaga reservada pelo jogador em waivers")
        .isEqualTo("PRACTICE_SQUAD_FULL");
    club.remove(club.size() - 2);
    RosterRules.Counts open = RosterRules.Counts.of(club);
    assertThat(RosterRules.checkMove(young, RosterStatus.PRACTICE_SQUAD, open, PRO, 0, 0)).isNull();
    assertThat(code(RosterRules.checkMove(player(RosterStatus.ACTIVE, 8, 0), RosterStatus.PRACTICE_SQUAD, open, PRO, 0, 0)))
        .as("a vaga de veterano também fica reservada").isEqualTo("PRACTICE_SQUAD_INELIGIBLE");
    Player exposed = player(RosterStatus.PRACTICE_SQUAD_WAIVERS, 1, 0);
    assertThat(code(RosterRules.checkMove(exposed, RosterStatus.ACTIVE, open, PRO, 0, 0))).isEqualTo("ROSTER_MOVE_NOT_ALLOWED");
    assertThat(code(RosterRules.checkMove(exposed, RosterStatus.PRACTICE_SQUAD, open, PRO, 0, 0))).isEqualTo("ROSTER_MOVE_NOT_ALLOWED");
    assertThat(code(RosterRules.checkMove(young, RosterStatus.PRACTICE_SQUAD_WAIVERS, open, PRO, 0, 0))).isEqualTo("ROSTER_MOVE_NOT_ALLOWED");
  }
}
