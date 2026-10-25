package br.com.velion.scoresaf.domain.catalog;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.game.Ruleset;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Liga do catálogo (RF5, RF16, RF34): regras ({@link LeagueRules}, por {@link Ruleset}), estrutura (conferências × divisões) e
 * dinheiro da liga (teto e piso salarial, cota de TV), em centavos de S$. {@code teamCount}, {@code conferenceCount} e
 * {@code divisionsPerConference} são caches da estrutura, gravados pelo editor (PUT .../structure) e pelo mundo semente.
 */
@Entity
@Table(name = "league", indexes = @Index(name = "ix_league_world", columnList = "world_id"))
@Getter
@Setter
@NoArgsConstructor
public class League extends AuditableEntity {
  /** Menor e maior número de franquias de uma liga (docs/01 §3.2, Q-3). */
  public static final int MIN_TEAMS = 8;
  public static final int MAX_TEAMS = 32;
  public static final int MAX_CONFERENCES = 2;
  public static final int MAX_DIVISIONS_PER_CONFERENCE = 4;

  @Version
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "world_id", nullable = false)
  private World world;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 8)
  private String shortName;

  /** País/região fictícia (ISO-3 ou "INT"). */
  @Column(nullable = false, length = 3)
  private String country = "INT";

  @Column(nullable = false)
  private int tier = 1;

  @Column(length = 512)
  private String logoUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private Ruleset ruleset = Ruleset.PRO;

  @Embedded
  private LeagueRules rules = LeagueRules.pro();

  @Column(nullable = false) private int teamCount;
  @Column(nullable = false) private int conferenceCount;
  @Column(nullable = false) private int divisionsPerConference;

  /** Teto salarial por temporada (centavos de S$). */
  @Column(nullable = false) private long salaryCap;
  /** Piso salarial por temporada (centavos de S$). */
  @Column(nullable = false) private long salaryFloor;

  @Column(nullable = false) private int prestige = 50;

  /** Cota de TV por temporada e franquia (centavos de S$). */
  @Column(nullable = false) private long tvRightsPerSeason;
}
