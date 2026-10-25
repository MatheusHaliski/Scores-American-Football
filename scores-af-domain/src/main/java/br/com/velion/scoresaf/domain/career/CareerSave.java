package br.com.velion.scoresaf.domain.career;

import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.Difficulty;
import br.com.velion.scoresaf.domain.shared.EmploymentStatus;
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
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Carreira salva (RF11 slots, RF34 nova carreira, RF40 central). RNF1: cada carreira tem o próprio {@link World} (kind SAVE),
 * clonado do mundo escolhido (padrão ou editado) na mesma transação que cria o save; editar o catálogo depois não a altera e jogar
 * não volta para o catálogo. {@code seed} é a semente-raiz ({@link SeedPolicy}): calendário e partidas derivam dela.
 * {@code slot} vale de 1 a 5 enquanto ACTIVE; ARCHIVED libera o slot (nulo).
 */
@Entity
@Table(name = "career_save",
    indexes = {@Index(name = "ix_career_save_user", columnList = "user_id"), @Index(name = "ix_career_save_world", columnList = "world_id")},
    uniqueConstraints = @UniqueConstraint(name = "ux_career_save_user_slot", columnNames = {"user_id", "slot"}))
@Getter
@Setter
@NoArgsConstructor
public class CareerSave extends AuditableEntity {
  /** Slots de carreira ACTIVE por usuário (RF11). */
  public static final int MAX_ACTIVE_SLOTS = 5;

  @Version
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @Column(nullable = false, length = 40)
  private String name;

  /** Mundo próprio da carreira (kind SAVE). */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "world_id", nullable = false)
  private World world;

  /** Nome do mundo de origem no momento da cópia (RNF1 CA4); o vínculo fica em {@code world.sourceWorld}. */
  @Column(length = 120)
  private String sourceWorldName;

  /** Head coach humano do save. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "manager_id", nullable = false)
  private ManagerProfile manager;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "league_id")
  private League league;

  /** Franquia atual; nula = sem clube. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "club_id")
  private Club club;

  /** Temporada corrente. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "season_id")
  private Season season;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private EmploymentStatus employmentStatus = EmploymentStatus.EMPLOYED;

  @Column(nullable = false)
  private int currentWeek = 1;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private Difficulty difficulty = Difficulty.NORMAL;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private SaveStatus status = SaveStatus.ACTIVE;

  /** 1–5 enquanto ACTIVE; nulo quando ARCHIVED. */
  private Integer slot;

  @Column(nullable = false)
  private long seed;

  private Instant lastPlayedAt;

  public boolean isActive() { return status == SaveStatus.ACTIVE; }
}
