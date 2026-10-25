package br.com.velion.scoresaf.domain.match;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Plano de jogo salvo da franquia do usuário (RF61): as tendências que o motor entende ({@link GamePlan}: corrida × passe, 4ª
 * descida, ritmo, foco defensivo, blitz e defesa base). Nasce do estilo do head coach (D-38) na primeira leitura; o PUT troca tudo
 * e exige {@code version} (409 em conflito, como o depth chart, D-53). O kickoff congela o plano no snapshot da partida.
 */
@Entity
@Table(name = "game_plan", indexes = @Index(name = "ix_game_plan_club", columnList = "club_id"),
    uniqueConstraints = @UniqueConstraint(name = "ux_game_plan_career_club", columnNames = {"career_save_id", "club_id"}))
@Getter
@Setter
@NoArgsConstructor
public class GamePlanSetup extends AuditableEntity {

  @Version
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "club_id", nullable = false)
  private Club club;

  @Column(nullable = false)
  private int passRatio;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private GamePlan.Aggression fourthDownAggression;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private GamePlan.Tempo tempo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private GamePlan.DefensiveFocus defensiveFocus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private GamePlan.Emphasis blitzFrequency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private DefensePackage baseDefense;

  /** Sobe a cada gravação (a versão otimista sobe junto). */
  @Column(nullable = false)
  private int revision;

  public void apply(GamePlan p) {
    passRatio = p.passRatio(); fourthDownAggression = p.fourthDownAggression(); tempo = p.tempo(); defensiveFocus = p.defensiveFocus();
    blitzFrequency = p.blitzFrequency(); baseDefense = p.baseDefense(); revision++;
  }

  /** O plano no formato do motor (sem o playbook, que é outro recurso). */
  public GamePlan toEngine() { return new GamePlan(passRatio, fourthDownAggression, tempo, defensiveFocus, blitzFrequency, baseDefense); }

  public long currentVersion() { return version == null ? 0 : version; }
}
