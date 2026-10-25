package br.com.velion.scoresaf.web.controller.season;

import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.usecase.rf21.GetCalendarUseCase;
import br.com.velion.scoresaf.web.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF16 / RF21 — temporada da carreira. Bloco 6a: só a leitura do calendário gerado (classificação, playoffs e avanço vêm no 6c). */
@RestController
@RequestMapping("/api/v1/careers/{id}")
@Tag(name = "RF16/RF21 — Temporada e calendário")
public class SeasonController {
  private final GetCalendarUseCase calendar;
  private final CurrentUser current;

  public SeasonController(GetCalendarUseCase calendar, CurrentUser current) { this.calendar = calendar; this.current = current; }

  @GetMapping("/calendar")
  @Operation(summary = "RF21 — Calendário da temporada", description = "Semanas com os jogos (quinta, domingo cedo/tarde/noite, segunda), folgas e a "
      + "semana do usuário; week opcional filtra uma semana.")
  public CareerDtos.CalendarDto calendar(@PathVariable UUID id, @RequestParam(required = false) Integer week) {
    return calendar.execute(id, current.requireId(), week);
  }
}
