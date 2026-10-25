package br.com.velion.scoresaf.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs da carreira (RF11 slots, RF34 nova carreira, RF40 central, RF21 calendário), lidos pelo front pelas amostras douradas
 * {@code careers-list.json}, {@code career-dashboard.json} e {@code career-calendar.json}. Dinheiro em centavos de S$; enums como
 * texto; datas em ISO-8601.
 */
public final class CareerDtos {
  private CareerDtos() {}

  /** Campanha (vitórias, derrotas, empates) e o texto "V-D" ou "V-D-E" (empate só aparece quando houver). */
  public record RecordDto(int wins, int losses, int ties, String summary) {
    public static RecordDto of(int w, int l, int t) { return new RecordDto(w, l, t, t > 0 ? w + "-" + l + "-" + t : w + "-" + l); }
  }

  public record CareerDto(UUID id, String name, Integer slot, String status, String difficulty, long seed, int currentWeek, String employmentStatus,
                          UUID worldId, UUID sourceWorldId, String sourceWorldName, UUID leagueId, String leagueName, String ruleset,
                          UUID clubId, String clubName, String clubShortName, String clubCity, String clubPrimaryColor, String clubSecondaryColor,
                          UUID coachId, String coachName, String coachNationality, String coachStyle,
                          UUID seasonId, Integer seasonYear, String seasonPhase, int regularSeasonWeeks, RecordDto record,
                          Instant createdAt, Instant lastPlayedAt, long version) {}

  /** Central de carreiras salvas (RF11): slots ACTIVE (máx. 5), os livres e todas as carreiras (ACTIVE por slot, depois ARCHIVED). */
  public record CareerListDto(int maxActiveSlots, int activeCount, List<Integer> freeSlots, List<CareerDto> careers) {}

  public record ClubRefDto(UUID id, String name, String shortName, String city, String primaryColor, String secondaryColor, String conference,
                           String division) {}

  public record FixtureDto(UUID id, int week, LocalDate date, String kickoffSlot, String status, UUID homeClubId, UUID awayClubId, Integer homeScore,
                           Integer awayScore, boolean divisional, boolean conferenceGame, boolean userGame) {}

  public record WeekDto(int number, String kind, LocalDate startsOn, String status, boolean userBye, List<UUID> byeClubIds, List<FixtureDto> fixtures) {}

  /** Calendário da temporada (RF21): franquias uma vez só ({@code clubs}) e as semanas com os jogos, as folgas e a do usuário. */
  public record CalendarDto(UUID seasonId, int seasonYear, String phase, int currentWeek, int regularSeasonWeeks, int gamesPerTeam, UUID userClubId,
                            List<ClubRefDto> clubs, List<WeekDto> weeks) {}

  public record CoachDto(UUID id, String name, String nationality, String style, int overall, int reputationLeague, int careerGames, int careerWins,
                         int careerTitles) {}

  public record NextGameDto(FixtureDto fixture, boolean home, ClubRefDto opponent, RecordDto opponentRecord) {}

  public record StandingRowDto(int rank, ClubRefDto club, RecordDto record, double winPct, int pointsFor, int pointsAgainst, boolean userClub) {}

  public record PlayerHighlightDto(UUID id, String name, String position, int jerseyNumber, int overall, String tier) {}

  public record InjuryDto(UUID playerId, String name, String position, String rosterStatus, int weeksOut) {}

  /**
   * Teto salarial da temporada: teto, comprometido (cap hits + dead money), dead money da temporada, espaço e o dead money das
   * dispensas desta temporada que a regra do 1º de junho já lançou na temporada seguinte (Q-24, D-79).
   */
  public record CapDto(long salaryCap, long capUsed, long deadMoney, long capSpace, long deadMoneyNextSeason) {}

  public record DepthChartAlertDto(long version, boolean valid, int errors, int warnings, List<String> codes) {}

  /** Partida ao vivo em andamento da carreira (bloco 6b): para a central retomar o jogo ({@code GET /matches/{matchId}}). */
  public record LiveMatchRefDto(UUID matchId, UUID fixtureId, int week, String status, int homeScore, int awayScore) {}

  /**
   * Central da franquia (RF40). O que ainda não existe vem como {@code null} explícito (o front mostra "—"): moral (RF41), análise do
   * analista (6c), inbox (bloco 7) e Scores Points (bloco 10). {@code live} é a partida ao vivo em andamento ({@link LiveMatchRefDto},
   * bloco 6b) ou {@code null}.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record DashboardDto(CareerDto career, ClubRefDto club, CoachDto coach, int currentWeek, NextGameDto nextGame, boolean byeWeek, RecordDto record,
                             Integer divisionRank, List<StandingRowDto> division, List<FixtureDto> upcomingDivisionGames,
                             List<PlayerHighlightDto> rosterHighlights, List<InjuryDto> injuries, CapDto cap, DepthChartAlertDto depthChart,
                             Object live, Object morale, Object analysis, List<Object> inbox, Integer unreadInbox, Integer scoresPoints) {}
}
