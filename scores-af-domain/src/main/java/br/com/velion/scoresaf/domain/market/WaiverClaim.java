package br.com.velion.scoresaf.domain.market;

import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.Entity;
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
 * Reivindicação pendente do usuário por um jogador em waivers (RF65, D-137): vale até o fim do período (fechamento da semana, dia da
 * free agency ou avanço da temporada), quando a fila decide pela ordem inversa da classificação; depois é apagada. Uma por jogador e
 * franquia (chave única: a repetida responde 409).
 */
@Entity
@Table(name = "waiver_claim", indexes = @Index(name = "ix_waiver_claim_player", columnList = "player_id"),
    uniqueConstraints = @UniqueConstraint(name = "ux_waiver_claim", columnNames = {"career_save_id", "player_id", "club_id"}))
@Getter
@Setter
@NoArgsConstructor
public class WaiverClaim extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_save_id", nullable = false)
  private CareerSave career;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "player_id", nullable = false)
  private Player player;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "club_id", nullable = false)
  private Club club;
}
