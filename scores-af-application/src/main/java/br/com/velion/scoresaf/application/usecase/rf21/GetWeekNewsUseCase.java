package br.com.velion.scoresaf.application.usecase.rf21;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.common.i18n.LocalizedMessages;
import br.com.velion.scoresaf.application.dto.SeasonDtos;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerMatchStatsRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.application.service.StandingsService;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.match.PlayerMatchStats;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Standings;
import br.com.velion.scoresaf.domain.season.Week;
import br.com.velion.scoresaf.domain.shared.WeekKind;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF21 — notícias da semana ({@code /rounds/{n}/news} do basquete → {@code /weeks/{n}/news}), geradas por regras na leitura a partir dos
 * jogos, da classificação e do box score gravados ({@link WeekNews}, D-103). Texto no idioma do pedido ({@code news.<KIND>.*} nos
 * catálogos), mais a chave e os parâmetros. Id determinístico por temporada, semana, tipo e referência. Semana sem jogo encerrado:
 * lista vazia; fora da temporada: 400 INVALID_WEEK.
 */
@Service
public class GetWeekNewsUseCase {
  private final CareerSupport support; private final WeekRepository weeks; private final FixtureRepository fixtures; private final PlayerMatchStatsRepository stats;
  private final StandingsService standings; private final LocalizedMessages messages;

  public GetWeekNewsUseCase(CareerSupport support, WeekRepository weeks, FixtureRepository fixtures, PlayerMatchStatsRepository stats,
                            StandingsService standings, LocalizedMessages messages) {
    this.support = support; this.weeks = weeks; this.fixtures = fixtures; this.stats = stats; this.standings = standings; this.messages = messages;
  }

  @Transactional(readOnly = true)
  public SeasonDtos.WeekNewsDto execute(UUID careerId, UUID userId, int n) {
    CareerSave save = support.owned(careerId, userId);
    Season season = save.getSeason();
    if (season == null) throw new BusinessRuleException("NO_SEASON", "Carreira sem temporada");
    int last = CareerAssembler.lastWeek(weeks.findBySeasonIdOrderByNumberAsc(season.getId()), season);
    if (n < 1 || n > last) throw new InvalidInputException("INVALID_WEEK", "Semana inválida: " + n, n, last);
    return news(save, season, n, LocaleContextHolder.getLocale());
  }

  /** Notícias da semana {@code n} (também usadas pela central). */
  public SeasonDtos.WeekNewsDto news(CareerSave save, Season season, int n, Locale locale) {
    Week week = weeks.findBySeasonIdAndNumber(season.getId(), n).orElse(null);
    WeekKind kind = week == null ? WeekKind.REGULAR : week.getKind();
    List<Fixture> all = fixtures.findBySeasonId(season.getId()).stream().sorted(CareerAssembler.FIXTURE_ORDER).toList();
    List<Fixture> mine = all.stream().filter(f -> f.getWeekNumber() == n).toList();
    boolean completed = !mine.isEmpty() && mine.stream().allMatch(Fixture::isFinished);
    List<WeekNews.Game> games = new ArrayList<>();
    List<WeekNews.Line> lines = new ArrayList<>();
    for (Fixture f : mine) {
      if (!f.isFinished() || f.getHomeScore() == null) continue;
      games.add(new WeekNews.Game(f.getId(), f.getHomeClub().getId(), f.getHomeClub().getName(), f.getAwayClub().getId(), f.getAwayClub().getName(),
          f.getHomeScore(), f.getAwayScore(), f.isOvertime()));
      for (PlayerMatchStats s : stats.findByFixtureId(f.getId())) {
        lines.add(new WeekNews.Line(s.getPlayer().getId(), s.getPlayer().getName(), s.getClub().getId(), f.getId(), s.getPassYards(), s.getPassTouchdowns(),
            s.getRushYards(), s.getRushTouchdowns(), s.getReceivingYards(), s.getReceivingTouchdowns(), s.getSacks(), s.getInterceptions()));
      }
    }
    List<SeasonDtos.NewsDto> items = new ArrayList<>();
    if (!games.isEmpty()) {
      LeagueRules rules = CareerSupport.rules(save);
      StandingsService.League league = standings.league(season);
      boolean regular = n <= season.getRegularSeasonWeeks();
      int through = regular ? n : season.getRegularSeasonWeeks();
      Standings.Table after = standings.table(season, league, all, through, rules);
      Map<UUID, StandingsService.Clinch> clinchAfter = regular ? standings.clinches(season, league, all, n, after, rules) : Map.of();
      Map<UUID, StandingsService.Clinch> clinchBefore = regular
          ? standings.clinches(season, league, all, n - 1, standings.table(season, league, all, n - 1, rules), rules) : Map.of();
      Map<UUID, String> names = new HashMap<>(), divisions = new HashMap<>();
      for (Club c : league.clubs()) {
        names.put(c.getId(), c.getName());
        if (c.getDivision() != null) divisions.put(c.getId(), c.getDivision().getName());
      }
      Map<String, String> conferences = new HashMap<>();
      for (Conference c : league.conferences()) conferences.put(c.getId().toString(), c.getName());
      String roundLabel = messages.get("news.round." + kind.name(), kind.name(), locale);
      WeekNews.Input in = new WeekNews.Input(n, !regular, roundLabel, kind == WeekKind.FINAL, regular && n == season.getRegularSeasonWeeks() && completed,
          season.getYear(), save.getClub() == null ? null : save.getClub().getId(), games, lines, names, after, clinchBefore, clinchAfter, divisions,
          conferences);
      for (WeekNews.Item it : WeekNews.build(in)) items.add(render(season, n, it, locale));
    }
    return new SeasonDtos.WeekNewsDto(season.getId(), n, kind.name(), completed, items);
  }

  private SeasonDtos.NewsDto render(Season season, int week, WeekNews.Item it, Locale locale) {
    Object[] args = it.params().values().toArray();
    String key = "news." + it.kind();
    String ref = (it.playerId() != null ? it.playerId() : it.fixtureId() != null ? it.fixtureId() : it.clubId()) + "";
    UUID id = UUID.nameUUIDFromBytes(("news:" + season.getId() + ":" + week + ":" + it.kind() + ":" + ref).getBytes(StandardCharsets.UTF_8));
    return new SeasonDtos.NewsDto(id, week, it.kind(), it.tone(), messages.get(key + ".headline", locale, args), messages.get(key + ".body", locale, args), key,
        it.params(), it.clubId(), it.playerId(), it.fixtureId());
  }
}
