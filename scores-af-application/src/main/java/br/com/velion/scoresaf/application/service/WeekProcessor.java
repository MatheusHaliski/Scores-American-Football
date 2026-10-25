package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.FixtureStatus;
import br.com.velion.scoresaf.domain.shared.WeekStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Fechamento da semana (RF27; {@code service/round} do basquete → {@code service/week}): simula inteiros os jogos da semana que não
 * têm partida ao vivo (os da IA, na ordem dos horários) e, quando todos acabaram, encerra a semana, abre a próxima e avança a semana
 * atual da temporada e da carreira. A classificação sai dos placares gravados ({@link StandingsService}, D-95). Fechar a última
 * semana da temporada regular abre os playoffs e fechar uma semana de playoff cria a rodada seguinte, até a final encerrar a temporada
 * ({@link PlayoffService}, D-96/D-97). Fechar a semana também encerra o período de waivers de quem saiu dos 53 rumo ao practice squad
 * ({@link WaiverWire}, Q-23, D-78), inclusive nas semanas de playoff (Q-39).
 */
@Service
public class WeekProcessor {
  private final FixtureRepository fixtures; private final WeekRepository weeks; private final SeasonRepository seasons; private final CareerSaveRepository saves;
  private final MatchSessionRepository sessions; private final MatchService matches; private final WaiverWire waivers; private final AuditService audit;
  private final PlayoffService playoffs;

  public WeekProcessor(FixtureRepository fixtures, WeekRepository weeks, SeasonRepository seasons, CareerSaveRepository saves, MatchSessionRepository sessions,
                       MatchService matches, WaiverWire waivers, AuditService audit, PlayoffService playoffs) {
    this.fixtures = fixtures; this.weeks = weeks; this.seasons = seasons; this.saves = saves; this.sessions = sessions; this.matches = matches;
    this.waivers = waivers; this.audit = audit; this.playoffs = playoffs;
  }

  /**
   * Simula o que falta da semana {@code week} e, se tudo acabou, fecha a semana. Uma partida ao vivo ainda não encerrada segura o
   * fechamento (devolve {@code false}). Idempotente: semana já fechada não muda nada.
   */
  public boolean close(CareerSave save, Season season, int week) {
    List<Fixture> fx = fixtures.findBySeasonIdAndWeekNumber(season.getId(), week).stream().sorted(CareerAssembler.FIXTURE_ORDER).toList();
    for (Fixture f : fx) {
      if (f.isFinished() || f.getStatus() == FixtureStatus.LIVE || sessions.findByFixtureId(f.getId()).isPresent()) continue;
      matches.simulateFull(save, f);
    }
    if (fx.stream().anyMatch(f -> !f.isFinished())) return false;
    if (season.getCurrentWeek() != week) return true;
    weeks.findBySeasonIdAndNumber(season.getId(), week).ifPresent(w -> { w.setStatus(WeekStatus.COMPLETED); weeks.save(w); });
    weeks.findBySeasonIdAndNumber(season.getId(), week + 1).ifPresent(w -> { w.setStatus(WeekStatus.CURRENT); weeks.save(w); });
    season.setCurrentWeek(week + 1);
    seasons.save(season);
    save.setCurrentWeek(week + 1);
    save.setLastPlayedAt(Instant.now());
    saves.save(save);
    playoffs.afterWeek(save, season, week);
    waivers.resolve(save, season, week);
    audit.ok(AuditActions.WEEK_COMPLETED, "Season", season.getId(), "semana " + week + " encerrada (" + fx.size() + " jogos)");
    return true;
  }
}
