package br.com.velion.scoresaf.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs do elenco (RF4, RF56 leitura), lidos pelo front pelas amostras {@code roster.json} e {@code roster-player.json}. Dinheiro em
 * centavos de S$.
 */
public final class RosterDtos {
  private RosterDtos() {}

  public record RosterRulesDto(int rosterMax, int gameDayActives, int practiceSquadMax, int irMinWeeks, int irReturnsPerSeason) {}

  public record CountsDto(int roster, int active, int inactive, int practiceSquad, int injuredReserve, int irReturnsUsed) {}

  /**
   * Card compacto: posição, overall na posição natural (OVR v1), nível, macros da família (ordem do card), situação, contrato da
   * temporada e as vagas do depth chart ({@code "QB1"}, {@code "KR2"}).
   */
  public record PlayerCardDto(UUID id, String name, int jerseyNumber, String position, String secondaryPosition, String unit, int age, int heightCm,
                              int weightKg, int overall, String tier, String family, Map<String, Integer> macros, String rosterStatus, int condition,
                              int morale, int experienceYears, int injuryWeeksOut, int suspensionWeeks, long capHit, Integer contractEndSeason,
                              String contractType, List<String> depthChart) {}

  /** Elenco da franquia: 53 por unidade (OFFENSE, DEFENSE, SPECIAL_TEAMS; ativos e inativos), practice squad e IR. */
  public record RosterDto(UUID clubId, String clubName, int seasonYear, int currentWeek, RosterRulesDto rules, CountsDto counts, CareerDtos.CapDto cap,
                          Map<String, List<PlayerCardDto>> units, List<PlayerCardDto> practiceSquad, List<PlayerCardDto> injuredReserve) {}

  public record RosterMoveDto(UUID id, int week, String kind, String fromStatus, String toStatus, long deadMoney, Instant at) {}

  /** Jogador da carreira: card, subatributos agrupados, overall em cada posição, contrato, dead money se dispensado agora e histórico. */
  public record PlayerDetailDto(PlayerCardDto card, UUID clubId, String clubName, boolean ownPlayer, String nationality, String college, String birthDate,
                                int potential, Map<String, Map<String, Integer>> attributes, Map<String, Integer> overallByPosition,
                                EditorDtos.ContractDto contract, long deadMoneyIfReleased, Integer irPlacedWeek, Integer irEarliestReturnWeek,
                                List<RosterMoveDto> history) {}

  /** Resultado de um movimento (53 ↔ practice squad ↔ IR). */
  public record RosterChangeDto(PlayerCardDto player, CountsDto counts, CareerDtos.CapDto cap) {}

  /** Resultado da dispensa: o jogador vai a waivers (WAIVED); dead money lançado na temporada. */
  public record ReleaseResultDto(UUID playerId, String name, String rosterStatus, long deadMoney, long capHitRemoved, CountsDto counts, CareerDtos.CapDto cap) {}
}
