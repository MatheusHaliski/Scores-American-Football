package br.com.velion.scoresaf.web.controller.analysis;

import br.com.velion.scoresaf.application.dto.AnalysisDtos;
import br.com.velion.scoresaf.application.usecase.rf41.AnalysisUseCases;
import br.com.velion.scoresaf.web.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analista do sistema ({@code AnalysisController} do basquete, por regras e sem provedor externo, D-104). Limite de 6 pedidos por
 * minuto por IP nas rotas {@code /analysis} ({@code AuthRateLimitFilter}, como no basquete). Carreira de outro usuário: 403 auditado.
 */
@RestController
@RequestMapping("/api/v1/careers/{id}/analysis")
@Tag(name = "RF41 — Analista do sistema")
public class AnalysisController {
  private final AnalysisUseCases analysis; private final CurrentUser current;

  public AnalysisController(AnalysisUseCases analysis, CurrentUser current) { this.analysis = analysis; this.current = current; }

  @GetMapping
  @Operation(summary = "Analista — último relatório", description = "Pontos fortes e fracos por unidade contra a média da liga, os próximos 3 adversários "
      + "(confrontos-chave e chance de vitória), trocas recomendadas no depth chart e ajustes do plano de jogo. 404 ANALYSIS_NOT_FOUND antes do primeiro.")
  public AnalysisDtos.AnalysisDto get(@PathVariable UUID id) { return analysis.get(id, current.requireId()); }

  @PostMapping("/refresh")
  @Operation(summary = "Analista — gerar novo relatório", description = "Roda o analista por regras com o elenco, o depth chart e o plano de jogo de hoje e "
      + "grava o relatório (auditoria ANALYSIS_GENERATED). Limite: 6 por minuto por IP (429 RATE_LIMITED).")
  public AnalysisDtos.AnalysisDto refresh(@PathVariable UUID id) { return analysis.refresh(id, current.requireId()); }
}
