package br.com.velion.scoresaf.web.controller.career;

import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.dto.MoraleDtos;
import br.com.velion.scoresaf.application.usecase.rf41.GetMoraleUseCase;
import br.com.velion.scoresaf.application.usecase.rf11.CareerUseCases;
import br.com.velion.scoresaf.application.usecase.rf34.CreateCareerUseCase;
import br.com.velion.scoresaf.application.usecase.rf40.GetClubDashboardUseCase;
import br.com.velion.scoresaf.web.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF11 / RF34 / RF40 / RF41 — carreiras salvas (slots), nova carreira, central da franquia e moral do elenco. Toda rota com {@code {id}}
 * exige o dono do save.
 */
@RestController
@RequestMapping("/api/v1/careers")
@Tag(name = "RF11/RF34/RF40/RF41 — Carreiras, central da franquia e moral")
public class CareerController {
  public record CreateRequest(@NotBlank @Size(max = 40) String name, UUID worldId, UUID leagueId, @NotNull UUID clubId, @Size(max = 40) String coachName,
                              @Size(max = 3) String coachNationality, @Size(max = 32) String coachStyle, @Size(max = 32) String difficulty,
                              @Min(1) @Max(5) Integer slot, Long seed) {}

  public record PatchRequest(@Size(max = 40) String name, @Size(max = 32) String difficulty, @Size(max = 32) String status, Long version) {}

  private final CareerUseCases careers; private final CreateCareerUseCase create; private final GetClubDashboardUseCase dashboard;
  private final GetMoraleUseCase morale; private final CurrentUser current;

  public CareerController(CareerUseCases careers, CreateCareerUseCase create, GetClubDashboardUseCase dashboard, GetMoraleUseCase morale,
                          CurrentUser current) {
    this.careers = careers; this.create = create; this.dashboard = dashboard; this.morale = morale; this.current = current;
  }

  @GetMapping @Operation(summary = "RF11 — Carreiras salvas e slots livres", description = "ACTIVE por slot (máx. 5), depois ARCHIVED.")
  public CareerDtos.CareerListDto list() { return careers.list(current.requireId()); }

  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "RF34 — Nova carreira", description = "Mundo (padrão ou meu modelo) → liga → franquia, head coach (estilo OFFENSIVE | DEFENSIVE | "
      + "DEVELOPER | MOTIVATOR), dificuldade, slot e semente opcionais. Copia o mundo para a carreira (RNF1), gera o calendário de 17 jogos + bye e o "
      + "depth chart padrão, tudo numa transação.")
  public CareerDtos.CareerDto create(@Valid @RequestBody CreateRequest r) {
    return create.execute(current.requireId(), new CreateCareerUseCase.Command(r.name(), r.worldId(), r.leagueId(), r.clubId(), r.coachName(),
        r.coachNationality(), r.coachStyle(), r.difficulty(), r.slot(), r.seed()));
  }

  @GetMapping("/{id}") @Operation(summary = "RF11 — Detalhar carreira", description = "403 para carreira de outro usuário (auditado); 404 se não existe.")
  public CareerDtos.CareerDto get(@PathVariable UUID id) { return careers.get(id, current.requireId()); }

  @PatchMapping("/{id}")
  @Operation(summary = "RF11 — Renomear, mudar dificuldade, arquivar ou reativar", description = "status ACTIVE | ARCHIVED; version opcional (409 se divergir).")
  public CareerDtos.CareerDto patch(@PathVariable UUID id, @Valid @RequestBody PatchRequest r) {
    return careers.patch(id, current.requireId(), new CareerUseCases.PatchCommand(r.name(), r.difficulty(), r.status(), r.version()));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "RF11 — Excluir carreira", description = "Exclusão definitiva (save, mundo da carreira, calendário, elenco, depth chart). "
      + "confirm = nome da carreira, exatamente (422 CAREER_CONFIRMATION_REQUIRED).")
  public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) String confirm) {
    careers.delete(id, current.requireId(), confirm);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/dashboard")
  @Operation(summary = "RF40 — Central da franquia", description = "Próximo jogo ou bye, campanha, divisão, próximos jogos de divisão, destaques do elenco, "
      + "lesionados, teto salarial, estado do depth chart, partida ao vivo, moral do elenco, último relatório do analista, notícias da última semana e o "
      + "usuário nos playoffs. inbox e scoresPoints ficam nulos até os blocos que os criam.")
  public CareerDtos.DashboardDto dashboard(@PathVariable UUID id) { return dashboard.execute(id, current.requireId()); }

  @GetMapping("/{id}/morale")
  @Operation(summary = "RF41 — Moral do elenco", description = "Moral do elenco e de cada jogador, por regras: resultados, snaps × esperado para o papel no "
      + "depth chart, contrato, idade e sequência, com os fatores da última semana, tendência, pedido de troca e modificador de desempenho (fora do motor).")
  public MoraleDtos.SquadMoraleDto morale(@PathVariable UUID id) { return morale.execute(id, current.requireId()); }
}
