package br.com.velion.scoresaf.web.controller.meta;

import br.com.velion.scoresaf.application.meta.GetGameInfoUseCase;
import br.com.velion.scoresaf.domain.game.GameInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Identidade do jogo, pública: o front confere que fala com a API certa (nome, esporte, versão, regras padrão). */
@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Meta")
public class MetaController {
  /** @param apiVersion versão do contrato REST (o caminho {@code /api/v1}) */
  public record MetaDto(String name, String sport, String version, String defaultRuleset, String apiVersion) {
    static MetaDto of(GameInfo g) { return new MetaDto(g.name(), g.sport(), g.version(), g.defaultRuleset().name(), "v1"); }
  }

  private final GetGameInfoUseCase info;

  public MetaController(GetGameInfoUseCase info) { this.info = info; }

  @GetMapping @Operation(summary = "Identidade do jogo", description = "Público. Nome, esporte, versão da build e regras padrão das ligas novas (D-4).")
  public MetaDto get() { return MetaDto.of(info.execute()); }
}
