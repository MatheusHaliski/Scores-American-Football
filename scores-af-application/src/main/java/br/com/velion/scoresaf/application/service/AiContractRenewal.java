package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.List;
import java.util.UUID;

/**
 * Renovação dos contratos que terminam nas franquias da IA, no avanço da temporada, antes da free agency abrir (Q-42, D-120; bloco 7a,
 * D-138: {@link MarketAiContractRenewal}, com o pedido do jogador e o teto rígido). O {@code SeasonUseCases.advance} chama uma vez por
 * franquia da IA, com o elenco que continua e os contratos que acabaram; a implementação escolhe quem renova, aplica o contrato novo, a
 * situação (53 ou practice squad) e devolve os ids renovados. Quem não renova vira agente livre com o movimento {@code CONTRACT_EXPIRED}
 * e vai para a free agency. A franquia do usuário nunca passa por aqui: ela renova pelo {@code POST /players/{pid}/renew} até o avanço.
 */
public interface AiContractRenewal {

  /** Contrato que terminou: o jogador (ainda com a franquia) e a situação em que estava. */
  record Expired(Player player, RosterStatus from) {}

  /**
   * @param clubId franquia da IA
   * @param staying jogadores da franquia com contrato em vigor (todas as situações com clube)
   * @param expired contratos da franquia que terminaram na temporada
   * @param newSeason ano da temporada que começa (início do contrato novo)
   * @param salaryCap teto da liga (centavos)
   * @param deadMoney dead money da franquia já lançado na temporada que começa (centavos)
   * @return ids dos jogadores renovados
   */
  List<UUID> renew(UUID clubId, List<Player> staying, List<Expired> expired, int newSeason, LeagueRules rules, long salaryCap, long deadMoney);
}
