package br.com.velion.scoresaf.domain.catalog;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.engine.ratings.OverallCalculator;
import br.com.velion.scoresaf.domain.engine.ratings.PositionRating;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.Period;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Jogador (RF4 elenco, RF5 editor, RF22 card, bloco 5 motor). Posição do futebol americano com unidade derivada, altura e peso,
 * college, situação no elenco ({@link RosterStatus}), subatributos e contrato embutidos. {@code overall} (na posição natural, OVR v1)
 * e {@code age} são caches recalculados por {@link #recalculate(LocalDate)}. Dinheiro em centavos de S$.
 */
@Entity
@Table(name = "player", indexes = {
    @Index(name = "ix_player_world", columnList = "world_id"),
    @Index(name = "ix_player_club", columnList = "club_id"),
    @Index(name = "ix_player_club_status", columnList = "club_id, roster_status"),
    @Index(name = "ix_player_overall", columnList = "overall")})
@Getter
@Setter
@NoArgsConstructor
public class Player extends AuditableEntity {

  @Version
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "world_id", nullable = false)
  private World world;

  /** Nulo = agente livre ou dispensado. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "club_id")
  private Club club;

  @Column(nullable = false, length = 120)
  private String name;

  /** ISO-3 (fictício ou real, sem vínculo com atleta real, Q-4). */
  @Column(nullable = false, length = 3)
  private String nationality;

  @Column(length = 120)
  private String college;

  @Column(nullable = false)
  private LocalDate birthDate;

  @Column(nullable = false)
  private int age;

  @Column(nullable = false) private int heightCm;
  @Column(nullable = false) private int weightKg;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private Position position;

  @Enumerated(EnumType.STRING)
  @Column(columnDefinition = "VARCHAR(32)")
  private Position secondaryPosition;

  @Embedded
  private PlayerAttributes attributes = new PlayerAttributes();

  @Column(nullable = false) private int overall;
  @Column(nullable = false) private int potential = 50;
  @Column(nullable = false) private int experienceYears;

  @Enumerated(EnumType.STRING)
  @Column(name = "roster_status", nullable = false, columnDefinition = "VARCHAR(32)")
  private RosterStatus rosterStatus = RosterStatus.ACTIVE;

  @Column(name = "physical_condition", nullable = false) private int condition = 100;
  @Column(nullable = false) private int morale = 60;
  @Column(nullable = false) private int suspensionWeeks;

  @Embedded
  private PlayerContract contract;

  @Column(nullable = false) private int jerseyNumber;
  @Column(nullable = false) private boolean legendary;

  @Column(length = 512) private String photoUrl;
  @Column(length = 512) private String model3dUrl;

  /** Unidade da posição natural. */
  public Unit getUnit() { return position == null ? null : position.unit(); }

  /** Recalcula idade e overall (posição natural) para a data de referência; o potencial nunca fica abaixo do overall. */
  public void recalculate(LocalDate referenceDate) {
    if (attributes == null) attributes = new PlayerAttributes();
    if (birthDate != null && referenceDate != null) age = Math.max(0, Period.between(birthDate, referenceDate).getYears());
    overall = OverallCalculator.overall(attributes, position);
    if (potential < overall) potential = overall;
  }

  /** Avaliação numa posição qualquer (o card avisa quando é fora de posição). */
  public PositionRating ratingAt(Position playing) { return OverallCalculator.rate(attributes, position, secondaryPosition, playing); }

  public boolean isFreeAgent() { return club == null; }
}
