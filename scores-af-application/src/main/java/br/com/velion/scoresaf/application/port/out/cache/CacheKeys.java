package br.com.velion.scoresaf.application.port.out.cache;

import java.util.UUID;

/**
 * Convenção de chaves do cache (Redis ou memória). Centralizada para que casos de uso e adaptadores
 * usem exatamente os mesmos nomes.
 */
public final class CacheKeys {
  private CacheKeys() {}

  /** {@code scoresaf:session:{hash}} — sessão por hash do refresh token. */
  public static String session(String refreshTokenHash) { return "scoresaf:session:" + refreshTokenHash; }

  /** {@code rl:{bucket}:{ip}} — janela de rate limit. */
  public static String rateLimit(String bucket, String ip) { return "rl:" + bucket + ":" + ip; }
}
