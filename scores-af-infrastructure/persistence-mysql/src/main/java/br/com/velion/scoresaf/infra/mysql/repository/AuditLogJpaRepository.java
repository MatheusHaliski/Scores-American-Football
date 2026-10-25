package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.AuditLogRepository;
import br.com.velion.scoresaf.domain.account.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link AuditLogRepository}. */
public interface AuditLogJpaRepository extends JpaRepository<AuditLog, UUID>, AuditLogRepository {}
