package br.com.velion.scoresaf.domain.catalog;

import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.shared.CoachStyle;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.Formation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Head coach, humano ou IA (RF8/RF9/RF15/RF18, RF41 moral). Formação e pacote preferidos alimentam o plano de jogo da IA (bloco 6). */
@Entity
@Table(name = "manager_profile", indexes = {
    @Index(name = "ix_manager_profile_world", columnList = "world_id"),
    @Index(name = "ix_manager_profile_user", columnList = "user_account_id")})
@Getter
@Setter
@NoArgsConstructor
public class ManagerProfile extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "world_id", nullable = false)
  private World world;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 3)
  private String nationality;

  @Column(nullable = false)
  private boolean human;

  /** Nulo para IA. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_account_id")
  private UserAccount userAccount;

  @Column(nullable = false) private int overall = 50;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private CoachStyle coachStyle = CoachStyle.OFFENSIVE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private Formation preferredFormation = Formation.SHOTGUN;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private DefensePackage preferredDefense = DefensePackage.BASE_4_3;

  @Column(nullable = false) private int reputationLeague = 50;
  @Column(nullable = false) private int careerTitles;
  @Column(nullable = false) private int careerGames;
  @Column(nullable = false) private int careerWins;
  @Column(nullable = false) private int careerPlayoffWins;

  @Column(length = 512) private String avatarUrl;
}
