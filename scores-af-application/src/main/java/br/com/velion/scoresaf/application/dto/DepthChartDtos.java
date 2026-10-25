package br.com.velion.scoresaf.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs do depth chart (RF60), lidos pelo front pela amostra {@code depth-chart.json}. O plano de jogo está em {@link GamePlanDtos}. */
public final class DepthChartDtos {
  private DepthChartDtos() {}

  public record EntryDto(int depth, UUID playerId, String name, int jerseyNumber, String position, int overallAtSlot, int overall, String tier,
                         boolean outOfPosition, boolean available, String rosterStatus) {}

  /** Vaga: posição (nula nos papéis KR, PR, H e THIRD_DOWN_BACK), mínimo para o depth chart estar completo e os jogadores por profundidade. */
  public record SlotDto(String slot, String position, boolean role, int minStarters, List<EntryDto> players) {}

  public record UnitDto(String unit, List<SlotDto> slots) {}

  /** Problema: código ({@code DEPTH_CHART_*} nos erros, {@code OUT_OF_POSITION} nos avisos), vaga, profundidade e jogador. */
  public record IssueDto(String code, String slot, Integer depth, UUID playerId) {}

  /** {@code version}: devolva no PUT; diverge ⇒ 409 DEPTH_CHART_VERSION_CONFLICT. */
  public record DepthChartDto(UUID id, UUID clubId, long version, boolean valid, Instant updatedAt, int gameDayActives, int uniquePlayers,
                              List<UnitDto> units, List<IssueDto> errors, List<IssueDto> warnings) {}
}
