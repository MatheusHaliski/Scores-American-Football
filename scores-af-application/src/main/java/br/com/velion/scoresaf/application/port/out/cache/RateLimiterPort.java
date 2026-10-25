package br.com.velion.scoresaf.application.port.out.cache;

import java.time.Duration;

/**
 * Contador atômico por balde com janela fixa (bloco 3 de segurança). Redis em cache-redis (scripts Lua); fallback em memória.
 * {@link #tryAcquire} reserva antes do trabalho caro (ex.: hash da senha): pedidos paralelos nunca passam do limite.
 */
public interface RateLimiterPort {
  /** Reserva uma unidade. @return true se ficou dentro do limite (a reserva fica contada até a janela vencer). */
  boolean tryAcquire(String key, int maxPerWindow, Duration window);

  /** Devolve uma reserva; o contador nunca fica abaixo de 0. */
  default void release(String key) {}

  /** Zera o balde (login correto, redefinição de senha por e-mail). */
  default void reset(String key) {}
}
