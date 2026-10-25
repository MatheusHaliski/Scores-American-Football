package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.account.UserSession;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/** Sessões de refresh token (RF2). */
public interface UserSessionRepository extends BaseRepository<UserSession, UUID> {
  Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);
  List<UserSession> findByUserId(UUID userId);
  List<UserSession> findByUserIdAndRevokedAtIsNull(UUID userId);
  long deleteByExpiresAtBefore(Instant threshold);
  /** D-25: a família ainda tem sessão ativa? Senão, o access token dela é recusado. */
  boolean existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(UUID familyId, Instant now);
  /**
   * Consome a sessão de forma atômica: marca {@code revokedAt} só se ela ainda estiver ativa e devolve quantas linhas mudou
   * (0 ou 1). Dois refreshes simultâneos do mesmo token disputam esta linha; só um ganha, o outro vê 0 e é tratado como reuso.
   */
  int revokeIfActive(UUID id, Instant revokedAt);
}
