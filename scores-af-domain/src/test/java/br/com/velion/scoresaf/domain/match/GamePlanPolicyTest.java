package br.com.velion.scoresaf.domain.match;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.engine.model.Playbook;
import br.com.velion.scoresaf.domain.match.GamePlanPolicy.SituationInput;
import br.com.velion.scoresaf.domain.match.GamePlanPolicy.WeightInput;
import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regras do plano de jogo e do playbook (RF61): limites, vocabulário do motor e playbook mínimo. */
class GamePlanPolicyTest {

  @Test
  void plano_valido_vira_o_plano_do_motor() {
    GamePlanPolicy.PlanResult r = GamePlanPolicy.plan(62, "aggressive", "HURRY_UP", "STOP_PASS", "HIGH", "BASE_3_4");
    assertThat(r.valid()).isTrue();
    assertThat(r.plan()).isEqualTo(new GamePlan(62, GamePlan.Aggression.AGGRESSIVE, GamePlan.Tempo.HURRY_UP, GamePlan.DefensiveFocus.STOP_PASS,
        GamePlan.Emphasis.HIGH, DefensePackage.BASE_3_4));
  }

  @Test
  void plano_fora_dos_limites_lista_todos_os_problemas() {
    GamePlanPolicy.PlanResult r = GamePlanPolicy.plan(95, "YOLO", null, "BALANCED", "NORMAL", "NICKEL");
    assertThat(r.valid()).isFalse();
    assertThat(r.errors()).extracting(GamePlanPolicy.Issue::code, GamePlanPolicy.Issue::field).containsExactly(
        org.assertj.core.groups.Tuple.tuple("PASS_RATIO_OUT_OF_RANGE", "passRatio"), org.assertj.core.groups.Tuple.tuple("UNKNOWN_VALUE", "fourthDownAggression"),
        org.assertj.core.groups.Tuple.tuple("REQUIRED", "tempo"), org.assertj.core.groups.Tuple.tuple("UNKNOWN_VALUE", "baseDefense"));
    assertThat(GamePlanPolicy.plan(19, "NORMAL", "NORMAL", "BALANCED", "NORMAL", "BASE_4_3").valid()).isFalse();
    assertThat(GamePlanPolicy.plan(20, "NORMAL", "NORMAL", "BALANCED", "NORMAL", "BASE_4_3").valid()).isTrue();
    assertThat(GamePlanPolicy.plan(80, "NORMAL", "NORMAL", "BALANCED", "NORMAL", "BASE_4_3").valid()).isTrue();
  }

  @Test
  void playbook_vazio_e_o_padrao_da_liga() {
    GamePlanPolicy.PlaybookResult r = GamePlanPolicy.playbook(List.of(), List.of());
    assertThat(r.valid()).isTrue();
    assertThat(r.playbook()).isEqualTo(Playbook.defaults());
  }

  @Test
  void playbook_com_pesos_validos_preenche_o_resto_com_100() {
    GamePlanPolicy.PlaybookResult r = GamePlanPolicy.playbook(
        List.of(new SituationInput("RED_ZONE", List.of(new WeightInput("DEEP_PASS", 0), new WeightInput("INSIDE_RUN", 180)))),
        List.of(new WeightInput("COVER_0", 0)));
    assertThat(r.valid()).isTrue();
    assertThat(r.playbook().weight(Playbook.Situation.RED_ZONE, PlayConcept.DEEP_PASS)).isZero();
    assertThat(r.playbook().weight(Playbook.Situation.RED_ZONE, PlayConcept.INSIDE_RUN)).isEqualTo(180);
    assertThat(r.playbook().weight(Playbook.Situation.EARLY_DOWN, PlayConcept.DEEP_PASS)).isEqualTo(100);
    assertThat(r.playbook().coverage(Coverage.COVER_0)).isZero();
    assertThat(r.playbook().coverage(Coverage.COVER_3)).isEqualTo(100);
  }

  @Test
  void playbook_invalido_e_incompleto() {
    GamePlanPolicy.PlaybookResult bad = GamePlanPolicy.playbook(
        List.of(new SituationInput("GOAL_LINE", List.of()), new SituationInput("EARLY_DOWN", List.of(new WeightInput("PUNT", 100),
            new WeightInput("SHORT_PASS", 201), new WeightInput("SCREEN", 50), new WeightInput("SCREEN", 60))), new SituationInput("EARLY_DOWN", List.of())),
        List.of(new WeightInput("COVER_9", 100)));
    assertThat(bad.valid()).isFalse();
    assertThat(bad.incompleteOnly()).isFalse();
    assertThat(bad.errors()).extracting(GamePlanPolicy.Issue::code).containsExactly("UNKNOWN_SITUATION", "UNKNOWN_ITEM", "WEIGHT_OUT_OF_RANGE",
        "DUPLICATE_ITEM", "DUPLICATE_SITUATION", "UNKNOWN_ITEM");

    GamePlanPolicy.PlaybookResult incomplete = GamePlanPolicy.playbook(
        List.of(new SituationInput("THIRD_AND_LONG", List.of(new WeightInput("INSIDE_RUN", 0), new WeightInput("OUTSIDE_RUN", 0))),
            new SituationInput("TWO_MINUTE", List.of(new WeightInput("SCREEN", 0), new WeightInput("SHORT_PASS", 0), new WeightInput("MEDIUM_PASS", 0),
                new WeightInput("DEEP_PASS", 0), new WeightInput("PLAY_ACTION", 0)))),
        List.of(new WeightInput("COVER_0", 0), new WeightInput("COVER_1", 0), new WeightInput("COVER_2", 0), new WeightInput("COVER_3", 0),
            new WeightInput("COVER_4", 0)));
    assertThat(incomplete.incompleteOnly()).isTrue();
    assertThat(incomplete.errors()).extracting(GamePlanPolicy.Issue::code, GamePlanPolicy.Issue::field).containsExactly(
        org.assertj.core.groups.Tuple.tuple("NO_RUN_CONCEPT", "THIRD_AND_LONG"), org.assertj.core.groups.Tuple.tuple("NO_PASS_CONCEPT", "TWO_MINUTE"),
        org.assertj.core.groups.Tuple.tuple("NO_COVERAGE", "coverages"));
  }
}
