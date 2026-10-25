package br.com.velion.scoresaf.application.dto;

import br.com.velion.scoresaf.domain.account.UserSession;
import java.time.Instant;
import java.util.UUID;

public record SessionDto(UUID id, String ip, String userAgent, Instant createdAt, Instant expiresAt, boolean current) {
  public static SessionDto of(UserSession s, boolean current) { return new SessionDto(s.getId(), s.getIp(), s.getUserAgent(), s.getCreatedAt(), s.getExpiresAt(), current); }
}
