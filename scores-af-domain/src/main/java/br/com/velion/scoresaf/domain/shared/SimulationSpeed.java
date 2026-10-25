package br.com.velion.scoresaf.domain.shared;

/**
 * Velocidade da simulação pelo calendário (RF36): INSTANT (só resultados por rodada), FAST (resumo por rodada: placar, destaques,
 * moral), NORMAL (placar por quarto + feed compacto). Não altera o resultado — o motor é o mesmo e determinístico (RF42).
 */
public enum SimulationSpeed { INSTANT, FAST, NORMAL }
