package br.com.velion.scoresaf.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs do plano de jogo e do playbook (RF61), lidos pelo front pelas amostras {@code game-plan.json} e {@code playbook.json}. Enums
 * como texto, no vocabulário do motor ({@code GamePlan}, {@code Playbook}); {@code version} volta no PUT (409 quando diverge).
 */
public final class GamePlanDtos {
  private GamePlanDtos() {}

  /** Valores aceitos no PUT do plano (o front monta os seletores a partir daqui). */
  public record GamePlanOptionsDto(int minPassRatio, int maxPassRatio, List<String> fourthDownAggression, List<String> tempo, List<String> defensiveFocus,
                                   List<String> blitzFrequency, List<String> baseDefense) {}

  /**
   * Plano de jogo da franquia. {@code source}: {@code COACH_STYLE} enquanto o usuário não gravou (o padrão do estilo do head coach,
   * D-38) e {@code SAVED} depois do primeiro PUT. Vale a partir do próximo kickoff (o jogo em andamento muda pelo intervalo, RF29).
   */
  public record GamePlanDto(UUID id, UUID clubId, long version, int passRatio, String fourthDownAggression, String tempo, String defensiveFocus,
                            String blitzFrequency, String baseDefense, String coachStyle, String source, boolean editable, Instant updatedAt,
                            GamePlanOptionsDto options) {}

  /** Peso de um conceito ou cobertura (0–200; 100 = padrão da liga, 0 = desativado). */
  public record WeightDto(String item, int weight) {}

  /** Uma situação do playbook ({@code EARLY_DOWN}, {@code THIRD_DOWN}, {@code THIRD_AND_LONG}, {@code RED_ZONE}, {@code TWO_MINUTE}). */
  public record SituationDto(String situation, List<WeightDto> concepts) {}

  /** Playbook da franquia: pesos por situação e por cobertura, os limites e os grupos de conceitos (corrida e passe). */
  public record PlaybookDto(UUID id, UUID clubId, long version, int minWeight, int maxWeight, int neutralWeight, List<String> runConcepts,
                            List<String> passConcepts, List<SituationDto> situations, List<WeightDto> coverages, int customWeights, Instant updatedAt) {}
}
