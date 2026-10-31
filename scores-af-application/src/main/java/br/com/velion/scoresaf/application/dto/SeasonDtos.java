package br.com.velion.scoresaf.application.dto;

import br.com.velion.scoresaf.application.dto.CareerDtos.ClubRefDto;
import br.com.velion.scoresaf.application.dto.CareerDtos.FixtureDto;
import br.com.velion.scoresaf.application.dto.CareerDtos.RecordDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs da temporada (bloco 6c): classificação (RF16), playoffs e chave (RF66), notícias da semana (RF21), resumo da temporada, avanço
 * e sala de troféus (RF35). Lidos pelo front pelas amostras douradas {@code standings.json}, {@code playoffs.json},
 * {@code playoffs-bracket.json}, {@code week-news.json}, {@code season-summary.json} e {@code trophies.json}. Enums como texto; datas
 * em ISO-8601; percentuais com 3 casas.
 */
public final class SeasonDtos {
  private SeasonDtos() {}

  // ---------------------------------------------------------------- RF16 — classificação

  /**
   * Linha da classificação. {@code seed}: semente atual (projeção durante a temporada regular, oficial depois); {@code clinch}: o que
   * a franquia já garantiu (BYE, DIVISION, PLAYOFFS) ou nulo; {@code eliminated}: sem chance de vaga; {@code tiebreaker}: critério que
   * a pôs à frente das empatadas logo abaixo (nulo sem empate).
   */
  public record StandingRowDto(int rank, ClubRefDto club, RecordDto record, double winPct, int pointsFor, int pointsAgainst, int pointDiff, String streak,
                               List<String> lastFive, RecordDto home, RecordDto away, RecordDto division, RecordDto conference, Integer seed, String clinch,
                               boolean eliminated, String tiebreaker, boolean userClub) {}

  public record DivisionTableDto(UUID id, String name, List<StandingRowDto> rows) {}

  /** Conferência: as divisões e a tabela da conferência inteira (sementes primeiro, depois a disputa dos wild cards). */
  public record ConferenceTableDto(UUID id, String name, String shortName, List<DivisionTableDto> divisions, List<StandingRowDto> rows) {}

  /**
   * Classificação (RF16): até a semana {@code throughWeek} da temporada regular; {@code official} quando a temporada regular acabou
   * (as sementes são as dos playoffs). {@code tiebreakers}: a cadeia de desempate entre franquias da mesma divisão e a de divisões
   * diferentes (wild card), na ordem em que são aplicadas.
   */
  public record StandingsDto(UUID seasonId, int seasonYear, String phase, int throughWeek, int regularSeasonWeeks, boolean official,
                             int seedsPerConference, int byeSeeds, UUID userClubId, List<String> divisionTiebreakers, List<String> wildCardTiebreakers,
                             List<ConferenceTableDto> conferences) {}

  // ---------------------------------------------------------------- RF66 — playoffs

  /** Semente: franquia, campanha, se é campeã de divisão, se folga na primeira rodada e a situação (PROJECTED, ALIVE, ELIMINATED, CHAMPION). */
  public record SeedDto(int seed, ClubRefDto club, RecordDto record, boolean divisionChampion, boolean bye, String status, String tiebreaker) {}

  public record ConferenceSeedsDto(UUID id, String name, String shortName, List<SeedDto> seeds) {}

  /**
   * O usuário nos playoffs: semente e situação (PROJECTED_IN, PROJECTED_OUT antes; BYE, ALIVE, ELIMINATED, MISSED durante; CHAMPION,
   * RUNNER_UP, ELIMINATED, MISSED depois) e o próximo jogo de playoff dele, quando houver.
   */
  public record UserPlayoffDto(UUID clubId, Integer seed, String status, String round, FixtureDto nextGame) {}

  /**
   * Situação dos playoffs (RF66): NOT_STARTED (sementes projetadas pela classificação), IN_PROGRESS ou FINISHED; a rodada atual, as
   * regras da chave e as rodadas planejadas.
   */
  public record PlayoffsDto(UUID seasonId, String status, boolean projection, String currentRound, int seedsPerConference, int byeSeeds,
                            boolean neutralSiteFinal, List<String> rounds, List<ConferenceSeedsDto> conferences, UserPlayoffDto user,
                            ClubRefDto champion, ClubRefDto runnerUp) {}

  public record BracketTeamDto(ClubRefDto club, int seed) {}

  /** Jogo da chave: o do calendário (placar, prorrogação, vencedor) ou projetado (sem {@code fixtureId}, status PROJECTED). */
  public record BracketGameDto(UUID fixtureId, String conference, BracketTeamDto home, BracketTeamDto away, Integer homeScore, Integer awayScore,
                               boolean overtime, UUID winnerClubId, boolean neutralSite, String status, LocalDate date, String kickoffSlot, boolean userGame) {}

  /** Rodada: índice, tipo, semana do calendário (nula se ainda não criada), situação (PROJECTED, PENDING, CURRENT, COMPLETED), jogos e folgas. */
  public record BracketRoundDto(int index, String kind, Integer week, String status, List<BracketGameDto> games, List<BracketTeamDto> byes) {}

  public record BracketDto(UUID seasonId, String status, UUID userClubId, List<BracketRoundDto> rounds, ClubRefDto champion) {}

  // ---------------------------------------------------------------- RF21 — notícias

  /**
   * Notícia gerada por regras (sem IA externa, D-103): tipo, tom (POSITIVE, NEGATIVE, NEUTRAL, DRAMATIC), manchete e texto no idioma
   * do pedido, a chave do texto e os parâmetros (o front pode montar o texto no idioma dele), e as referências.
   */
  public record NewsDto(UUID id, int week, String kind, String tone, String headline, String body, String textKey, Map<String, Object> params,
                        UUID clubId, UUID playerId, UUID fixtureId) {}

  public record WeekNewsDto(UUID seasonId, int week, String weekKind, boolean completed, List<NewsDto> items) {}

  // ---------------------------------------------------------------- RF35 — fim de temporada

  public record AwardDto(String type, UUID playerId, String name, String position, ClubRefDto club, double score, Map<String, Integer> statLine) {}

  public record LeaderDto(UUID playerId, String name, String position, ClubRefDto club, int value, int games) {}

  /** Campanha do usuário: temporada regular, posições, semente e o resultado nos playoffs (CHAMPION, RUNNER_UP, CONFERENCE, DIVISIONAL, WILD_CARD, PRELIMINARY, MISSED, IN_PROGRESS). */
  public record CampaignDto(RecordDto record, int pointsFor, int pointsAgainst, Integer divisionRank, Integer conferenceRank, Integer seed, String playoffResult) {}

  /** Contrato que termina na temporada (pendência de renovação do bloco 7). */
  public record ExpiringContractDto(UUID playerId, String name, String position, int age, int overall, int endSeason, long capHit) {}

  /** Passo da entressafra (D-10): fase, situação (PENDING, DONE, SKIPPED) e quem cuida (SEASON_ADVANCE ou BLOCK_7). */
  public record OffseasonStepDto(String phase, String status, String handledBy) {}

  public record OffseasonDto(boolean canAdvance, List<OffseasonStepDto> steps, List<ExpiringContractDto> expiringContracts) {}

  /**
   * Resumo da temporada (RF35): campeã e vice, campanha do usuário, prêmios (provisórios até a final: calculados das estatísticas até
   * aqui) e líderes da temporada regular, e a entressafra.
   */
  public record SeasonSummaryDto(UUID seasonId, int number, int year, String phase, boolean completed, ClubRefDto champion, ClubRefDto runnerUp,
                                 UUID userClubId, CampaignDto campaign, boolean provisionalAwards, List<AwardDto> awards, Map<String, List<LeaderDto>> leaders,
                                 OffseasonDto offseason) {}

  /**
   * Resultado do avanço da temporada (RF35, D-99): a temporada que fechou, a nova (número, ano, fase, semana), os passos da entressafra,
   * quantos contratos terminaram sem renovação (os jogadores viraram agentes livres, D-100), quantos as franquias da IA renovaram sozinhas
   * ({@code aiRenewals}, D-120), os contratos encerrados da franquia do usuário (pendências do bloco 7), quantos jogadores dela evoluíram
   * e regrediram, o dead money que a temporada anterior jogou para a nova e a carreira atualizada.
   */
  public record SeasonAdvanceDto(UUID previousSeasonId, UUID seasonId, int seasonNumber, int seasonYear, String phase, int currentWeek,
                                 List<OffseasonStepDto> steps, int expiredContracts, int aiRenewals, List<ExpiringContractDto> userExpiredContracts,
                                 int improved, int declined, long deadMoneyCarried, CareerDtos.CareerDto career) {}

  // ---------------------------------------------------------------- RF35/RF17 — troféus

  /** Título (CHAMPIONSHIP, CONFERENCE, DIVISION) de uma temporada. */
  public record TrophyDto(UUID seasonId, int seasonNumber, int seasonYear, String kind, ClubRefDto club, String conference, String division,
                          boolean userClub) {}

  /** Temporada da carreira: campeã, vice, MVP e o resultado do usuário. */
  public record SeasonHistoryDto(UUID seasonId, int number, int year, boolean completed, ClubRefDto champion, ClubRefDto runnerUp, AwardDto mvp,
                                 RecordDto userRecord, String userResult) {}

  /** Sala de troféus da carreira: contagem do usuário, todos os títulos (com a marca do usuário) e as temporadas. */
  public record TrophiesDto(UUID userClubId, int championships, int conferenceTitles, int divisionTitles, int playoffAppearances,
                            List<TrophyDto> trophies, List<SeasonHistoryDto> seasons) {}
}
