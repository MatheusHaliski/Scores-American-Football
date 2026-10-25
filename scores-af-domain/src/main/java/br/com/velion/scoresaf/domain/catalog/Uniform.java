package br.com.velion.scoresaf.domain.catalog;

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

/**
 * Uniforme do clube, um por slot (casa, fora, alternativo): camisa, calça, número com contorno e padrão. A validação de contraste e
 * o editor de identidade chegam no bloco 8 (RF28); {@code revision} sobe quando algo visual muda (a partida grava a que usou).
 */
@Entity
@Table(name = "uniform", indexes = @Index(name = "ix_uniform_club", columnList = "club_id"),
    uniqueConstraints = @UniqueConstraint(name = "ux_uniform_club_slot", columnNames = {"club_id", "slot"}))
@Getter
@Setter
@NoArgsConstructor
public class Uniform extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "club_id", nullable = false)
  private Club club;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private UniformSlot slot = UniformSlot.HOME;

  @Column(nullable = false, length = 7) private String jerseyColor = "#000000";
  @Column(nullable = false, length = 7) private String pantsColor = "#FFFFFF";
  @Column(nullable = false, length = 7) private String numberColor = "#FFFFFF";
  @Column(nullable = false, length = 7) private String numberOutlineColor = "#000000";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private UniformPattern pattern = UniformPattern.SOLID;

  @Column(length = 120) private String textureKey;
  @Column(nullable = false) private boolean active = true;
  @Column(nullable = false) private int revision = 1;
}
