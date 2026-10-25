package br.com.velion.scoresaf.domain.match;

import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Uma linha do depth chart (RF60): vaga (posição ou papel), profundidade (1 = titular) e jogador. */
@Entity
@Table(name = "depth_chart_slot", indexes = @Index(name = "ix_depth_chart_slot_player", columnList = "player_id"),
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_depth_chart_slot_depth", columnNames = {"depth_chart_id", "slot", "depth"}),
        @UniqueConstraint(name = "ux_depth_chart_slot_player", columnNames = {"depth_chart_id", "slot", "player_id"})})
@Getter
@Setter
@NoArgsConstructor
public class DepthChartSlot extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "depth_chart_id", nullable = false)
  private DepthChart depthChart;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private ChartSlot slot;

  @Column(nullable = false)
  private int depth;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "player_id", nullable = false)
  private Player player;

  public static DepthChartSlot of(DepthChart chart, ChartSlot slot, int depth, Player player) {
    DepthChartSlot s = new DepthChartSlot();
    s.depthChart = chart; s.slot = slot; s.depth = depth; s.player = player;
    return s;
  }
}
