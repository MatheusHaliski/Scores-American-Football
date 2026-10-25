package br.com.velion.scoresaf.application.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs do editor de mundos (RF5), lidos pelo front (amostras douradas {@code world-editor-club.json} e
 * {@code world-editor-player.json}). Dinheiro em centavos de S$; enums como texto.
 */
public final class EditorDtos {
  private EditorDtos() {}

  public record WorldDto(UUID id, String name, String description, String kind, boolean defaultTemplate, boolean editable, UUID sourceWorldId,
                         int baseYear, long seed, int leagues, int clubs, int players) {}

  public record LeagueDto(UUID id, UUID worldId, String name, String shortName, String country, int tier, String logoUrl, String ruleset,
                          int teamCount, int conferenceCount, int divisionsPerConference, long salaryCap, long salaryFloor, int prestige,
                          long tvRightsPerSeason, boolean structureValid, RulesDto rules) {}

  /** Estrutura da liga (PUT .../structure): conferências → divisões → franquias. No comando, ids de conferência/divisão são opcionais. */
  public record StructureDto(UUID leagueId, List<ConferenceDto> conferences) {}

  public record ConferenceDto(UUID id, String name, String shortName, List<DivisionDto> divisions) {}

  public record DivisionDto(UUID id, String name, List<UUID> clubIds) {}

  public record ClubDto(UUID id, UUID worldId, UUID leagueId, UUID conferenceId, UUID divisionId, String name, String shortName, String city,
                        int foundedYear, String logoKey, String primaryColor, String secondaryColor, String accentColor, int prestige,
                        int reputationBoard, int reputationFans, long cash, long scoutingBudget, int scoutingLevel, Integer expectedWins,
                        UUID stadiumId, String stadiumName, String managerName, int rosterCount, int practiceSquadCount, int rosterMax,
                        int practiceSquadMax, List<UniformDto> uniforms, HelmetDto helmet) {}

  public record UniformDto(UUID id, String slot, String jerseyColor, String pantsColor, String numberColor, String numberOutlineColor,
                           String pattern, String textureKey, boolean active, int revision) {}

  public record HelmetDto(String shellColor, String stripe, String stripeColor, String facemaskColor, String decalKey, String finish) {}

  public record StadiumDto(UUID id, String name, int capacity, String roof, String surface, String climateZone, int level, int atmosphere,
                           long value, long maintenanceCostPerWeek, String model3dUrl, String endZoneArtKey, int memberDiscountPct,
                           boolean dynamicPricing, int seasonTicketPct, List<SectorDto> sectors) {}

  public record SectorDto(UUID id, String type, Integer seats, Long ticketPrice, Integer condition, Long upgradeCost) {}

  /** Linha da lista de jogadores do clube. */
  public record PlayerSummaryDto(UUID id, String name, String position, String unit, String rosterStatus, int jerseyNumber, int age,
                                 int heightCm, int weightKg, int overall, String tier) {}

  /** Jogador no editor: dados, subatributos agrupados, avaliação na posição natural (OVR v1), overall em cada posição e contrato. */
  public record PlayerEditorDto(UUID id, UUID worldId, UUID clubId, String name, String nationality, String college, String birthDate, int age,
                                int heightCm, int weightKg, String position, String secondaryPosition, String unit, String rosterStatus,
                                int jerseyNumber, int experienceYears, int potential, boolean legendary, Map<String, Map<String, Integer>> attributes,
                                RatingDto rating, Map<String, Integer> overallByPosition, ContractDto contract) {}

  /** Avaliação numa posição: overall, nível do card, macros da família (ordem do card) e aviso de fora de posição. */
  public record RatingDto(String version, String position, String family, int overall, String tier, boolean outOfPosition,
                          Map<String, Integer> macros) {}

  public record ContractDto(int startSeason, int endSeason, int years, List<Long> baseSalaryByYear, long signingBonus, long guaranteed,
                            List<Long> capHitByYear, List<Long> deadMoneyIfCutByYear, String type, boolean franchiseTag, long totalValue) {}
}
