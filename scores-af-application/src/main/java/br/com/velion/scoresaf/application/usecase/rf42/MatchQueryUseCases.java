package br.com.velion.scoresaf.application.usecase.rf42;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.application.dto.MatchDtos;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchReportRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore;
import br.com.velion.scoresaf.domain.match.MatchReport;
import br.com.velion.scoresaf.domain.match.MatchSession;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF27/RF31 — consultas da partida: estado ao vivo, play-by-play com cursor, relatório pós-jogo (id da sessão ou do jogo do
 * calendário, inclusive jogos da IA) e o painel da semana. Partida de outro usuário: 404 ({@link MatchSupport#ownedSession}).
 */
@Service
public class MatchQueryUseCases {
  public static final int MAX_EVENTS = 1000;

  private final CareerSupport support; private final MatchSupport matches; private final MatchSessionRepository sessions; private final FixtureRepository fixtures;
  private final MatchReportRepository reports; private final ClubRepository clubs; private final WeekRepository weeks;

  public MatchQueryUseCases(CareerSupport support, MatchSupport matches, MatchSessionRepository sessions, FixtureRepository fixtures,
                            MatchReportRepository reports, ClubRepository clubs, WeekRepository weeks) {
    this.support = support; this.matches = matches; this.sessions = sessions; this.fixtures = fixtures; this.reports = reports; this.clubs = clubs;
    this.weeks = weeks;
  }

  @Transactional(readOnly = true)
  public MatchDtos.MatchStateDto get(UUID matchId, UUID userId) {
    MatchSession s = matches.ownedSession(matchId, userId);
    return MatchSupport.state(s, MatchSupport.read(s));
  }

  @Transactional(readOnly = true)
  public MatchDtos.MatchEventsDto events(UUID matchId, UUID userId, int after, int limit) {
    MatchSession s = matches.ownedSession(matchId, userId);
    MatchSnapshot snap = MatchSupport.read(s);
    int max = Math.max(1, Math.min(limit, MAX_EVENTS));
    List<PlayEvent> evs = snap.events().stream().filter(e -> e.seq() > after).limit(max).toList();
    int last = snap.events().isEmpty() ? 0 : snap.events().get(snap.events().size() - 1).seq();
    return new MatchDtos.MatchEventsDto(s.getId(), Math.max(0, after), last, snap.finished(), evs);
  }

  /** {@code mid} pode ser o id da sessão ao vivo ou o id do jogo do calendário (jogos da IA não têm sessão). */
  @Transactional(readOnly = true)
  public MatchDtos.MatchReportDto report(UUID mid, UUID userId) {
    MatchSession session = sessions.findById(mid).orElse(null);
    Fixture f;
    if (session != null) {
      matches.requireOwner(session.getCareer(), userId, "MatchSession", mid);
      f = session.getFixture();
    } else {
      f = fixtures.findById(mid).orElseThrow(() -> new NotFoundException("Partida", mid));
      matches.requireOwner(f.getSeason().getCareer(), userId, "Fixture", mid);
      session = sessions.findByFixtureId(f.getId()).orElse(null);
    }
    if (!f.isFinished()) throw new BusinessRuleException("MATCH_NOT_FINISHED", "O relatório fica disponível ao fim da partida");
    MatchReport r = reports.findByFixtureId(f.getId()).orElseThrow(() -> new NotFoundException("Relatório", mid));
    MatchDtos.ReportData d;
    try {
      d = Jsons.MAPPER.readValue(r.getReportJson(), MatchDtos.ReportData.class);
    } catch (Exception e) {
      throw new IllegalStateException("relatório da partida " + f.getId() + " ilegível", e);
    }
    MatchDtos.ReportSideDto home = new MatchDtos.ReportSideDto(CareerAssembler.club(f.getHomeClub()), r.getHomeScore(), d.homeQuarters(), d.homeTeam(), d.homePlayers());
    MatchDtos.ReportSideDto away = new MatchDtos.ReportSideDto(CareerAssembler.club(f.getAwayClub()), r.getAwayScore(), d.awayQuarters(), d.awayTeam(), d.awayPlayers());
    List<PlayerBoxScore> all = new ArrayList<>(d.homePlayers());
    all.addAll(d.awayPlayers());
    return new MatchDtos.MatchReportDto(f.getId(), session == null ? null : session.getId(), f.getWeekNumber(), f.getKickoffDate(), f.getKickoffSlot().name(),
        r.isUserGame(), r.getSeed(), r.getEngineVersion(), r.getRulesVersion(), d.weather(), home, away, r.isOvertime(), r.getWinnerClubId(), r.getPlayCount(),
        leaders(all), d.drives(), d.scoringPlays(), d.decisions());
  }

  @Transactional(readOnly = true)
  public MatchDtos.WeekBoardDto week(UUID careerId, UUID userId, int n) {
    CareerSave save = support.owned(careerId, userId);
    Season season = save.getSeason();
    if (season == null) throw new BusinessRuleException("NO_SEASON", "Carreira sem temporada");
    int last = CareerAssembler.lastWeek(weeks.findBySeasonIdOrderByNumberAsc(season.getId()), season);
    if (n < 1 || n > last) throw new InvalidInputException("INVALID_WEEK", "Semana inválida: " + n, n, last);
    return matches.board(save, season, n, clubs.findByLeagueIdOrderByNameAsc(season.getLeague().getId()));
  }

  /** Líderes da partida por categoria (o maior valor; empate: camisa menor), um por categoria em que alguém registrou algo. */
  static List<MatchDtos.LeaderDto> leaders(List<PlayerBoxScore> all) {
    List<MatchDtos.LeaderDto> out = new ArrayList<>();
    leader(out, all, "PASSING", b -> b.passing() == null ? Integer.MIN_VALUE : b.passing().yards());
    leader(out, all, "RUSHING", b -> b.rushing() == null ? Integer.MIN_VALUE : b.rushing().yards());
    leader(out, all, "RECEIVING", b -> b.receiving() == null ? Integer.MIN_VALUE : b.receiving().yards());
    leader(out, all, "TACKLES", b -> b.defense() == null ? Integer.MIN_VALUE : b.defense().soloTackles());
    leader(out, all, "SACKS", b -> b.defense() == null || b.defense().sacks() == 0 ? Integer.MIN_VALUE : b.defense().sacks());
    return out;
  }

  private static void leader(List<MatchDtos.LeaderDto> out, List<PlayerBoxScore> all, String category, ToIntFunction<PlayerBoxScore> value) {
    all.stream().filter(b -> value.applyAsInt(b) != Integer.MIN_VALUE)
        .max(Comparator.comparingInt(value).thenComparing(Comparator.comparingInt(PlayerBoxScore::jerseyNumber).reversed()))
        .ifPresent(b -> out.add(new MatchDtos.LeaderDto(category, b.clubId(), b.playerId(), b.name(), b.position().name(), value.applyAsInt(b))));
  }
}
