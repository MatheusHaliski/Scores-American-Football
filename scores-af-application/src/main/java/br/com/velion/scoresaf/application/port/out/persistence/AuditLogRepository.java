package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.account.AuditLog;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/** Trilha de auditoria (RNF7). */
public interface AuditLogRepository extends BaseRepository<AuditLog, UUID> {
  Page<AuditLog> findByActorOrderByTimestampDesc(String actor, Pageable pageable);
  Page<AuditLog> findByTimestampBetweenOrderByTimestampDesc(Instant from, Instant to, Pageable pageable);
  Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);
  Page<AuditLog> findByResourceAndResourceIdOrderByTimestampDesc(String resource, String resourceId, Pageable pageable);
  Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
