package br.com.velion.scoresaf.domain.account;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.shared.Difficulty;
import br.com.velion.scoresaf.domain.shared.SimulationSpeed;
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

/**
 * Preferências do jogador (RF10, HU-RF10): duração do quarto na partida ao vivo e nível de chamada de jogada (futebol americano,
 * D-10), dificuldade padrão, artes, trilha, fonte, acessibilidade.
 */
@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserSettings extends AuditableEntity {
  public static final int MIN_QUARTER_MINUTES = 1;
  public static final int MAX_QUARTER_MINUTES = 15;
  public static final int DEFAULT_QUARTER_MINUTES = 3;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private UserAccount user;

  /**
   * Quanto tempo real (minutos) cada quarto leva na partida ao vivo (RF10, RNF2). O relógio de jogo continua com os 15 minutos
   * regulamentares; só a exibição acelera. Faixa {@value #MIN_QUARTER_MINUTES}–{@value #MAX_QUARTER_MINUTES}.
   */
  @Column(name = "quarter_duration_minutes", nullable = false)
  private int quarterDurationMinutes = DEFAULT_QUARTER_MINUTES;

  /** Quando o manager chama a jogada (RF10, RF62, Q-2): só nos momentos decisivos (padrão), em todas ou nunca. */
  @Enumerated(EnumType.STRING)
  @Column(name = "play_calling_level", nullable = false, columnDefinition = "VARCHAR(32)")
  private PlayCallingLevel playCallingLevel = PlayCallingLevel.KEY_MOMENTS;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private Difficulty difficulty = Difficulty.NORMAL;

  /** Chave da arte de fundo (asset no S3). */
  @Column(length = 64)
  private String backgroundArt;

  /** Skin dos esquemas de jogador/clube/liga. */
  @Column(length = 64)
  private String schemeArt;

  @Column(length = 64)
  private String soundtrack;

  @Column(length = 64)
  private String fontFamily;

  @Column(nullable = false)
  private boolean reducedMotion;

  @Column(nullable = false)
  private boolean notificationsEnabled = true;

  // RF46 — Aparência
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private ThemeMode theme = ThemeMode.SYSTEM;

  @Column(nullable = false)
  private boolean highContrast;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private FontScale fontScale = FontScale.MEDIUM;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private UiDensity density = UiDensity.COMFORTABLE;

  // RF46 — Idioma: formato regional (datas, números, moeda); null = o mesmo do idioma da conta
  @Column(length = 16)
  private String regionalFormat;

  // RF36 — velocidade padrão da simulação pelo calendário
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private SimulationSpeed simulationSpeed = SimulationSpeed.FAST;
}
