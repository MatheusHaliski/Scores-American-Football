package br.com.velion.scoresaf.application.usecase.rf21;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Week;
import br.com.velion.scoresaf.application.service.PlayoffService;
import br.com.velion.scoresaf.application.service.StandingsService;
import br.com.velion.scoresaf.domain.shared.WeekKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF21 (leitura) — calendário gerado da temporada da carreira: as franquias da liga uma vez só e, por semana, a quinta de início, os
 * jogos (horário, mandante, visitante, divisão/conferência, placar quando houver), as folgas e se o usuário folga. {@code week}
 * opcional filtra uma semana. Bloco 6c: as semanas de playoff (com os placares) entram quando a rodada é criada, a folga da semana de
 * playoff é só a da semente que folga, e {@code playoffWeeks} lista as rodadas planejadas (D-105). A geração com IA e a simulação pelo
 * calendário são do RF21/RF36 (blocos seguintes).
 */
@Service
public class GetCalendarUseCase {
  private final CareerSupport support; private final WeekRepository weeks; private final FixtureRepository fixtures; private final ClubRepository clubs;
  private final PlayoffService playoffs; private final StandingsService standings;

  public GetCalendarUseCase(CareerSupport support, WeekRepository weeks, FixtureRepository fixtures, ClubRepository clubs, PlayoffService playoffs,
                            StandingsService standings) {
    this.support = support; this.weeks = weeks; this.fixtures = fixtures; this.clubs = clubs; this.playoffs = playoffs; this.standings = standings;
  }

  @Transactional(readOnly = true)
  public CareerDtos.CalendarDto execute(UUID careerId, UUID userId, Integer onlyWeek) {
    CareerSave save = support.owned(careerId, userId);
    Season season = save.getSeason();
    if (season == null) throw new BusinessRuleException("NO_SEASON", "Carreira sem temporada");
    List<Week> seasonWeeks = weeks.findBySeasonIdOrderByNumberAsc(season.getId());
    int last = CareerAssembler.lastWeek(seasonWeeks, season);
    if (onlyWeek != null && (onlyWeek < 1 || onlyWeek > last)) {
      throw new InvalidInputException("INVALID_WEEK", "Semana inválida: " + onlyWeek, onlyWeek, last);
    }
    UUID userClub = save.getClub() == null ? null : save.getClub().getId();
    List<Club> leagueClubs = new ArrayList<>(clubs.findByLeagueIdOrderByNameAsc(season.getLeague().getId()));
    leagueClubs.sort(Comparator.comparing((Club c) -> c.getConference() == null ? Integer.MAX_VALUE : c.getConference().getSortOrder())
        .thenComparing(c -> c.getDivision() == null ? Integer.MAX_VALUE : c.getDivision().getSortOrder()).thenComparing(Club::getName));
    List<Fixture> all = fixtures.findBySeasonId(season.getId()).stream().sorted(CareerAssembler.FIXTURE_ORDER).toList();
    List<CareerDtos.WeekDto> out = new ArrayList<>();
    for (Week w : seasonWeeks) {
      if (onlyWeek != null && w.getNumber() != onlyWeek) continue;
      List<Fixture> games = all.stream().filter(f -> f.getWeekNumber() == w.getNumber()).toList();
      Set<UUID> playing = new HashSet<>();
      games.forEach(f -> { playing.add(f.getHomeClub().getId()); playing.add(f.getAwayClub().getId()); });
      // semana de playoff: folga é só a da semente que folga na rodada (D-105)
      Set<UUID> playoffByes = playoffs.byes(season, w.getNumber());
      List<UUID> byes = leagueClubs.stream().map(Club::getId).filter(id -> !playing.contains(id) && (playoffByes == null || playoffByes.contains(id))).toList();
      out.add(new CareerDtos.WeekDto(w.getNumber(), w.getKind().name(), w.getStartsOn(), w.getStatus().name(), userClub != null && byes.contains(userClub),
          byes, games.stream().map(f -> CareerAssembler.fixture(f, userClub)).toList()));
    }
    return new CareerDtos.CalendarDto(season.getId(), season.getYear(), season.getPhase().name(), season.getCurrentWeek(), season.getRegularSeasonWeeks(),
        season.getGamesPerTeam(), userClub, leagueClubs.stream().map(CareerAssembler::club).toList(), out, playoffWeeks(save, season, seasonWeeks));
  }

  /** Rodadas de playoff planejadas pelas regras da liga, com a quinta-feira prevista (a semana seguinte à anterior). */
  private List<CareerDtos.PlayoffWeekDto> playoffWeeks(CareerSave save, Season season, List<Week> seasonWeeks) {
    List<WeekKind> kinds = PlayoffService.plannedRounds(standings.league(season), CareerSupport.rules(save));
    LocalDate lastRegular = seasonWeeks.stream().filter(w -> w.getNumber() <= season.getRegularSeasonWeeks()).map(Week::getStartsOn)
        .max(Comparator.naturalOrder()).orElse(null);
    List<CareerDtos.PlayoffWeekDto> out = new ArrayList<>();
    for (int i = 0; i < kinds.size(); i++) {
      int number = season.getRegularSeasonWeeks() + i + 1;
      Week w = seasonWeeks.stream().filter(x -> x.getNumber() == number).findFirst().orElse(null);
      LocalDate starts = w != null ? w.getStartsOn() : lastRegular == null ? null : lastRegular.plusWeeks(i + 1L);
      out.add(new CareerDtos.PlayoffWeekDto(number, kinds.get(i).name(), starts, w == null ? "PENDING" : w.getStatus().name()));
    }
    return out;
  }
}
