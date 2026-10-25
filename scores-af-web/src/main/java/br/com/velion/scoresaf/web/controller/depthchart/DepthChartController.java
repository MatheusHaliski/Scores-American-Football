package br.com.velion.scoresaf.web.controller.depthchart;

import br.com.velion.scoresaf.application.dto.DepthChartDtos;
import br.com.velion.scoresaf.application.usecase.rf60.DepthChartUseCases;
import br.com.velion.scoresaf.web.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

/** RF60 — depth chart (TacticsController → DepthChartController) e o plano de jogo padrão, só leitura no bloco 6a. */
@RestController
@RequestMapping("/api/v1/careers/{id}")
@Tag(name = "RF60 — Depth chart e plano de jogo")
public class DepthChartController {
  public record SlotRequest(@NotBlank @Size(max = 32) String slot, @NotNull @Size(max = 10) List<@NotNull UUID> playerIds) {}
  public record PutRequest(@NotNull Long version, @NotNull @Size(max = 40) List<@Valid @NotNull SlotRequest> slots) {}

  private final DepthChartUseCases depthChart; private final CurrentUser current;

  public DepthChartController(DepthChartUseCases depthChart, CurrentUser current) { this.depthChart = depthChart; this.current = current; }

  @GetMapping("/depth-chart")
  @Operation(summary = "RF60 — Depth chart", description = "Vagas por unidade (posições e papéis KR, PR, H, THIRD_DOWN_BACK), overall na vaga, fora de "
      + "posição, disponibilidade, erros e avisos; version para o PUT.")
  public DepthChartDtos.DepthChartDto get(@PathVariable UUID id) { return depthChart.get(id, current.requireId()); }

  @PutMapping("/depth-chart")
  @Operation(summary = "RF60 — Gravar depth chart", description = "Troca o depth chart inteiro (vagas ausentes ficam vazias). 409 "
      + "DEPTH_CHART_VERSION_CONFLICT com a versão atual; 422 com a lista de erros (indisponível, repetido, incompleto, mais que os ativos do jogo).")
  public DepthChartDtos.DepthChartDto put(@PathVariable UUID id, @Valid @RequestBody PutRequest r) {
    return depthChart.put(id, current.requireId(), new DepthChartUseCases.PutCommand(r.version(),
        r.slots().stream().map(s -> new DepthChartUseCases.SlotCommand(s.slot(), s.playerIds())).toList()));
  }

  @GetMapping("/game-plan")
  @Operation(summary = "RF61 — Plano de jogo (padrão, só leitura)", description = "O plano que o motor usa: o padrão do estilo do head coach. Edição no bloco 6b.")
  public DepthChartDtos.GamePlanDto gamePlan(@PathVariable UUID id) { return depthChart.gamePlan(id, current.requireId()); }
}
