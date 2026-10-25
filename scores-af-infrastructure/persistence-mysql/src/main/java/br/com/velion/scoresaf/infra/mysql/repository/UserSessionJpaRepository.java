package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.domain.account.UserSession;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data implementa a porta {@link UserSessionRepository}. */
public interface UserSessionJpaRepository extends JpaRepository<UserSession, UUID>, UserSessionRepository {
  /** UPDATE condicional: o banco serializa as duas transações na mesma linha, e a segunda não encontra mais a sessão ativa. */
  @Override
  @Modifying(flushAutomatically = true)
  @Query("update UserSession s set s.revokedAt = :revokedAt where s.id = :id and s.revokedAt is null")
  int revokeIfActive(@Param("id") UUID id, @Param("revokedAt") Instant revokedAt);
}
