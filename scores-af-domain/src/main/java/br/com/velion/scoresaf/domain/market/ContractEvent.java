package br.com.velion.scoresaf.domain.market;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.season.Season;
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
 * Transação de contrato da carreira (RF49/RF65, bloco 7a): contratação de agente livre, renovação (do usuário ou da IA),
 * reestruturação, franchise tag e reivindicação em waivers, com os números do contrato. É o histórico do mercado (as últimas
 * contratações da free agency) e garante a franchise tag única por temporada e franquia: só a linha da tag tem {@code tagSlot} = 1, e a
 * chave única {@code (season_id, club_id, tag_slot)} recusa a segunda (as outras linhas ficam com nulo, que não repete). Centavos de S$.
 */
@Entity
@Table(name = "contract_event", indexes = {@Index(name = "ix_contract_event_career", columnList = "career_save_id, created_at"),
    @Index(name = "ix_contract_event_player", columnList = "player_id"), @Index(name = "ix_contract_event_club", columnList = "club_id")},
    uniqueConstraints = @UniqueConstraint(name = "ux_contract_event_tag", columnNames = {"season_id", "club_id", "tag_slot"}))
@Getter
@Setter
@NoArgsConstructor
public class ContractEvent extends AuditableEntity {

  public enum Kind { SIGNING, RENEWAL, RESTRUCTURE, FRANCHISE_TAG, WAIVER_CLAIM }

  /** Quem fez: o usuário ou a IA. */
  public enum Source { USER, AI }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "season_id", nullable = false)
  private Season season;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "club_id", nullable = false)
  private Club club;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "player_id", nullable = false)
  private Player player;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private Kind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(16)")
  private Source source = Source.USER;

  /** Dia da free agency em que aconteceu (nulo fora da janela; DAY é palavra reservada no H2). */
  @Column(name = "market_day")
  private Integer day;

  /** Anos do contrato depois da transação. */
  @Column(nullable = false)
  private int years;

  /** Valor total do contrato depois da transação (salários + luvas). */
  @Column(nullable = false)
  private long totalValue;

  @Column(nullable = false)
  private long signingBonus;

  @Column(nullable = false)
  private long guaranteed;

  /** Valor da operação: salário convertido (reestruturação), valor da tag ou média anual (contratação e renovação). */
  @Column(nullable = false)
  private long amount;

  /** 1 só na franchise tag (chave única por temporada e franquia); nulo nas outras. */
  @Column(name = "tag_slot")
  private Integer tagSlot;
}
