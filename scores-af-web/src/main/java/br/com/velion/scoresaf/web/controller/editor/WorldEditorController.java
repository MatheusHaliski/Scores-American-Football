package br.com.velion.scoresaf.web.controller.editor;

import br.com.velion.scoresaf.application.dto.EditorDtos;
import br.com.velion.scoresaf.application.usecase.rf05.EditorActor;
import br.com.velion.scoresaf.application.usecase.rf05.WorldEditorUseCases;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.web.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF5 — editor de mundos: as mesmas rotas do basquete e a estrutura da liga ({@code PUT /worlds/{id}/leagues/{lid}/structure},
 * conferências e divisões). Exige login; o acesso por mundo (dono, mundo padrão só leitura, ADMIN só leitura) fica na
 * {@code WorldAccess}. Erros no contrato do esqueleto (RFC 7807 com {@code code} traduzido).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "RF5 — Editor de mundos")
public class WorldEditorController {
  public record WorldRequest(@Size(max = 120) String name, @Size(max = 500) String description) {}

  private final WorldEditorUseCases editor;
  private final CurrentUser current;

  public WorldEditorController(WorldEditorUseCases editor, CurrentUser current) { this.editor = editor; this.current = current; }

  private EditorActor actor() { return new EditorActor(current.requireId(), current.hasRole(UserRole.ADMIN.name())); }

  // ---- mundos
  @GetMapping("/worlds") @Operation(summary = "RF5 — Listar mundos (padrão + meus modelos)")
  public List<EditorDtos.WorldDto> worlds() { return editor.listWorlds(actor()); }

  @GetMapping("/worlds/{id}") @Operation(summary = "RF5 — Detalhar mundo")
  public EditorDtos.WorldDto world(@PathVariable UUID id) { return editor.getWorld(id, actor()); }

  @PostMapping("/worlds/{id}/clone") @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "RF5 — Clonar mundo (padrão, meu modelo ou o mundo de uma carreira minha) para um modelo editável",
      description = "O mundo padrão nunca é alterado; a carreira continua com o próprio mundo (Q-26, RNF1).")
  public EditorDtos.WorldDto clone(@PathVariable UUID id, @Valid @RequestBody(required = false) WorldRequest r) {
    return editor.cloneWorld(id, actor(), r == null ? null : r.name());
  }

  @PostMapping("/worlds") @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "RF5 — Criar mundo modelo (cópia do padrão)")
  public EditorDtos.WorldDto create(@Valid @RequestBody(required = false) WorldRequest r) { return editor.createWorld(actor(), r == null ? null : r.name()); }

  @PutMapping("/worlds/{id}") @Operation(summary = "RF5 — Renomear mundo")
  public EditorDtos.WorldDto update(@PathVariable UUID id, @Valid @RequestBody WorldRequest r) {
    return editor.updateWorld(id, actor(), r.name(), r.description());
  }

  @DeleteMapping("/worlds/{id}") @Operation(summary = "RF5 — Excluir mundo modelo")
  public ResponseEntity<Void> delete(@PathVariable UUID id) { editor.deleteWorld(id, actor()); return ResponseEntity.noContent().build(); }

  // ---- ligas e estrutura
  @GetMapping("/worlds/{id}/leagues") @Operation(summary = "RF5 — Ligas do mundo")
  public List<EditorDtos.LeagueDto> leagues(@PathVariable UUID id) { return editor.listLeagues(id, actor()); }

  @PostMapping("/worlds/{id}/leagues") @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "RF5 — Criar liga", description = "Regras PRO ou COLLEGE (LeagueRules), teto e piso salarial.")
  public EditorDtos.LeagueDto createLeague(@PathVariable UUID id, @RequestBody WorldEditorUseCases.LeagueCommand c) {
    return editor.createLeague(id, actor(), c);
  }

  @GetMapping("/worlds/{id}/leagues/{lid}/structure") @Operation(summary = "RF5 — Conferências, divisões e franquias da liga")
  public EditorDtos.StructureDto structure(@PathVariable UUID id, @PathVariable UUID lid) { return editor.getStructure(id, lid, actor()); }

  @PutMapping("/worlds/{id}/leagues/{lid}/structure")
  @Operation(summary = "RF5 — Definir conferências e divisões", description = "1–2 conferências, o mesmo número de divisões (1–4) em cada uma, "
      + "toda franquia da liga em exatamente uma divisão, 8–32 franquias e divisões equilibradas.")
  public EditorDtos.StructureDto updateStructure(@PathVariable UUID id, @PathVariable UUID lid, @RequestBody EditorDtos.StructureDto body) {
    return editor.updateStructure(id, lid, actor(), body);
  }

  @PutMapping("/leagues/{id}") @Operation(summary = "RF5 — Editar liga (regras, teto salarial)")
  public EditorDtos.LeagueDto updateLeague(@PathVariable UUID id, @RequestBody WorldEditorUseCases.LeagueCommand c) {
    return editor.updateLeague(id, actor(), c);
  }

  @DeleteMapping("/leagues/{id}") @Operation(summary = "RF5 — Excluir liga (sem franquias)")
  public ResponseEntity<Void> deleteLeague(@PathVariable UUID id) { editor.deleteLeague(id, actor()); return ResponseEntity.noContent().build(); }

  // ---- franquias
  @GetMapping("/leagues/{id}/clubs") @Operation(summary = "RF5 — Franquias da liga")
  public List<EditorDtos.ClubDto> clubs(@PathVariable UUID id) { return editor.listClubs(id, actor()); }

  @PostMapping("/leagues/{id}/clubs") @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "RF5 — Criar franquia (com estádio, uniformes, capacete e head coach)")
  public EditorDtos.ClubDto createClub(@PathVariable UUID id, @RequestBody WorldEditorUseCases.ClubCommand c) { return editor.createClub(id, actor(), c); }

  @GetMapping("/clubs/{id}") @Operation(summary = "RF5 — Detalhar franquia")
  public EditorDtos.ClubDto club(@PathVariable UUID id) { return editor.getClub(id, actor()); }

  @PutMapping("/clubs/{id}") @Operation(summary = "RF5 — Editar franquia (nome, cores, finanças)")
  public EditorDtos.ClubDto updateClub(@PathVariable UUID id, @RequestBody WorldEditorUseCases.ClubCommand c) { return editor.updateClub(id, actor(), c); }

  @DeleteMapping("/clubs/{id}") @Operation(summary = "RF5 — Excluir franquia (jogadores viram agentes livres)")
  public ResponseEntity<Void> deleteClub(@PathVariable UUID id) { editor.deleteClub(id, actor()); return ResponseEntity.noContent().build(); }

  // ---- estádio
  @GetMapping("/clubs/{id}/stadium") @Operation(summary = "RF5 — Estádio e setores da franquia")
  public EditorDtos.StadiumDto stadium(@PathVariable UUID id) { return editor.getStadium(id, actor()); }

  @PutMapping("/clubs/{id}/stadium") @Operation(summary = "RF5 — Editar estádio (cobertura, piso, setores 60–80 mil, ingressos)")
  public EditorDtos.StadiumDto updateStadium(@PathVariable UUID id, @RequestBody WorldEditorUseCases.StadiumCommand c) {
    return editor.updateStadium(id, actor(), c);
  }

  // ---- jogadores
  @GetMapping("/clubs/{id}/players") @Operation(summary = "RF5 — Jogadores da franquia")
  public List<EditorDtos.PlayerSummaryDto> players(@PathVariable UUID id) { return editor.listPlayers(id, actor()); }

  @PostMapping("/clubs/{id}/players") @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "RF5 — Criar jogador", description = "Posição, altura e peso dentro da faixa da posição, subatributos 0–100, limites de elenco da liga.")
  public EditorDtos.PlayerEditorDto createPlayer(@PathVariable UUID id, @RequestBody WorldEditorUseCases.PlayerCommand c) {
    return editor.createPlayer(id, actor(), c);
  }

  @GetMapping("/players/{id}") @Operation(summary = "RF5 — Detalhar jogador (overall por posição, macros, contrato)")
  public EditorDtos.PlayerEditorDto player(@PathVariable UUID id) { return editor.getPlayer(id, actor()); }

  @PutMapping("/players/{id}") @Operation(summary = "RF5 — Editar jogador")
  public EditorDtos.PlayerEditorDto updatePlayer(@PathVariable UUID id, @RequestBody WorldEditorUseCases.PlayerCommand c) {
    return editor.updatePlayer(id, actor(), c);
  }

  @DeleteMapping("/players/{id}") @Operation(summary = "RF5 — Excluir jogador")
  public ResponseEntity<Void> deletePlayer(@PathVariable UUID id) { editor.deletePlayer(id, actor()); return ResponseEntity.noContent().build(); }
}
