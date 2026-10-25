package br.com.velion.scoresaf.web.security;

import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback em memória do {@link SessionCachePort} (do {@code InMemoryCacheAdapters} do basquete, módulo cache-redis). Vive aqui,
 * ao lado do {@link InMemoryRateLimiter}, até o cache-redis entrar (D-8); o adaptador Redis substitui o bean quando
 * {@code scores.persistence.cache=redis}. Mesmo TTL em processo único; é só projeção de leitura de {@code user_session}.
 */
public class InMemorySessionCache implements SessionCachePort {
  private record Expiring(UUID userId, Instant expiresAt) { boolean expired() { return Instant.now().isAfter(expiresAt); } }

  private final Map<String, Expiring> map = new ConcurrentHashMap<>();

  @Override public void put(String hash, UUID userId, Duration ttl) { map.put(hash, new Expiring(userId, Instant.now().plus(ttl))); }

  @Override public Optional<UUID> get(String hash) {
    Expiring e = map.get(hash);
    if (e == null) return Optional.empty();
    if (e.expired()) { map.remove(hash); return Optional.empty(); }
    return Optional.of(e.userId());
  }

  @Override public void evict(String hash) { map.remove(hash); }
}
