package br.com.velion.scoresaf.application.dto;

import br.com.velion.scoresaf.domain.engine.model.DecisionRecord;
import br.com.velion.scoresaf.domain.engine.model.Drive;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore;
import br.com.velion.scoresaf.domain.engine.model.TeamBoxScore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs da preparação (RF7), do painel da semana e da partida ao vivo (RF27/RF29/RF62) e do relatório pós-jogo, lidos pelo front pelas
 * amostras {@code preparation.json}, {@code week.json}, {@code match-state.json}, {@code match-events.json} e
 * {@code match-report.json}. Lances, drives, box score e decisões vão no vocabulário do motor (os mesmos records do
 * {@link LiveMatchDto}, D-5): o estado ao vivo da API embrulha o próprio {@link LiveMatchDto} em {@code live}.
 */
public final class MatchDtos {
  private MatchDtos() {}

  // ---------------------------------------------------------------- RF7 preparação

  /** Jogador citado (confronto, jogador-chave): overall na posição em que joga. */
  public record PlayerRefDto(UUID id, String name, String position, int jerseyNumber, int overall, String tier) {}

  /** Média dos titulares da unidade ({@code OFFENSE}, {@code DEFENSE}, {@code SPECIAL_TEAMS}) dos dois lados. */
  public record UnitComparisonDto(String unit, int ours, int theirs, int edge) {}

  /**
   * Confronto-chave: {@code key} ({@code WR_VS_CB}, {@code OL_VS_DL}, {@code QB_VS_PASS_RUSH}), {@code side} ({@code OUR_OFFENSE} = nosso
   * ataque contra a defesa deles; {@code THEIR_OFFENSE} = o contrário), as médias, a vantagem ({@code edge} = nosso − deles) e quem leva
   * ({@code OURS}, {@code THEIRS}, {@code EVEN}).
   */
  public record MatchupDto(String key, String side, int ours, int theirs, int edge, String advantage, List<PlayerRefDto> ourPlayers,
                           List<PlayerRefDto> theirPlayers) {}

  /** Prontidão do depth chart para o jogo: erros bloqueiam o kickoff (422 DEPTH_CHART_INVALID), avisos não. */
  public record ReadinessDto(boolean ready, long depthChartVersion, List<DepthChartDtos.IssueDto> errors, List<DepthChartDtos.IssueDto> warnings) {}

  /** Clima previsto (o mesmo sorteio do jogo: estádio do mandante e semente da partida). */
  public record WeatherDto(String condition, String stadium, String roof, String surface) {}

  /**
   * Preparar time (RF7): o próximo jogo, o adversário, a comparação por unidade, os confrontos-chave, os jogadores-chave dos dois lados,
   * o clima, a prontidão do depth chart e o resumo do plano e do playbook. {@code matchId} quando a partida já foi iniciada.
   */
  public record PreparationDto(CareerDtos.FixtureDto fixture, boolean home, CareerDtos.ClubRefDto club, CareerDtos.ClubRefDto opponent,
                               CareerDtos.RecordDto record, CareerDtos.RecordDto opponentRecord, WeatherDto weather, List<UnitComparisonDto> units,
                               List<MatchupDto> keyMatchups, List<PlayerRefDto> keyPlayers, List<PlayerRefDto> opponentKeyPlayers,
                               ReadinessDto readiness, GamePlanDtos.GamePlanDto gamePlan, int playbookCustomWeights, String playCallingLevel,
                               UUID matchId) {}

  // ---------------------------------------------------------------- RF27 painel da semana

  /** Situação ao vivo de um jogo da semana (só a partida do usuário tem sessão; os outros são simulados quando a semana fecha). */
  public record LiveLineDto(UUID matchId, String status, int quarter, int clockSeconds, UUID possessionClubId, int down, int distance, int ballOnAbsYd,
                            int homeScore, int awayScore) {}

  public record WeekGameDto(CareerDtos.FixtureDto fixture, CareerDtos.ClubRefDto home, CareerDtos.ClubRefDto away, LiveLineDto live) {}

  /**
   * Painel da semana (RF27): todos os jogos por horário, as folgas, a partida do usuário em destaque ({@code userFixtureId},
   * {@code matchId}) e se a semana fechou ({@code completed}). {@code currentWeek} é a semana atual da temporada depois da operação.
   */
  public record WeekBoardDto(UUID seasonId, int week, int regularSeasonWeeks, int currentWeek, String kind, String status, LocalDate startsOn,
                             boolean completed, boolean userBye, UUID userFixtureId, UUID matchId, List<UUID> byeClubIds, List<WeekGameDto> games) {}

  // ---------------------------------------------------------------- RF27/RF29/RF62 partida ao vivo

  /**
   * Estado da partida ao vivo como a API entrega: a sessão (situação, franquia do usuário, nível de chamada congelado no kickoff, prazo
   * da decisão aberta, o que dá para fazer agora) e o estado do motor no mesmo {@link LiveMatchDto} da amostra {@code live-match.json}.
   *
   * @param status situação da sessão: NOT_STARTED, LIVE, BREAK, HALFTIME, AWAITING_DECISION, FINISHED
   * @param adjustable o playbook do intervalo aceita ajustes agora (RF29)
   * @param advanceModes modos de avanço aceitos agora (vazio com decisão aberta no prazo ou partida encerrada)
   * @param decisionDeadlineAt prazo da decisão aberta; vencido, a IA chama a jogada no próximo avanço ({@code CallSource.AUTO})
   */
  public record MatchStateDto(UUID matchId, UUID careerId, UUID fixtureId, int week, String status, UUID userClubId, boolean userHome,
                              String playCallingLevel, boolean adjustable, List<String> advanceModes, Instant decisionDeadlineAt,
                              int decisionTimeoutSeconds, LiveMatchDto live) {}

  /** Play-by-play com cursor: os lances com {@code seq > after}, até {@code limit}; {@code lastSeq} é o último da partida. */
  public record MatchEventsDto(UUID matchId, int after, int lastSeq, boolean finished, List<PlayEvent> events) {}

  // ---------------------------------------------------------------- relatório pós-jogo

  /** Lado do jogo no relatório: franquia, placar, pontos por quarto, box score da equipe e dos jogadores. */
  public record ReportSideDto(CareerDtos.ClubRefDto club, int score, List<Integer> quarters, TeamBoxScore stats, List<PlayerBoxScore> players) {}

  /** Líder da partida numa categoria ({@code PASSING}, {@code RUSHING}, {@code RECEIVING}, {@code TACKLES}, {@code SACKS}): jardas ou total. */
  public record LeaderDto(String category, UUID clubId, UUID playerId, String name, String position, int value) {}

  /**
   * Relatório pós-jogo (RF27/RF31) de qualquer jogo do calendário: reprodutível ({@code seed}, versões do motor e das regras), box
   * score, líderes, drives, jogadas de pontuação e o log de decisões (chamadas do manager, decisões automáticas, tempos técnicos e
   * ajustes do intervalo).
   */
  public record MatchReportDto(UUID fixtureId, UUID matchId, int week, LocalDate date, String kickoffSlot, boolean userGame, long seed, int engineVersion,
                               int rulesVersion, String weather, ReportSideDto home, ReportSideDto away, boolean overtime, UUID winnerClubId, int playCount,
                               List<LeaderDto> leaders, List<Drive> drives, List<PlayEvent> scoringPlays, List<DecisionRecord> decisions) {}

  /** O que o relatório guarda em {@code MatchReport.reportJson} (o motor como entregou). */
  public record ReportData(String weather, List<Integer> homeQuarters, List<Integer> awayQuarters, TeamBoxScore homeTeam, TeamBoxScore awayTeam,
                           List<PlayerBoxScore> homePlayers, List<PlayerBoxScore> awayPlayers, List<Drive> drives, List<PlayEvent> scoringPlays,
                           List<DecisionRecord> decisions) {}
}
