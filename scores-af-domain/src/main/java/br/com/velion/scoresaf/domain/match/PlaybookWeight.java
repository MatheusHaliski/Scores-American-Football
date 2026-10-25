package br.com.velion.scoresaf.domain.match;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Um peso do playbook (RF61): {@code scope} é a situação ({@code Playbook.Situation}) com {@code item} = conceito de jogada, ou
 * {@link #COVERAGE} com {@code item} = cobertura. Peso de 0 a 200 (100 = padrão da liga, 0 = desativado).
 */
@Entity
@Table(name = "playbook_weight", uniqueConstraints = @UniqueConstraint(name = "ux_playbook_weight_item", columnNames = {"playbook_id", "scope", "item"}))
@Getter
@Setter
@NoArgsConstructor
public class PlaybookWeight extends AuditableEntity {
  public static final String COVERAGE = "COVERAGE";

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "playbook_id", nullable = false)
  private PlaybookSetup playbook;

  @Column(nullable = false, length = 32)
  private String scope;

  @Column(nullable = false, length = 32)
  private String item;

  @Column(nullable = false)
  private int weight;

  public static PlaybookWeight of(PlaybookSetup playbook, String scope, String item, int weight) {
    PlaybookWeight w = new PlaybookWeight();
    w.playbook = playbook; w.scope = scope; w.item = item; w.weight = weight;
    return w;
  }
}
