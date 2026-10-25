package br.com.velion.scoresaf.web.controller.match;

import br.com.velion.scoresaf.application.dto.MatchDtos;
import br.com.velion.scoresaf.application.usecase.rf29.MatchAdjustmentsUseCase;
import br.com.velion.scoresaf.application.usecase.rf42.AdvanceMatchUseCase;
import br.com.velion.scoresaf.application.usecase.rf42.MatchQueryUseCases;
import br.com.velion.scoresaf.application.usecase.rf42.PlayWeekUseCase;
import br.com.velion.scoresaf.application.usecase.rf62.PlayCallUseCase;
import br.com.velion.scoresaf.domain.match.GamePlanPolicy;
import br.com.velion.scoresaf.web.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF27 / RF29 / RF42 / RF62 — jogar a semana, partida ao vivo por descida, playbook do intervalo, chamada de jogada e pós-jogo (o
 * {@code MatchController} do basquete com {@code /rounds} → {@code /weeks} e a rota nova {@code /play-calls/{decisionId}}). Partida de
 * outro usuário responde 404.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "RF27/RF29/RF42/RF62 — Partida")
public class MatchController {
  public record AdvanceRequest(@Size(max = 16) String mode, Long version) {}
  public record WeightRequest(@Size(max = 32) String item, Integer weight) {}
  public record SituationRequest(@Size(max = 32) String situation, @Size(max = 16) List<@Valid WeightRequest> concepts) {}
  public record GamePlanPatch(Integer passRatio, @Size(max = 32) String fourthDownAggression, @Size(max = 32) String tempo, @Size(max = 32) String defensiveFocus,
                              @Size(max = 32) String blitzFrequency, @Size(max = 32) String baseDefense) {}
  public record PlaybookPatch(@Size(max = 10) List<@Valid SituationRequest> situations, @Size(max = 16) List<@Valid WeightRequest> coverages) {}
  public record AdjustmentRequest(@NotBlank @Size(max = 16) String type, @Valid GamePlanPatch gamePlan, @Valid PlaybookPatch playbook) {}
  public record DecisionsRequest(@NotNull Long version, @NotEmpty @Size(max = 5) List<@Valid @NotNull AdjustmentRequest> adjustments) {}
  public record PlayCallRequest(@NotBlank @Size(max = 32) String optionCode, @NotNull Long version) {}

  private final PlayWeekUseCase play; private final AdvanceMatchUseCase advance; private final MatchAdjustmentsUseCase adjustments;
  private final PlayCallUseCase playCalls; private final MatchQueryUseCases queries; private final CurrentUser current;

  public MatchController(PlayWeekUseCase play, AdvanceMatchUseCase advance, MatchAdjustmentsUseCase adjustments, PlayCallUseCase playCalls,
                         MatchQueryUseCases queries, CurrentUser current) {
    this.play = play; this.advance = advance; this.adjustments = adjustments; this.playCalls = playCalls; this.queries = queries; this.current = current;
  }

  @PostMapping("/careers/{id}/weeks/current/play")
  @Operation(summary = "RF27 — Jogar a semana atual", description = "Confere o depth chart (422 DEPTH_CHART_INVALID com errors[]), cria a partida ao vivo do "
      + "usuário (semente do jogo, nível de chamada de jogada da conta) e devolve o painel da semana; os outros jogos são simulados quando a semana fecha. "
      + "Semana de folga fecha na hora. Idempotente. 409 NO_NEXT_GAME no fim da temporada regular.")
  public MatchDtos.WeekBoardDto play(@PathVariable UUID id) { return play.execute(id, current.requireId()); }

  @GetMapping("/careers/{id}/weeks/{n}")
  @Operation(summary = "RF27 — Painel da semana", description = "Todos os jogos da semana por horário, folgas, a partida do usuário (matchId) com a situação ao vivo.")
  public MatchDtos.WeekBoardDto week(@PathVariable UUID id, @PathVariable int n) { return queries.week(id, current.requireId(), n); }

  @GetMapping("/matches/{mid}")
  @Operation(summary = "RF27 — Estado da partida ao vivo", description = "Situação da sessão (NOT_STARTED, LIVE, BREAK, HALFTIME, AWAITING_DECISION, FINISHED), "
      + "modos de avanço aceitos, prazo da decisão aberta e o estado do motor em live (placar por quarto, relógio, descida e distância, bola, tempos técnicos, "
      + "decisão aberta, últimos lances, box score parcial).")
  public MatchDtos.MatchStateDto get(@PathVariable UUID mid) { return queries.get(mid, current.requireId()); }

  @PostMapping("/matches/{mid}/advance")
  @Operation(summary = "RF27/RF42 — Avançar a partida", description = "mode = PLAY (padrão) | DRIVE | QUARTER | TO_DECISION | FULL; todo modo para na próxima "
      + "decisão do manager e no fim. 409 DECISION_PENDING com decisão aberta no prazo (vencido, a IA chama e o avanço segue); version opcional (409 "
      + "MATCH_VERSION_CONFLICT). Ao terminar, grava o resultado e fecha a semana.")
  public MatchDtos.MatchStateDto advance(@PathVariable UUID mid, @Valid @RequestBody(required = false) AdvanceRequest r) {
    return advance.execute(mid, current.requireId(), new AdvanceMatchUseCase.Command(r == null ? null : r.mode(), r == null ? null : r.version()));
  }

  @PostMapping("/matches/{mid}/decisions")
  @Operation(summary = "RF29 — Playbook do intervalo", description = "Ajustes GAME_PLAN e PLAYBOOK (só entre quartos, no intervalo ou antes do kickoff: 422 "
      + "ADJUSTMENT_NOT_ALLOWED) e TIMEOUT (bola morta antes do snap: 422 TIMEOUT_NOT_ALLOWED). Valem do próximo snap; não avançam a partida. version obrigatório.")
  public MatchDtos.MatchStateDto decisions(@PathVariable UUID mid, @Valid @RequestBody DecisionsRequest r) {
    List<MatchAdjustmentsUseCase.Adjustment> list = r.adjustments().stream().map(a -> new MatchAdjustmentsUseCase.Adjustment(a.type(),
        a.gamePlan() == null ? null : new MatchAdjustmentsUseCase.PlanPatch(a.gamePlan().passRatio(), a.gamePlan().fourthDownAggression(), a.gamePlan().tempo(),
            a.gamePlan().defensiveFocus(), a.gamePlan().blitzFrequency(), a.gamePlan().baseDefense()),
        a.playbook() == null ? null : new MatchAdjustmentsUseCase.PlaybookPatch(situations(a.playbook().situations()), weights(a.playbook().coverages()))))
        .toList();
    return adjustments.execute(mid, current.requireId(), new MatchAdjustmentsUseCase.Command(r.version(), list));
  }

  @PostMapping("/matches/{mid}/play-calls/{decisionId}")
  @Operation(summary = "RF62 — Chamar a jogada", description = "Escolhe uma opção (optionCode) da decisão aberta e resolve a jogada. 404 DECISION_NOT_FOUND; "
      + "409 DECISION_CLOSED (já resolvida, ou prazo vencido: a IA chamou); 422 PLAY_NOT_OFFERED; 409 MATCH_VERSION_CONFLICT.")
  public MatchDtos.MatchStateDto playCall(@PathVariable UUID mid, @PathVariable @Size(max = 64) String decisionId, @Valid @RequestBody PlayCallRequest r) {
    return playCalls.execute(mid, current.requireId(), decisionId, new PlayCallUseCase.Command(r.optionCode(), r.version()));
  }

  @GetMapping("/matches/{mid}/events")
  @Operation(summary = "RF27 — Play-by-play (cursor)", description = "Lances com seq > after, até limit (máx. 1000), no vocabulário do motor (textKey + params).")
  public MatchDtos.MatchEventsDto events(@PathVariable UUID mid, @RequestParam(defaultValue = "0") int after, @RequestParam(defaultValue = "200") int limit) {
    return queries.events(mid, current.requireId(), after, limit);
  }

  @GetMapping("/matches/{mid}/report")
  @Operation(summary = "RF27/RF31 — Relatório pós-jogo", description = "Box score das equipes e dos jogadores, líderes, drives, jogadas de pontuação e decisões. "
      + "Aceita o id da partida ao vivo ou do jogo do calendário (inclusive jogos da IA). 422 MATCH_NOT_FINISHED.")
  public MatchDtos.MatchReportDto report(@PathVariable UUID mid) { return queries.report(mid, current.requireId()); }

  static List<GamePlanPolicy.SituationInput> situations(List<SituationRequest> in) {
    return in == null ? List.of() : in.stream().map(s -> s == null ? null : new GamePlanPolicy.SituationInput(s.situation(), weights(s.concepts()))).toList();
  }

  static List<GamePlanPolicy.WeightInput> weights(List<WeightRequest> in) {
    return in == null ? List.of() : in.stream().map(w -> w == null ? null : new GamePlanPolicy.WeightInput(w.item(), w.weight())).toList();
  }
}
