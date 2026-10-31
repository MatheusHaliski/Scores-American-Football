package br.com.velion.scoresaf.web.controller.roster;

import br.com.velion.scoresaf.application.dto.DepthChartDtos;
import br.com.velion.scoresaf.application.dto.MarketDtos;
import br.com.velion.scoresaf.application.dto.RosterDtos;
import br.com.velion.scoresaf.application.usecase.rf04.RosterUseCases;
import br.com.velion.scoresaf.application.usecase.rf49.ContractUseCases;
import br.com.velion.scoresaf.application.usecase.rf60.DepthChartUseCases;
import br.com.velion.scoresaf.web.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF4 / RF56 — elenco da franquia (53 por unidade, practice squad, IR), jogador, movimentos e dispensa (SquadController do basquete). */
@RestController
@RequestMapping("/api/v1/careers/{id}")
@Tag(name = "RF4/RF56 — Elenco")
public class RosterController {
  public record StatusRequest(@NotBlank @Size(max = 32) String status) {}
  public record RoleRequest(@NotBlank @Size(max = 32) String role, @Min(1) @Max(10) Integer depth, Long version) {}
  public record RenewRequest(@NotNull @Min(1) @Max(5) Integer years, @NotNull @Size(min = 1, max = 5) List<@NotNull @Min(0) Long> salaryByYear,
                             @Min(0) Long signingBonus, @Min(0) Long guaranteed) {}
  public record RestructureRequest(@Min(1) Long amount) {}

  private final RosterUseCases roster; private final DepthChartUseCases depthChart; private final ContractUseCases contracts; private final CurrentUser current;

  public RosterController(RosterUseCases roster, DepthChartUseCases depthChart, ContractUseCases contracts, CurrentUser current) {
    this.roster = roster; this.depthChart = depthChart; this.contracts = contracts; this.current = current;
  }

  @GetMapping("/roster")
  @Operation(summary = "RF4 — Elenco", description = "53 por unidade (OFFENSE, DEFENSE, SPECIAL_TEAMS), practice squad e IR, com overall na posição, "
      + "macros, nível, contrato da temporada e vagas do depth chart; contadores, limites da liga e teto salarial.")
  public RosterDtos.RosterDto roster(@PathVariable UUID id) { return roster.roster(id, current.requireId()); }

  @GetMapping("/players/{pid}")
  @Operation(summary = "RF4/RF22 — Jogador da carreira", description = "Qualquer jogador do mundo da carreira (404 fora dele): card, subatributos, overall "
      + "por posição, contrato, dead money se dispensado agora, IR e histórico de movimentos.")
  public RosterDtos.PlayerDetailDto player(@PathVariable UUID id, @PathVariable UUID pid) { return roster.player(id, current.requireId(), pid); }

  @PatchMapping("/players/{pid}/roster-status")
  @Operation(summary = "RF4/RF56 — Mover entre 53, practice squad e IR", description = "status ACTIVE | INACTIVE | PRACTICE_SQUAD | IR, pelas regras da liga "
      + "(53, 16, IR com lesão e semanas mínimas). Sai do depth chart quando deixa de estar ativo.")
  public RosterDtos.RosterChangeDto status(@PathVariable UUID id, @PathVariable UUID pid, @Valid @RequestBody StatusRequest r) {
    return roster.changeStatus(id, current.requireId(), pid, r.status());
  }

  @PostMapping("/players/{pid}/release")
  @Operation(summary = "RF4 — Dispensar", description = "O jogador vai a waivers (WAIVED) e o dead money do contrato entra no teto da temporada; "
      + "no fim do período a fila decide quem o leva (sem reivindicação, vira agente livre).")
  public RosterDtos.ReleaseResultDto release(@PathVariable UUID id, @PathVariable UUID pid) { return roster.release(id, current.requireId(), pid); }

  @PatchMapping("/players/{pid}/role")
  @Operation(summary = "RF60 — Papel no depth chart", description = "role KR | PR | H | THIRD_DOWN_BACK na profundidade depth (1 = titular); "
      + "version opcional (409 se divergir). Devolve o depth chart.")
  public DepthChartDtos.DepthChartDto role(@PathVariable UUID id, @PathVariable UUID pid, @Valid @RequestBody RoleRequest r) {
    return depthChart.role(id, current.requireId(), pid, new DepthChartUseCases.RoleCommand(r.role(), r.depth(), r.version()));
  }

  @PostMapping("/players/{pid}/renew")
  @Operation(summary = "RF49 — Renovar contrato", description = "No último ano do contrato: anos, salário por ano, luvas e garantias. O jogador aceita "
      + "ou recusa pelo valor de mercado, idade, moral e papel (422 OFFER_REFUSED com a contraproposta em details); 422 RENEWAL_NOT_ALLOWED fora da "
      + "janela; teto rígido (422 CAP_EXCEEDED).")
  public MarketDtos.ContractResultDto renew(@PathVariable UUID id, @PathVariable UUID pid, @Valid @RequestBody RenewRequest r) {
    return contracts.renew(id, current.requireId(), pid, new ContractUseCases.OfferCommand(r.years(), r.salaryByYear(), r.signingBonus(), r.guaranteed()));
  }

  @PostMapping("/players/{pid}/restructure")
  @Operation(summary = "RF49 — Reestruturar contrato", description = "Converte salário do ano em luvas rateadas pelos anos restantes (amount; sem "
      + "corpo, o máximo): libera espaço agora e aumenta o dead money futuro. 422 RESTRUCTURE_NOT_ALLOWED / RESTRUCTURE_INVALID / CAP_EXCEEDED.")
  public MarketDtos.ContractResultDto restructure(@PathVariable UUID id, @PathVariable UUID pid, @Valid @RequestBody(required = false) RestructureRequest r) {
    return contracts.restructure(id, current.requireId(), pid, r == null ? null : r.amount());
  }

  @PostMapping("/players/{pid}/franchise-tag")
  @Operation(summary = "RF49 — Franchise tag", description = "Na entressafra, antes da free agency, no último ano do contrato: um ano a mais pela "
      + "média dos maiores cap hits da posição (mínimo 120 % do atual), todo garantido. Uma por temporada (409 FRANCHISE_TAG_USED).")
  public MarketDtos.ContractResultDto franchiseTag(@PathVariable UUID id, @PathVariable UUID pid) {
    return contracts.franchiseTag(id, current.requireId(), pid);
  }

  @PostMapping("/players/{pid}/trade-block")
  @Operation(summary = "RF49/RF50 — Pôr no trade block", description = "Só grava e mostra; as trocas são do bloco 7b.")
  public RosterDtos.PlayerCardDto tradeBlock(@PathVariable UUID id, @PathVariable UUID pid) { return contracts.tradeBlock(id, current.requireId(), pid, true); }

  @PostMapping("/players/{pid}/trade-block/remove")
  @Operation(summary = "RF49/RF50 — Tirar do trade block")
  public RosterDtos.PlayerCardDto tradeBlockRemove(@PathVariable UUID id, @PathVariable UUID pid) {
    return contracts.tradeBlock(id, current.requireId(), pid, false);
  }
}
