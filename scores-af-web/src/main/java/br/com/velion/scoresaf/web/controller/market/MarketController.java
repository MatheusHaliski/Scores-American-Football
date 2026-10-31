package br.com.velion.scoresaf.web.controller.market;

import br.com.velion.scoresaf.application.dto.MarketDtos;
import br.com.velion.scoresaf.application.usecase.rf49.ContractUseCases;
import br.com.velion.scoresaf.application.usecase.rf65.FreeAgencyUseCases;
import br.com.velion.scoresaf.application.usecase.rf65.WaiverUseCases;
import br.com.velion.scoresaf.web.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF49 / RF65 — teto salarial, free agency e waivers da carreira (MarketController do basquete: no futebol americano não há compra entre clubes). */
@RestController
@RequestMapping("/api/v1/careers/{id}")
@Tag(name = "RF49/RF65 — Mercado")
public class MarketController {
  public record SignRequest(@NotNull UUID playerId, @Size(max = 32) String rosterStatus, @Min(1) @Max(5) Integer years,
                            @Size(max = 5) List<@NotNull @Min(0) Long> salaryByYear, @Min(0) Long signingBonus, @Min(0) Long guaranteed) {}
  public record AdvanceRequest(@Size(max = 16) String mode) {}

  private final ContractUseCases contracts; private final FreeAgencyUseCases freeAgency; private final WaiverUseCases waivers; private final CurrentUser current;

  public MarketController(ContractUseCases contracts, FreeAgencyUseCases freeAgency, WaiverUseCases waivers, CurrentUser current) {
    this.contracts = contracts; this.freeAgency = freeAgency; this.waivers = waivers; this.current = current;
  }

  @GetMapping("/cap")
  @Operation(summary = "RF49 — Folha salarial", description = "Teto, comprometido, espaço, dead money da temporada e da seguinte, projeção das "
      + "próximas 3 temporadas, cada contrato ano a ano (salário-base, luvas rateadas, garantia, cap hit, dead money) e as ações possíveis.")
  public MarketDtos.CapSheetDto cap(@PathVariable UUID id) { return contracts.cap(id, current.requireId()); }

  @GetMapping("/free-agency")
  @Operation(summary = "RF65 — Free agency", description = "Janela (dia, desconto), teto e elenco do usuário, agentes livres com o pedido do dia "
      + "(filtros position, unit, minOverall, maxAge; sort OVERALL | ASKING | AGE | POSITION; limit até 200) e as últimas contratações.")
  public MarketDtos.FreeAgencyDto list(@PathVariable UUID id, @RequestParam(required = false) String position, @RequestParam(required = false) String unit,
                                       @RequestParam(required = false) Integer minOverall, @RequestParam(required = false) Integer maxAge,
                                       @RequestParam(required = false) String sort, @RequestParam(required = false) Integer limit) {
    return freeAgency.list(id, current.requireId(), new FreeAgencyUseCases.Filter(position, unit, minOverall, maxAge, sort, limit));
  }

  @PostMapping("/free-agency")
  @Operation(summary = "RF65 — Proposta a um agente livre", description = "Nos 53 (anos, salário por ano, luvas, garantias) ou no practice squad. "
      + "O jogador aceita ou recusa (422 OFFER_REFUSED com a contraproposta em details); teto rígido (422 CAP_EXCEEDED) e limites do elenco.")
  public MarketDtos.ContractResultDto sign(@PathVariable UUID id, @Valid @RequestBody SignRequest r) {
    return freeAgency.sign(id, current.requireId(), new FreeAgencyUseCases.SignCommand(r.playerId(), r.rosterStatus(), r.years(), r.salaryByYear(),
        r.signingBonus(), r.guaranteed()));
  }

  @PostMapping("/free-agency/advance")
  @Operation(summary = "RF65 — Avançar a free agency", description = "mode DAY (padrão: um dia; a IA contrata, os waivers se resolvem, os pedidos "
      + "caem) ou CLOSE (até fechar; a temporada passa à temporada regular). 422 FREE_AGENCY_CLOSED fora da janela.")
  public MarketDtos.FreeAgencyDto advance(@PathVariable UUID id, @Valid @RequestBody(required = false) AdvanceRequest r) {
    return freeAgency.advance(id, current.requireId(), r == null ? null : r.mode());
  }

  @GetMapping("/waivers")
  @Operation(summary = "RF65 — Waivers", description = "Jogadores expostos (dispensados e rumo ao practice squad), prioridade do usuário na fila "
      + "(ordem inversa da classificação), quando o período fecha e as últimas reivindicações.")
  public MarketDtos.WaiversDto waivers(@PathVariable UUID id) { return waivers.list(id, current.requireId()); }

  @PostMapping("/waivers/{playerId}/claim")
  @Operation(summary = "RF65 — Reivindicar em waivers", description = "Fica pendente até o fim do período; a fila decide pela prioridade. 409 "
      + "WAIVER_CLAIM_EXISTS se repetida; 422 sem vaga, sem teto ou jogador exposto pela própria franquia.")
  public MarketDtos.WaiversDto claim(@PathVariable UUID id, @PathVariable UUID playerId) { return waivers.claim(id, current.requireId(), playerId); }

  @DeleteMapping("/waivers/{playerId}/claim")
  @Operation(summary = "RF65 — Cancelar a reivindicação")
  public MarketDtos.WaiversDto cancelClaim(@PathVariable UUID id, @PathVariable UUID playerId) { return waivers.cancel(id, current.requireId(), playerId); }
}
