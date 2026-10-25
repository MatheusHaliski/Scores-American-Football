package br.com.velion.scoresaf.domain.catalog;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Franquia (RF5 editor, RF12 estádio, RF14 finanças, RF40 central): liga, conferência, divisão, cores e dinheiro (centavos de S$).
 * Conferência e divisão ficam nulas só enquanto a liga não tem estrutura (editor). {@code stadium}: o clube é o lado dono
 * (FK {@code club.stadium_id}, única), como no basquete, para o estádio ser um proxy LAZY de verdade.
 */
@Entity
@Table(name = "club", indexes = {
    @Index(name = "ix_club_world", columnList = "world_id"),
    @Index(name = "ix_club_league", columnList = "league_id"),
    @Index(name = "ix_club_division", columnList = "division_id"),
    @Index(name = "ix_club_manager", columnList = "manager_id"),
    @Index(name = "ux_club_stadium", columnList = "stadium_id", unique = true)})
@Getter
@Setter
@NoArgsConstructor
public class Club extends AuditableEntity {

  @Version
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "world_id", nullable = false)
  private World world;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "league_id")
  private League league;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conference_id")
  private Conference conference;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "division_id")
  private Division division;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 4)
  private String shortName;

  @Column(nullable = false, length = 80)
  private String city;

  @Column(nullable = false)
  private int foundedYear;

  @Column(length = 120)
  private String logoKey;

  @Column(nullable = false, length = 7) private String primaryColor = "#000000";
  @Column(nullable = false, length = 7) private String secondaryColor = "#FFFFFF";
  @Column(nullable = false, length = 7) private String accentColor = "#808080";

  @Column(nullable = false) private int prestige = 50;
  @Column(nullable = false) private int reputationBoard = 50;
  @Column(nullable = false) private int reputationFans = 50;

  /** Caixa (centavos de S$). */
  @Column(nullable = false) private long cash;
  /** Verba de olheiros por temporada (centavos de S$). */
  @Column(nullable = false) private long scoutingBudget;
  @Column(nullable = false) private int scoutingLevel = 1;

  /** Vitórias esperadas na temporada (ranking de força do elenco; recalculado no início da temporada, RF41). */
  private Integer expectedWins;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stadium_id", unique = true)
  private Stadium stadium;

  /** Head coach atual (IA ou humano); nulo = vago. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "manager_id")
  private ManagerProfile manager;

  @Column(name = "user_controlled", nullable = false)
  private boolean userControlled;
}
