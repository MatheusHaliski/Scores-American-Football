package br.com.velion.scoresaf.domain.catalog;

import br.com.velion.scoresaf.domain.game.Ruleset;
import br.com.velion.scoresaf.domain.shared.OvertimeFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Regras da liga, embutidas em {@code League} e versionadas (D-4): tudo o que o docs/01 §3.1 (jogo) e §3.2 (temporada e elenco)
 * fixa, mais os limites provisórios da Q-7. A resposta da Q-1 muda só os valores das fábricas {@link #pro()} e {@link #college()},
 * nunca o código. {@link #version} sobe quando um campo novo entra (o motor do bloco 5 grava a versão usada em cada partida).
 *
 * <p>Os campos de temporada (semanas, sementes de playoff, rodadas do draft) ficam aqui e não em {@code League} para a regra inteira
 * ser uma coisa só, copiada com a liga e versionada junto (D-20).
 */
@Embeddable
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LeagueRules {
  /** Versão do conjunto de campos (não é lock otimista). */
  public static final int CURRENT_VERSION = 1;

  @Column(name = "rule_version", nullable = false) @Builder.Default private int version = CURRENT_VERSION;

  // ---- tempo (§3.1)
  @Column(name = "rule_quarter_minutes", nullable = false) @Builder.Default private int quarterMinutes = 15;
  @Column(name = "rule_play_clock_seconds", nullable = false) @Builder.Default private int playClockSeconds = 40;
  @Column(name = "rule_play_clock_after_stoppage_seconds", nullable = false) @Builder.Default private int playClockAfterStoppageSeconds = 25;
  @Column(name = "rule_two_minute_warning", nullable = false) @Builder.Default private boolean twoMinuteWarning = true;
  @Column(name = "rule_timeouts_per_half", nullable = false) @Builder.Default private int timeoutsPerHalf = 3;
  /** Relógio para na primeira descida enquanto as correntes são reposicionadas (COLLEGE). */
  @Column(name = "rule_clock_stops_on_first_down", nullable = false) @Builder.Default private boolean clockStopsOnFirstDown = false;

  // ---- prorrogação (§3.1)
  @Enumerated(EnumType.STRING)
  @Column(name = "rule_overtime_format", nullable = false, columnDefinition = "VARCHAR(32)")
  @Builder.Default private OvertimeFormat overtimeFormat = OvertimeFormat.BOTH_POSSESS_THEN_SUDDEN_DEATH;
  /** Duração do período extra; 0 no formato de posses alternadas (sem relógio). */
  @Column(name = "rule_overtime_minutes", nullable = false) @Builder.Default private int overtimeMinutes = 10;
  /** Linha de partida de cada posse no formato de posses alternadas (25 no college); 0 no formato profissional (começa com kickoff). */
  @Column(name = "rule_overtime_start_yard_line", nullable = false) @Builder.Default private int overtimeStartYardLine = 0;
  @Column(name = "rule_regular_season_ties_allowed", nullable = false) @Builder.Default private boolean regularSeasonTiesAllowed = true;

  // ---- campo, descidas e pontos (§3.1)
  @Column(name = "rule_downs", nullable = false) @Builder.Default private int downs = 4;
  @Column(name = "rule_yards_to_gain", nullable = false) @Builder.Default private int yardsToGain = 10;
  @Column(name = "rule_field_length_yards", nullable = false) @Builder.Default private int fieldLengthYards = 100;
  @Column(name = "rule_end_zone_yards", nullable = false) @Builder.Default private int endZoneYards = 10;
  /** Distância entre a linha lateral e os hash marks, em pés (70 pés e 9 pol. no profissional; 60 pés no college). */
  @Column(name = "rule_hash_from_sideline_feet", nullable = false) @Builder.Default private double hashFromSidelineFeet = 70.75;
  @Column(name = "rule_points_touchdown", nullable = false) @Builder.Default private int pointsTouchdown = 6;
  @Column(name = "rule_points_extra_point", nullable = false) @Builder.Default private int pointsExtraPoint = 1;
  @Column(name = "rule_points_two_point", nullable = false) @Builder.Default private int pointsTwoPoint = 2;
  @Column(name = "rule_points_field_goal", nullable = false) @Builder.Default private int pointsFieldGoal = 3;
  @Column(name = "rule_points_safety", nullable = false) @Builder.Default private int pointsSafety = 2;
  /** Linha do snap do ponto extra por chute (15 no profissional, 3 no college). */
  @Column(name = "rule_pat_yard_line", nullable = false) @Builder.Default private int patYardLine = 15;
  @Column(name = "rule_two_point_yard_line", nullable = false) @Builder.Default private int twoPointYardLine = 2;
  @Column(name = "rule_kickoff_yard_line", nullable = false) @Builder.Default private int kickoffYardLine = 35;
  @Column(name = "rule_touchback_yard_line", nullable = false) @Builder.Default private int touchbackYardLine = 25;
  /** Recepção válida com um pé dentro do campo (COLLEGE); no profissional, dois pés. */
  @Column(name = "rule_one_foot_inbounds", nullable = false) @Builder.Default private boolean oneFootInbounds = false;

  // ---- temporada (§3.2)
  @Column(name = "rule_regular_season_weeks", nullable = false) @Builder.Default private int regularSeasonWeeks = 18;
  @Column(name = "rule_games_per_team", nullable = false) @Builder.Default private int gamesPerTeam = 17;
  @Column(name = "rule_bye_weeks_per_team", nullable = false) @Builder.Default private int byeWeeksPerTeam = 1;
  @Column(name = "rule_playoff_seeds_per_conference", nullable = false) @Builder.Default private int playoffSeedsPerConference = 7;
  /** Sementes de cada conferência que folgam na primeira rodada dos playoffs. */
  @Column(name = "rule_playoff_bye_seeds", nullable = false) @Builder.Default private int playoffByeSeeds = 1;
  @Column(name = "rule_neutral_site_final", nullable = false) @Builder.Default private boolean neutralSiteFinal = true;
  @Column(name = "rule_draft_rounds", nullable = false) @Builder.Default private int draftRounds = 7;
  /** Teto salarial rígido (o valor fica em {@code League.salaryCap}). */
  @Column(name = "rule_hard_cap", nullable = false) @Builder.Default private boolean hardCap = true;

  // ---- elenco (§3.2 e Q-7)
  @Column(name = "rule_roster_max", nullable = false) @Builder.Default private int rosterMax = 53;
  @Column(name = "rule_game_day_actives", nullable = false) @Builder.Default private int gameDayActives = 46;
  @Column(name = "rule_practice_squad_max", nullable = false) @Builder.Default private int practiceSquadMax = 16;
  /** Q-7: jogadores do practice squad protegidos de contratação por outro clube. */
  @Column(name = "rule_practice_squad_protected_max", nullable = false) @Builder.Default private int practiceSquadProtectedMax = 4;
  /** Q-7: elevações do practice squad para o dia de jogo por jogador e temporada. */
  @Column(name = "rule_practice_squad_elevations", nullable = false) @Builder.Default private int practiceSquadElevations = 3;
  /** Q-7: elenco máximo na offseason (maior que 53). */
  @Column(name = "rule_offseason_roster_max", nullable = false) @Builder.Default private int offseasonRosterMax = 90;
  /** Q-7: retornos da lista de lesionados por temporada. */
  @Column(name = "rule_ir_returns_per_season", nullable = false) @Builder.Default private int irReturnsPerSeason = 8;
  /** Q-7: semanas mínimas na lista de lesionados antes de voltar. */
  @Column(name = "rule_ir_min_weeks", nullable = false) @Builder.Default private int irMinWeeks = 4;
  /** Q-7: rodízio da linha defensiva (menor e maior número de jogadores). */
  @Column(name = "rule_dl_rotation_min", nullable = false) @Builder.Default private int dlRotationMin = 6;
  @Column(name = "rule_dl_rotation_max", nullable = false) @Builder.Default private int dlRotationMax = 9;
  /** Anos máximos de rateio do bônus de assinatura no teto (bloco 7). */
  @Column(name = "rule_bonus_proration_max_years", nullable = false) @Builder.Default private int bonusProrationMaxYears = 5;

  /** Padrão profissional (liga fictícia, Q-1 opção a). */
  public static LeagueRules pro() { return LeagueRules.builder().build(); }

  /**
   * Variante college (Q-1): relógio para na primeira descida, prorrogação com posses alternadas da linha de 25 e sem empate, um pé
   * dentro na recepção, hash marks a 60 pés, ponto extra da linha de 3, 12 jogos sem folga obrigatória, sem practice squad nem teto
   * rígido, elenco maior. Os números de elenco do college são provisórios (Q-9).
   */
  public static LeagueRules college() {
    return LeagueRules.builder()
        .clockStopsOnFirstDown(true).oneFootInbounds(true)
        .overtimeFormat(OvertimeFormat.ALTERNATING_POSSESSIONS).overtimeMinutes(0).overtimeStartYardLine(25).regularSeasonTiesAllowed(false)
        .hashFromSidelineFeet(60.0).patYardLine(3).twoPointYardLine(3)
        .regularSeasonWeeks(14).gamesPerTeam(12).byeWeeksPerTeam(2).playoffSeedsPerConference(6).playoffByeSeeds(2).draftRounds(0).hardCap(false)
        .rosterMax(105).gameDayActives(70).practiceSquadMax(0).practiceSquadProtectedMax(0).practiceSquadElevations(0).offseasonRosterMax(105)
        .build();
  }

  public static LeagueRules defaults(Ruleset ruleset) { return ruleset == Ruleset.COLLEGE ? college() : pro(); }

  /**
   * Campos fora da faixa aceita, pelo nome do campo (vazio = regras válidas). O editor (RF5) recusa regras com violação; o motor
   * do bloco 5 pode confiar nos valores.
   */
  public List<String> violations() {
    List<String> v = new ArrayList<>();
    range(v, "quarterMinutes", quarterMinutes, 1, 15);
    range(v, "playClockSeconds", playClockSeconds, 10, 60);
    range(v, "playClockAfterStoppageSeconds", playClockAfterStoppageSeconds, 10, playClockSeconds);
    range(v, "timeoutsPerHalf", timeoutsPerHalf, 0, 5);
    range(v, "overtimeMinutes", overtimeMinutes, overtimeFormat == OvertimeFormat.ALTERNATING_POSSESSIONS ? 0 : 1, 15);
    range(v, "overtimeStartYardLine", overtimeStartYardLine, overtimeFormat == OvertimeFormat.ALTERNATING_POSSESSIONS ? 1 : 0,
        overtimeFormat == OvertimeFormat.ALTERNATING_POSSESSIONS ? 50 : 0);
    range(v, "downs", downs, 3, 4);
    range(v, "yardsToGain", yardsToGain, 5, 15);
    range(v, "fieldLengthYards", fieldLengthYards, 100, 110);
    range(v, "endZoneYards", endZoneYards, 10, 20);
    if (hashFromSidelineFeet < 40 || hashFromSidelineFeet > 80) v.add("hashFromSidelineFeet");
    range(v, "pointsTouchdown", pointsTouchdown, 1, 10);
    range(v, "pointsExtraPoint", pointsExtraPoint, 1, 3);
    range(v, "pointsTwoPoint", pointsTwoPoint, 1, 3);
    range(v, "pointsFieldGoal", pointsFieldGoal, 1, 5);
    range(v, "pointsSafety", pointsSafety, 1, 3);
    range(v, "patYardLine", patYardLine, 1, 20);
    range(v, "twoPointYardLine", twoPointYardLine, 1, 10);
    range(v, "kickoffYardLine", kickoffYardLine, 20, 50);
    range(v, "touchbackYardLine", touchbackYardLine, 20, 35);
    range(v, "gamesPerTeam", gamesPerTeam, 1, 20);
    range(v, "regularSeasonWeeks", regularSeasonWeeks, gamesPerTeam, gamesPerTeam + 4);
    range(v, "byeWeeksPerTeam", byeWeeksPerTeam, 0, 4);
    if (gamesPerTeam + byeWeeksPerTeam > regularSeasonWeeks) v.add("byeWeeksPerTeam");
    range(v, "playoffSeedsPerConference", playoffSeedsPerConference, 1, 8);
    range(v, "playoffByeSeeds", playoffByeSeeds, 0, playoffSeedsPerConference);
    range(v, "draftRounds", draftRounds, 0, 10);
    range(v, "rosterMax", rosterMax, 22, 120);
    range(v, "gameDayActives", gameDayActives, 22, rosterMax);
    range(v, "practiceSquadMax", practiceSquadMax, 0, 20);
    range(v, "practiceSquadProtectedMax", practiceSquadProtectedMax, 0, practiceSquadMax);
    range(v, "practiceSquadElevations", practiceSquadElevations, 0, 10);
    range(v, "offseasonRosterMax", offseasonRosterMax, rosterMax, 120);
    range(v, "irReturnsPerSeason", irReturnsPerSeason, 0, 20);
    range(v, "irMinWeeks", irMinWeeks, 1, 10);
    range(v, "dlRotationMin", dlRotationMin, 4, 12);
    range(v, "dlRotationMax", dlRotationMax, dlRotationMin, 12);
    range(v, "bonusProrationMaxYears", bonusProrationMaxYears, 1, 10);
    return v;
  }

  private static void range(List<String> out, String field, int value, int min, int max) {
    if (value < min || value > max) out.add(field);
  }
}
