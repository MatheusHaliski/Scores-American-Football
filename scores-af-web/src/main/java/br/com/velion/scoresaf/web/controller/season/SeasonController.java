package br.com.velion.scoresaf.web.controller.season;

import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.dto.SeasonDtos;
import br.com.velion.scoresaf.application.usecase.rf16.GetStandingsUseCase;
import br.com.velion.scoresaf.application.usecase.rf17.GetTrophiesUseCase;
import br.com.velion.scoresaf.application.usecase.rf21.GetCalendarUseCase;
import br.com.velion.scoresaf.application.usecase.rf21.GetWeekNewsUseCase;
import br.com.velion.scoresaf.application.usecase.rf35.SeasonUseCases;
import br.com.velion.scoresaf.application.usecase.rf66.PlayoffUseCases;
import br.com.velion.scoresaf.web.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF16 / RF21 / RF35 / RF66 — temporada da carreira ({@code SeasonController} do basquete, com {@code /rounds/{n}/news} →
 * {@code /weeks/{n}/news} e a rota nova {@code /playoffs/bracket}): classificação, calendário, notícias da semana, playoffs, sala de
 * troféus, resumo e avanço da temporada. Carreira de outro usuário: 403 auditado (D-59).
 */
@RestController
@RequestMapping("/api/v1/careers/{id}")
@Tag(name = "RF16/RF21/RF35/RF66 — Temporada")
public class SeasonController {
  /** Avanço da temporada: {@code seasonId} (opcional) é a temporada que o cliente quer fechar (409 se ela já foi fechada). */
  public record AdvanceRequest(UUID seasonId) {}

  private final GetStandingsUseCase standings; private final GetCalendarUseCase calendar; private final GetWeekNewsUseCase news;
  private final PlayoffUseCases playoffs; private final GetTrophiesUseCase trophies; private final SeasonUseCases seasons; private final CurrentUser current;

  public SeasonController(GetStandingsUseCase standings, GetCalendarUseCase calendar, GetWeekNewsUseCase news, PlayoffUseCases playoffs,
                          GetTrophiesUseCase trophies, SeasonUseCases seasons, CurrentUser current) {
    this.standings = standings; this.calendar = calendar; this.news = news; this.playoffs = playoffs; this.trophies = trophies; this.seasons = seasons;
    this.current = current;
  }

  @GetMapping("/standings")
  @Operation(summary = "RF16 — Classificação", description = "Por divisão e por conferência: V-D-E, %, pontos pró e contra, saldo, sequência, últimos 5, "
      + "casa/fora, divisão e conferência, semente (projeção até o fim da temporada regular), vaga/título garantidos, eliminação e o critério de desempate "
      + "(confronto direto, divisão, jogos em comum, conferência, força das vitórias, força da tabela, saldo, sorteio).")
  public SeasonDtos.StandingsDto standings(@PathVariable UUID id) { return standings.execute(id, current.requireId()); }

  @GetMapping("/calendar")
  @Operation(summary = "RF21 — Calendário da temporada", description = "Semanas com os jogos (quinta, domingo cedo/tarde/noite, segunda) e placares, folgas, "
      + "a semana do usuário, as semanas de playoff criadas e as rodadas de playoff planejadas; week opcional filtra uma semana.")
  public CareerDtos.CalendarDto calendar(@PathVariable UUID id, @RequestParam(required = false) Integer week) {
    return calendar.execute(id, current.requireId(), week);
  }

  @GetMapping("/weeks/{n}/news")
  @Operation(summary = "RF21 — Notícias da semana", description = "Geradas por regras a partir dos jogos e relatórios da semana (resultado do usuário, "
      + "atropelo, jogo mais emocionante, destaques individuais, sequências, vagas, eliminações, sementes, playoffs e campeã), no idioma do pedido.")
  public SeasonDtos.WeekNewsDto news(@PathVariable UUID id, @PathVariable int n) { return news.execute(id, current.requireId(), n); }

  @GetMapping("/playoffs")
  @Operation(summary = "RF66 — Playoffs", description = "Situação (NOT_STARTED com sementes projetadas, IN_PROGRESS, FINISHED), sementes por conferência "
      + "(7, folga da 1ª), rodada atual, o usuário (semente, situação, próximo jogo), campeã e vice.")
  public SeasonDtos.PlayoffsDto playoffs(@PathVariable UUID id) { return playoffs.situation(id, current.requireId()); }

  @GetMapping("/playoffs/bracket")
  @Operation(summary = "RF66 — Chave dos playoffs", description = "Rodadas (Wild Card, Divisional, Conferência, Final) com os jogos, placares, prorrogação, "
      + "vencedor e folgas; re-seed a cada rodada; final em campo neutro; antes do fim da temporada regular, a primeira rodada projetada.")
  public SeasonDtos.BracketDto bracket(@PathVariable UUID id) { return playoffs.bracket(id, current.requireId()); }

  @GetMapping("/trophies")
  @Operation(summary = "RF35/RF17 — Sala de troféus", description = "Títulos de divisão, conferência e liga por temporada (com a marca do usuário), a "
      + "contagem do usuário e o histórico (campeã, vice, MVP, resultado do usuário).")
  public SeasonDtos.TrophiesDto trophies(@PathVariable UUID id) { return trophies.execute(id, current.requireId()); }

  @GetMapping("/season/summary")
  @Operation(summary = "RF35 — Resumo da temporada", description = "Campeã e vice, campanha do usuário, prêmios (MVP, OPOY, DPOY, OROY, DROY, head coach; "
      + "provisórios até a final), líderes e a entressafra (contratos que terminam, passos e ganchos do bloco 7). number opcional: outra temporada da carreira.")
  public SeasonDtos.SeasonSummaryDto summary(@PathVariable UUID id, @RequestParam(required = false) Integer number) {
    return seasons.summary(id, current.requireId(), number);
  }

  @PostMapping("/season/advance")
  @Operation(summary = "RF35 — Avançar a temporada", description = "Só depois da final (422 SEASON_NOT_FINISHED): waivers, moral, contratos que terminam "
      + "viram agentes livres (pendências do bloco 7), free agency e draft (bloco 7, pulados), envelhecimento e progressão, calendário novo pela "
      + "classificação final. seasonId opcional: temporada que o cliente quer fechar (409 SEASON_ALREADY_ADVANCED se já foi fechada).")
  public SeasonDtos.SeasonAdvanceDto advance(@PathVariable UUID id, @RequestBody(required = false) AdvanceRequest r) {
    return seasons.advance(id, current.requireId(), r == null ? null : r.seasonId());
  }
}
