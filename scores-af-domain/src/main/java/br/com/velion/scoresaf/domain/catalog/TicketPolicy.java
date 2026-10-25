package br.com.velion.scoresaf.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Política de ingressos do estádio (RF12/RF26): desconto do sócio, preço dinâmico e fatia reservada a carnês da temporada. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketPolicy {
  @Column(name = "ticket_member_discount_pct", nullable = false) private int memberDiscountPct = 15;
  @Column(name = "ticket_dynamic_pricing", nullable = false) private boolean dynamicPricing;
  /** Percentual dos assentos vendidos como carnê da temporada (0–90). */
  @Column(name = "ticket_season_ticket_pct", nullable = false) private int seasonTicketPct = 60;
}
