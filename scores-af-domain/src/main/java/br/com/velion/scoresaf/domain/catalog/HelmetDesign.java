package br.com.velion.scoresaf.domain.catalog;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Capacete do clube (um por clube): casca, faixa central, grade e decalque. Editor e 3D chegam no bloco 8 (RF28). */
@Entity
@Table(name = "helmet_design")
@Getter
@Setter
@NoArgsConstructor
public class HelmetDesign extends AuditableEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "club_id", nullable = false, unique = true)
  private Club club;

  @Column(nullable = false, length = 7) private String shellColor = "#000000";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private HelmetStripe stripe = HelmetStripe.SINGLE;

  @Column(nullable = false, length = 7) private String stripeColor = "#FFFFFF";
  @Column(nullable = false, length = 7) private String facemaskColor = "#808080";
  @Column(length = 120) private String decalKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private HelmetFinish finish = HelmetFinish.GLOSS;
}
