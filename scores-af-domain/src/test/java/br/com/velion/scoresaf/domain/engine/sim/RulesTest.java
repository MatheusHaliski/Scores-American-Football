package br.com.velion.scoresaf.domain.engine.sim;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.engine.model.Drive;
import br.com.velion.scoresaf.domain.engine.model.DriveResult;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.PlayResult;
import br.com.velion.scoresaf.domain.engine.model.PlayType;
import br.com.velion.scoresaf.domain.engine.model.RulesSnapshot;
import br.com.velion.scoresaf.domain.engine.sim.PenaltyEnforcer.DownState;
import br.com.velion.scoresaf.domain.engine.sim.PenaltyEnforcer.Enforcement;
import br.com.velion.scoresaf.domain.shared.PenaltyCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras do jogo dentro do motor (docs/01 §3.1): faltas, chutes, pontuação, prorrogação e relógio. */
class RulesTest {

  @Test
  void falta_aceita_poe_a_bola_onde_o_catalogo_manda() {
    int checked = 0;
    for (MatchResult r : SampleGames.pro()) {
      List<PlayEvent> ev = r.events();
      for (int i = 0; i < ev.size(); i++) {
        PlayEvent e = ev.get(i);
        if (e.penalty() == null || !e.penalty().accepted() || e.result() != PlayResult.PENALTY) continue;
        PenaltyCode code = e.penalty().code();
        if (code.spotFoul()) continue; // ponto da falta não está no evento
        boolean byDefense = !e.penalty().againstClubId().equals(e.possessionClubId());
        Enforcement exp = PenaltyEnforcer.enforce(new DownState(e.down(), e.distance(), SampleGames.rel(r, e)), code, byDefense, 0);
        PlayEvent next = SampleGames.nextPlay(ev, i);
        if (next == null || next.drive() != e.drive() || exp.turnoverOnDowns() || exp.safety()) continue;
        assertThat(new DownState(next.down(), next.distance(), SampleGames.rel(r, next))).as("%s em %s", code, e).isEqualTo(exp.next());
        assertThat(e.firstDown()).isEqualTo(exp.firstDown());
        assertThat(e.yards()).isEqualTo(exp.yards());
        checked++;
      }
    }
    assertThat(checked).isGreaterThan(300);
  }

  @Test
  void falta_recusada_vale_a_jogada_e_aparece_no_evento() {
    long declined = SampleGames.pro().stream().flatMap(r -> r.events().stream()).filter(e -> e.penalty() != null && !e.penalty().accepted()).count();
    assertThat(declined).isGreaterThan(10);
    SampleGames.pro().stream().flatMap(r -> r.events().stream()).filter(e -> e.penalty() != null && !e.penalty().accepted())
        .forEach(e -> { assertThat(e.result()).isNotEqualTo(PlayResult.PENALTY); assertThat(e.penalty().yards()).isZero(); });
  }

  @Test
  void touchback_de_kickoff_usa_a_linha_configurada_nas_regras() {
    RulesSnapshot r30 = RulesSnapshot.pro();
    r30 = new RulesSnapshot(r30.version(), r30.ruleset(), r30.quarterMinutes(), r30.playClockSeconds(), r30.playClockAfterStoppageSeconds(),
        r30.twoMinuteWarning(), r30.timeoutsPerHalf(), r30.clockStopsOnFirstDown(), r30.overtimeFormat(), r30.overtimeMinutes(), r30.overtimeStartYardLine(),
        r30.regularSeasonTiesAllowed(), r30.downs(), r30.yardsToGain(), r30.pointsTouchdown(), r30.pointsExtraPoint(), r30.pointsTwoPoint(), r30.pointsFieldGoal(),
        r30.pointsSafety(), r30.patYardLine(), r30.twoPointYardLine(), r30.kickoffYardLine(), 30, r30.oneFootInbounds());
    int touchbacks = 0;
    for (MatchResult r : SampleGames.run(r30, 20, 777, s -> s)) {
      List<PlayEvent> ev = r.events();
      for (int i = 0; i < ev.size(); i++) {
        PlayEvent e = ev.get(i);
        if (e.type() != PlayType.KICKOFF || e.result() != PlayResult.TOUCHBACK) continue;
        PlayEvent next = SampleGames.nextPlay(ev, i);
        if (next == null) continue;
        assertThat(next.possessionClubId()).isNotEqualTo(e.possessionClubId());
        assertThat(SampleGames.rel(r, next)).isEqualTo(30);
        assertThat(next.down()).isEqualTo(1);
        touchbacks++;
      }
    }
    assertThat(touchbacks).isGreaterThan(50);
  }

  @Test
  void safety_da_2_pontos_a_defesa_e_quem_sofreu_chuta_do_proprio_20() {
    int safeties = 0;
    for (MatchResult r : SampleGames.pro()) {
      List<PlayEvent> ev = r.events();
      for (int i = 0; i < ev.size(); i++) {
        PlayEvent e = ev.get(i);
        if (e.scoringClubId() == null || e.points() != 2 || e.type() == PlayType.TWO_POINT) continue;
        assertThat(e.scoringClubId()).as("safety é da defesa").isNotEqualTo(e.possessionClubId());
        assertThat(e.driveEnded()).isEqualTo(DriveResult.SAFETY);
        PlayEvent next = SampleGames.nextPlay(ev, i);
        if (next != null) {
          assertThat(next.type()).isEqualTo(PlayType.FREE_KICK);
          assertThat(next.possessionClubId()).isEqualTo(e.possessionClubId());
        }
        safeties++;
      }
    }
    assertThat(safeties).isGreaterThan(3);
  }

  @Test
  void touchdown_vale_6_e_tem_conversao_pat_vale_1_dois_pontos_vale_2_fg_vale_3() {
    int tds = 0, pats = 0, twos = 0, fgs = 0;
    for (MatchResult r : SampleGames.pro()) {
      List<PlayEvent> ev = r.events();
      int prevHome = 0, prevAway = 0;
      for (int i = 0; i < ev.size(); i++) {
        PlayEvent e = ev.get(i);
        int dHome = e.homeScore() - prevHome, dAway = e.awayScore() - prevAway;
        prevHome = e.homeScore();
        prevAway = e.awayScore();
        if (e.points() == 0) { assertThat(dHome + dAway).isZero(); continue; }
        assertThat(SampleGames.home(r, e.scoringClubId()) ? dHome : dAway).isEqualTo(e.points());
        switch (e.result()) {
          case EXTRA_POINT_GOOD -> { assertThat(e.points()).isEqualTo(1); pats++; }
          case TWO_POINT_GOOD -> { assertThat(e.points()).isEqualTo(2); twos++; }
          case FIELD_GOAL_GOOD -> { assertThat(e.points()).isEqualTo(3); fgs++; }
          default -> {
            if (e.points() == 6) {
              tds++;
              PlayEvent next = SampleGames.nextPlay(ev, i);
              if (e.quarter() <= 4) {
                assertThat(next.type()).as("conversão depois do TD").isIn(PlayType.EXTRA_POINT, PlayType.TWO_POINT);
                assertThat(next.possessionClubId()).isEqualTo(e.scoringClubId());
              }
            }
          }
        }
      }
    }
    assertThat(tds).isGreaterThan(500);
    assertThat(pats).isGreaterThan(400);
    assertThat(twos).isGreaterThan(3);
    assertThat(fgs).isGreaterThan(300);
  }

  @Test
  void prorrogacao_pro_td_na_primeira_posse_encerra_e_senao_os_dois_tem_a_bola() {
    int ot = 0, firstPossessionTd = 0;
    List<MatchResult> games = new java.util.ArrayList<>(SampleGames.pro());
    games.addAll(SampleGames.run(RulesSnapshot.pro(), 200, 50_000, MatchSetup::asKnockout));
    for (MatchResult r : games) {
      if (!r.overtime()) { assertThat(r.homeScore()).isNotEqualTo(r.awayScore()); continue; }
      ot++;
      List<Drive> otDrives = r.drives().stream().filter(d -> d.quarter() >= 5).toList();
      PlayEvent last = r.events().get(r.events().size() - 1);
      assertThat(last.result()).isEqualTo(PlayResult.END_OF_GAME);
      if (!otDrives.isEmpty() && otDrives.get(0).result() == DriveResult.TD) {
        firstPossessionTd++;
        assertThat(otDrives).hasSize(1);
        assertThat(r.winnerClubId()).isEqualTo(otDrives.get(0).clubId());
        // sem conversão depois do TD que decide
        assertThat(r.events().stream().filter(e -> e.quarter() >= 5 && e.type() == PlayType.EXTRA_POINT)).isEmpty();
      } else if (r.winnerClubId() != null) {
        UUID loser = r.winnerClubId().equals(r.homeClubId()) ? r.awayClubId() : r.homeClubId();
        boolean returnTd = r.events().stream().anyMatch(e -> e.quarter() >= 5 && e.points() == 6 && e.drive() == 0);
        boolean safety = r.events().stream().anyMatch(e -> e.quarter() >= 5 && e.points() == 2);
        if (!returnTd && !safety) assertThat(otDrives).as("o perdedor teve a bola").anyMatch(d -> d.clubId().equals(loser));
      } else {
        assertThat(r.homeScore()).isEqualTo(r.awayScore());
        assertThat(r.events().stream().anyMatch(e -> e.result() == PlayResult.END_OF_OVERTIME)).isTrue();
      }
    }
    assertThat(ot).isGreaterThan(8);
    assertThat(firstPossessionTd).isGreaterThan(0);
  }

  @Test
  void prorrogacao_eliminatoria_nunca_termina_empatada() {
    for (MatchResult r : SampleGames.run(RulesSnapshot.pro(), 200, 50_000, MatchSetup::asKnockout)) {
      assertThat(r.winnerClubId()).isNotNull();
    }
  }

  @Test
  void prorrogacao_college_tem_posses_alternadas_da_25_e_nao_empata() {
    int ot = 0, multi = 0;
    for (MatchResult r : SampleGames.college()) {
      assertThat(r.winnerClubId()).as("college não empata").isNotNull();
      if (!r.overtime()) continue;
      ot++;
      List<Drive> otDrives = r.drives().stream().filter(d -> d.quarter() >= 5).toList();
      assertThat(otDrives).isNotEmpty();
      for (Drive d : otDrives) {
        int rel = d.clubId().equals(r.homeClubId()) ? d.startAbsYd() : 100 - d.startAbsYd();
        assertThat(rel).as("posse começa na 25 do adversário").isEqualTo(75);
        assertThat(d.result()).isNotIn(DriveResult.PUNT, DriveResult.END_HALF);
      }
      // nenhuma rodada termina com só um time tendo a bola, e as duas equipes alternam
      for (int i = 0; i + 1 < otDrives.size(); i += 2) assertThat(otDrives.get(i).clubId()).isNotEqualTo(otDrives.get(i + 1).clubId());
      if (r.events().stream().anyMatch(e -> e.quarter() >= 7)) {
        multi++;
        assertThat(r.events().stream().filter(e -> e.quarter() >= 7 && e.type().isScrimmage())).as("da 3ª rodada em diante só 2 pontos").isEmpty();
      }
      assertThat(r.events().stream().filter(e -> e.quarter() >= 5).allMatch(e -> e.clockSeconds() == 0)).as("sem relógio").isTrue();
    }
    assertThat(ot).isGreaterThan(5);
  }

  @Test
  void relogio_do_college_para_na_primeira_descida_e_o_do_pro_nao() {
    long collegeFirstDowns = 0;
    for (MatchResult r : SampleGames.college()) {
      for (PlayEvent e : r.events()) {
        if (e.firstDown() && (e.type() == PlayType.RUN || e.type() == PlayType.PASS) && e.quarter() <= 4) {
          assertThat(e.clockStopped()).as("COLLEGE: %s", e).isTrue();
          collegeFirstDowns++;
        }
      }
    }
    assertThat(collegeFirstDowns).isGreaterThan(1000);
    long proRunning = SampleGames.pro().stream().flatMap(r -> r.events().stream())
        .filter(e -> e.firstDown() && e.type() == PlayType.RUN && e.penalty() == null && !e.clockStopped()).count();
    assertThat(proRunning).as("PRO: primeira descida em campo deixa o relógio correr").isGreaterThan(1000);
  }

  @Test
  void relogio_para_no_incompleto_e_ha_um_aviso_dos_2_minutos_em_cada_tempo() {
    for (MatchResult r : SampleGames.pro()) {
      for (PlayEvent e : r.events()) if (e.result() == PlayResult.INCOMPLETE) assertThat(e.clockStopped()).isTrue();
      for (int q : new int[] {2, 4}) {
        long warnings = r.events().stream().filter(e -> e.type() == PlayType.TWO_MINUTE_WARNING && e.quarter() == q).count();
        assertThat(warnings).as("aviso no %dº quarto", q).isEqualTo(1);
        r.events().stream().filter(e -> e.type() == PlayType.TWO_MINUTE_WARNING && e.quarter() == q).forEach(e -> assertThat(e.clockSeconds()).isLessThanOrEqualTo(120));
      }
    }
  }

  @Test
  void quem_esta_na_frente_ajoelha_no_fim_e_quem_esta_atras_usa_tempo_tecnico_e_onside() {
    long kneels = 0, onsides = 0, timeouts = 0;
    for (MatchResult r : SampleGames.pro()) {
      for (PlayEvent e : r.events()) {
        if (e.type() == PlayType.KNEEL && e.quarter() == 4) {
          int diff = SampleGames.home(r, e.possessionClubId()) ? e.homeScore() - e.awayScore() : e.awayScore() - e.homeScore();
          assertThat(diff).isPositive();
          kneels++;
        }
        if (e.type() == PlayType.ONSIDE_KICK) {
          int diff = SampleGames.home(r, e.possessionClubId()) ? e.homeScore() - e.awayScore() : e.awayScore() - e.homeScore();
          assertThat(diff).isNegative();
          assertThat(e.quarter()).isGreaterThanOrEqualTo(4);
          onsides++;
        }
        if (e.type() == PlayType.TIMEOUT) timeouts++;
      }
      assertThat(r.homeTeam().timeoutsUsed()).isLessThanOrEqualTo(3 * 2 + 2);
    }
    assertThat(kneels).isGreaterThan(50);
    assertThat(onsides).isGreaterThan(10);
    assertThat(timeouts).isGreaterThan(100);
  }

  @Test
  void quarto_tem_15_minutos_e_o_jogo_termina() {
    for (MatchResult r : SampleGames.pro()) {
      PlayEvent last = r.events().get(r.events().size() - 1);
      assertThat(last.result()).isEqualTo(PlayResult.END_OF_GAME);
      assertThat(r.events()).allMatch(e -> e.clockSeconds() >= 0 && e.clockSeconds() <= 15 * 60);
      assertThat(r.events().stream().filter(e -> e.result() == PlayResult.END_OF_QUARTER)).hasSize(2);
      assertThat(r.events().stream().filter(e -> e.result() == PlayResult.END_OF_HALF)).hasSize(1);
    }
  }
}
