package br.com.velion.scoresaf.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs do analista do sistema (por regras, sem IA externa, D-104), lidos pelo front pela amostra dourada {@code analysis.json}. Os
 * códigos (unidade, confronto, campo do plano, motivo) ficam em inglês, como os do motor; o resumo vem no idioma do pedido.
 */
public final class AnalysisDtos {
  private AnalysisDtos() {}

  /** Unidade: média dos titulares, média da liga e a diferença. */
  public record UnitDto(String unit, int rating, int leagueAverage, int diff) {}

  public record MatchupDto(String key, int ours, int theirs, int edge, String advantage) {}

  /** Próximo jogo: adversário, mando, força das duas equipes, chance de vitória, veredito (FAVORITE, EVEN, UNDERDOG), maior ameaça e confrontos. */
  public record OutlookDto(UUID fixtureId, int week, CareerDtos.ClubRefDto opponent, boolean home, boolean neutralSite, int strength, int opponentStrength,
                           double winProbability, String verdict, String biggestThreat, List<MatchupDto> matchups) {}

  /** Troca recomendada no depth chart: quem entra e quem sai da titularidade da vaga, com o overall de cada um na vaga. */
  public record DepthChangeDto(String slot, UUID inPlayerId, String inName, int inOverall, UUID outPlayerId, String outName, int outOverall, int gain) {}

  /** Ajuste do plano de jogo: campo, valor salvo, valor recomendado e o motivo. */
  public record PlanAdviceDto(String field, String current, String recommended, String reason) {}

  /**
   * Relatório: quando e em que semana foi gerado, origem (RULES), resumo (código, texto no idioma do pedido e parâmetros), força da
   * equipe e da liga, as unidades, os pontos fortes e fracos, os próximos jogos e as recomendações de depth chart e de plano de jogo.
   */
  public record AnalysisDto(UUID id, UUID seasonId, int week, String source, Instant generatedAt, String summaryCode, String summary,
                            Map<String, Object> summaryParams, int strength, int leagueStrength, List<UnitDto> units, List<UnitDto> strengths,
                            List<UnitDto> weaknesses, List<OutlookDto> upcoming, List<DepthChangeDto> depthChart, List<PlanAdviceDto> gamePlan) {}

  /** Resumo para a central (RF40). */
  public record AnalysisSummaryDto(UUID id, int week, Instant generatedAt, String summaryCode, String summary, Double nextWinProbability) {}
}
