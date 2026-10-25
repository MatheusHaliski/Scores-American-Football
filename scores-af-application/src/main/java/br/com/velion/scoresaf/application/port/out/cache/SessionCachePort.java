package br.com.velion.scoresaf.application.port.out.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache de sessões (brief §11): hash do refresh token → usuário, com TTL igual ao do refresh (7 dias).
 * A fonte da verdade é {@code user_session} no MySQL; aqui só a projeção de leitura rápida.
 * Chave Redis: {@code scores:session:{hash}}.
 */
public interface SessionCachePort {

  void put(String refreshTokenHash, UUID userId, Duration ttl);

  Optional<UUID> get(String refreshTokenHash);

  void evict(String refreshTokenHash);
}
