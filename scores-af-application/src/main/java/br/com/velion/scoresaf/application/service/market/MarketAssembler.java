package br.com.velion.scoresaf.application.service.market;

import br.com.velion.scoresaf.application.dto.MarketDtos;
import br.com.velion.scoresaf.domain.market.ContractOffer;

/** Conversões do mercado para os DTOs (bloco 7a). */
public final class MarketAssembler {
  private MarketAssembler() {}

  public static MarketDtos.OfferDto offer(ContractOffer o) {
    return o == null ? null : new MarketDtos.OfferDto(o.years(), o.salaryByYear(), o.signingBonus(), o.guaranteed(), o.totalValue(), o.apy());
  }
}
