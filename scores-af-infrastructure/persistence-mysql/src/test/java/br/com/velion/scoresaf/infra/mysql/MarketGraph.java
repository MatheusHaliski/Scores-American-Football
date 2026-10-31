package br.com.velion.scoresaf.infra.mysql;

import br.com.velion.scoresaf.infra.mysql.repository.ContractEventJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.FreeAgencyWindowJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.WaiverClaimJpaRepository;
import org.springframework.stereotype.Component;

/** Repositórios das tabelas da V8 (mercado, bloco 7a) para os testes de schema. */
@Component
class MarketGraph {
  final ContractEventJpaRepository events; final FreeAgencyWindowJpaRepository windows; final WaiverClaimJpaRepository claims;

  MarketGraph(ContractEventJpaRepository events, FreeAgencyWindowJpaRepository windows, WaiverClaimJpaRepository claims) {
    this.events = events; this.windows = windows; this.claims = claims;
  }
}
