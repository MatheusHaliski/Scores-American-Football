package br.com.velion.scoresaf.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs do mercado (bloco 7a: RF49 teto e contratos, RF65 free agency e waivers), lidos pelo front pelas amostras {@code cap.json},
 * {@code free-agency.json}, {@code waivers.json} e {@code renew-result.json}. Dinheiro em centavos de S$.
 */
public final class MarketDtos {
  private MarketDtos() {}

  /** Proposta de contrato (pedido do jogador, contraproposta ou o que foi assinado). */
  public record OfferDto(int years, List<Long> salaryByYear, long signingBonus, long guaranteed, long totalValue, long apy) {}

  /**
   * O que dá para fazer com o contrato do jogador agora (e por que não): renovar (janela: último ano), reestruturar (2+ anos pela
   * frente e a temporada em andamento; até {@code restructureMax}), franchise tag (entressafra, antes da free agency, uma por
   * temporada; valor da tag) e trade block.
   */
  public record ContractActionsDto(boolean canRenew, String renewBlockedReason, boolean canRestructure, String restructureBlockedReason,
                                   long restructureMax, boolean canFranchiseTag, String franchiseTagBlockedReason, Long franchiseTagValue,
                                   boolean tradeBlock) {}

  /** Mercado do jogador: valor de mercado anual, o pedido (renovação ou agente livre) e as ações de contrato. */
  public record PlayerMarketDto(long marketValue, OfferDto asking, ContractActionsDto actions) {}

  /** Um ano do contrato na folha: salário-base, parcela das luvas, parte garantida, cap hit e dead money se cortado antes dele. */
  public record ContractYearDto(int season, long baseSalary, long prorationBonus, long guaranteed, long capHit, long deadMoneyIfCut) {}

  /** Linha da folha: um contrato da franquia, ano a ano, com as ações possíveis. */
  public record CapContractDto(UUID playerId, String name, String position, int jerseyNumber, String rosterStatus, int age, int overall,
                               String contractType, boolean franchiseTag, int startSeason, int endSeason, int yearsRemaining, long capHit,
                               long totalValue, long signingBonus, long guaranteed, List<ContractYearDto> years, ContractActionsDto actions) {}

  /** Dead money de uma dispensa: na temporada da dispensa e o que foi para a seguinte (regra do 1º de junho). */
  public record DeadMoneyDto(UUID playerId, String name, int seasonYear, long amount, long nextSeason) {}

  /** Projeção de uma temporada: comprometido (cap hits + dead money), dead money, espaço e quantos contratos. */
  public record CapYearDto(int seasonYear, long committed, long deadMoney, long capSpace, int contracts) {}

  /** Folha salarial ({@code GET /cap}): a temporada atual, as próximas 3 e os contratos. */
  public record CapSheetDto(UUID clubId, String clubName, int seasonYear, String phase, boolean hardCap, long salaryCap, long salaryFloor, long capUsed,
                            long capSpace, long deadMoney, long deadMoneyNextSeason, int rosterCount, int practiceSquadCount,
                            List<CapYearDto> projections, List<CapContractDto> contracts, List<DeadMoneyDto> deadMoneyEntries,
                            FranchiseTagDto franchiseTag) {}

  /** Franchise tag da temporada: se a janela está aberta, se já foi usada e com quem. */
  public record FranchiseTagDto(boolean windowOpen, boolean used, UUID playerId, String playerName, Long value) {}

  /** Resultado de renovação, contratação, reestruturação ou franchise tag. */
  public record ContractResultDto(String kind, UUID playerId, String name, String position, String rosterStatus, EditorDtos.ContractDto contract,
                                  long capHitThisSeason, OfferDto offer, CareerDtos.CapDto cap, RosterDtos.CountsDto counts) {}

  /** Janela da free agency: OPEN (dia {@code day} de {@code days}), CLOSED (fechada nesta temporada) ou NONE (temporada sem janela). */
  public record WindowDto(String status, int day, int days, String marketDate, int discountPct, boolean canAdvance) {}

  /** Agente livre na lista: card resumido, de onde veio, o pedido do dia e se aceitaria o practice squad. */
  public record FreeAgentDto(UUID id, String name, String position, String secondaryPosition, String unit, int age, int overall, String tier,
                             int experienceYears, int potential, String source, CareerDtos.ClubRefDto previousClub, boolean practiceSquadEligible,
                             boolean acceptsPracticeSquad, long marketValue, OfferDto asking, long askingCapHit) {}

  /** Contratação recente na free agency (usuário ou IA). */
  public record SigningDto(UUID playerId, String name, String position, int overall, CareerDtos.ClubRefDto club, String kind, String source,
                           Integer day, int years, long totalValue, Instant at) {}

  /** Free agency ({@code GET /free-agency}): janela, teto e elenco do usuário, a lista filtrada e as últimas contratações. */
  public record FreeAgencyDto(UUID seasonId, int seasonYear, String phase, WindowDto window, CareerDtos.CapDto cap, RosterDtos.CountsDto counts,
                              int rosterMax, int practiceSquadMax, int total, List<FreeAgentDto> players, List<SigningDto> recentSignings) {}

  /** Jogador em waivers: de onde veio, o contrato que quem reivindicar assume e a situação da reivindicação do usuário. */
  public record WaiverPlayerDto(UUID id, String name, String position, int age, int overall, String tier, String kind, CareerDtos.ClubRefDto fromClub,
                                long capHit, int yearsRemaining, boolean claimedByUser, boolean canClaim, String blockedReason) {}

  /** Waivers ({@code GET /waivers}): a prioridade do usuário na fila, quando o período fecha e os jogadores expostos. */
  public record WaiversDto(int priority, int clubs, String resolvesAt, CareerDtos.CapDto cap, RosterDtos.CountsDto counts, List<WaiverPlayerDto> players,
                           List<ClaimResultDto> recentClaims) {}

  /** Reivindicação resolvida recentemente: quem levou o jogador. */
  public record ClaimResultDto(UUID playerId, String name, String position, CareerDtos.ClubRefDto club, String source, Instant at) {}
}
