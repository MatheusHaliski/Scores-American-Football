package br.com.velion.scoresaf.domain.career;

/**
 * Tipo de movimento do elenco registrado na carreira (RF4): troca de situação (53, practice squad, IR), dispensa (waivers) e, desde
 * a Q-23 (D-78), a saída de waivers sem reivindicação no avanço da semana (WAIVER_CLEARED: o jogador entra no practice squad) e,
 * desde o bloco 6c, o fim do contrato no avanço da temporada (CONTRACT_EXPIRED: o jogador vira agente livre; a franquia de origem fica
 * no movimento, que é a lista de pendências de renovação do bloco 7, D-100). Bloco 7a (RF65): a contratação de um agente livre
 * (SIGNING, pelo usuário ou pela IA, com a franquia que contratou), a reivindicação em waivers (WAIVER_CLAIM, com a franquia que
 * reivindicou) e a saída de waivers sem reivindicação de quem foi dispensado (WAIVER_UNCLAIMED: vira agente livre).
 */
public enum RosterMoveKind { STATUS_CHANGE, RELEASE, WAIVER_CLEARED, CONTRACT_EXPIRED, SIGNING, WAIVER_CLAIM, WAIVER_UNCLAIMED }
