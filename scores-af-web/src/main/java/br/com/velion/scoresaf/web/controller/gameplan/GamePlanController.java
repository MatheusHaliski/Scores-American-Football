package br.com.velion.scoresaf.web.controller.gameplan;

import br.com.velion.scoresaf.application.dto.GamePlanDtos;
import br.com.velion.scoresaf.application.dto.MatchDtos;
import br.com.velion.scoresaf.application.usecase.rf07.GetPreparationUseCase;
import br.com.velion.scoresaf.application.usecase.rf61.GamePlanUseCases;
import br.com.velion.scoresaf.domain.match.GamePlanPolicy;
import br.com.velion.scoresaf.web.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF61 / RF7 — plano de jogo, playbook e preparação do próximo jogo ({@code TacticsController} do basquete → {@code GamePlanController};
 * o depth chart fica no {@code DepthChartController}).
 */
@RestController
@RequestMapping("/api/v1/careers/{id}")
@Tag(name = "RF61/RF7 — Plano de jogo, playbook e preparação")
public class GamePlanController {
  public record GamePlanRequest(@NotNull Long version, @NotNull Integer passRatio, @NotNull @Size(max = 32) String fourthDownAggression,
                                @NotNull @Size(max = 32) String tempo, @NotNull @Size(max = 32) String defensiveFocus, @NotNull @Size(max = 32) String blitzFrequency,
                                @NotNull @Size(max = 32) String baseDefense) {}
  public record WeightRequest(@Size(max = 32) String item, Integer weight) {}
  public record SituationRequest(@Size(max = 32) String situation, @Size(max = 16) List<@Valid WeightRequest> concepts) {}
  public record PlaybookRequest(@NotNull Long version, @Size(max = 10) List<@Valid SituationRequest> situations, @Size(max = 16) List<@Valid WeightRequest> coverages) {}

  private final GamePlanUseCases plans; private final GetPreparationUseCase preparation; private final CurrentUser current;

  public GamePlanController(GamePlanUseCases plans, GetPreparationUseCase preparation, CurrentUser current) {
    this.plans = plans; this.preparation = preparation; this.current = current;
  }

  @GetMapping("/game-plan")
  @Operation(summary = "RF61 — Plano de jogo", description = "Tendências que o motor usa (corrida × passe, 4ª descida, ritmo, foco defensivo, blitz, defesa "
      + "base), a origem (COACH_STYLE até o primeiro PUT, depois SAVED), os valores aceitos e a version para o PUT.")
  public GamePlanDtos.GamePlanDto gamePlan(@PathVariable UUID id) { return plans.getPlan(id, current.requireId()); }

  @PutMapping("/game-plan")
  @Operation(summary = "RF61 — Gravar plano de jogo", description = "Troca o plano inteiro; vale a partir do próximo kickoff. 409 GAME_PLAN_VERSION_CONFLICT "
      + "com a versão atual; 422 GAME_PLAN_INVALID com errors[] (passRatio de 20 a 80, valores do motor, defesa base 4-3 ou 3-4).")
  public GamePlanDtos.GamePlanDto putGamePlan(@PathVariable UUID id, @Valid @RequestBody GamePlanRequest r) {
    return plans.putPlan(id, current.requireId(), new GamePlanUseCases.PlanCommand(r.version(), r.passRatio(), r.fourthDownAggression(), r.tempo(),
        r.defensiveFocus(), r.blitzFrequency(), r.baseDefense()));
  }

  @GetMapping("/playbook")
  @Operation(summary = "RF61 — Playbook", description = "Peso de cada conceito de jogada por situação (descidas iniciais, 3ª descida, 3ª e longa, red zone, "
      + "2 minutos) e de cada cobertura: 0–200, 100 = padrão da liga, 0 = desativado; version para o PUT.")
  public GamePlanDtos.PlaybookDto playbook(@PathVariable UUID id) { return plans.getPlaybook(id, current.requireId()); }

  @PutMapping("/playbook")
  @Operation(summary = "RF61 — Gravar playbook", description = "Troca o playbook inteiro (o que não vier fica em 100). 409 PLAYBOOK_VERSION_CONFLICT; 422 "
      + "PLAYBOOK_INVALID (peso fora de 0–200, situação ou item desconhecido, repetido) ou PLAYBOOK_INCOMPLETE (situação sem corrida ou sem passe, menos "
      + "de 2 coberturas ativas), com errors[].")
  public GamePlanDtos.PlaybookDto putPlaybook(@PathVariable UUID id, @Valid @RequestBody PlaybookRequest r) {
    List<GamePlanPolicy.SituationInput> situations = r.situations() == null ? List.of() : r.situations().stream()
        .map(s -> new GamePlanPolicy.SituationInput(s.situation(), weights(s.concepts()))).toList();
    return plans.putPlaybook(id, current.requireId(), new GamePlanUseCases.PlaybookCommand(r.version(), situations, weights(r.coverages())));
  }

  @GetMapping("/next-match/preparation")
  @Operation(summary = "RF7 — Preparar time", description = "Próximo jogo e adversário, campanhas, clima previsto, comparação por unidade, confrontos-chave "
      + "(WR × CB, OL × DL, QB × pass rush, dos dois lados), jogadores-chave, prontidão do depth chart e resumo do plano e do playbook. 409 NO_NEXT_GAME.")
  public MatchDtos.PreparationDto preparation(@PathVariable UUID id) { return preparation.execute(id, current.requireId()); }

  static List<GamePlanPolicy.WeightInput> weights(List<WeightRequest> in) {
    return in == null ? List.of() : in.stream().map(w -> w == null ? null : new GamePlanPolicy.WeightInput(w.item(), w.weight())).toList();
  }
}
