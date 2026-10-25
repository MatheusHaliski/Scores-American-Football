package br.com.velion.scoresaf.application.audit;

import br.com.velion.scoresaf.application.port.out.persistence.AuditLogRepository;
import br.com.velion.scoresaf.domain.account.AuditLog;
import br.com.velion.scoresaf.domain.common.AuditContext;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Log de auditoria do RNF7 — explícito, em transação própria ({@code REQUIRES_NEW}) para sobreviver ao rollback
 * da operação auditada. Nunca recebe senha, token ou conteúdo de campo cifrado (só ids e rótulos).
 */
@Service
public class AuditService {
  private final AuditLogRepository repository;
  public AuditService(AuditLogRepository repository) { this.repository = repository; }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String action, String resource, String resourceId, String result, String details) {
    AuditLog log = new AuditLog();
    log.setActor(AuditContext.actor());
    log.setAction(action);
    log.setResource(resource);
    log.setResourceId(resourceId == null ? null : truncate(resourceId, 64));
    log.setResult(result);
    log.setIp(truncate(AuditContext.ip(), 64));
    log.setUserAgent(truncate(AuditContext.userAgent(), 255));
    log.setCorrelationId(truncate(AuditContext.correlationId(), 64));
    log.setTimestamp(Instant.now());
    log.setDetails(details);
    repository.save(log);
  }
  // Cada atalho tem o próprio @Transactional: a chamada interna a record() não passa pelo proxy, então sem isto o registro
  // entrava na transação de quem chamou e sumia no rollback (ex.: LOGIN_FAILED, que sempre termina em exceção).
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void ok(String action, String resource, Object resourceId) { record(action, resource, String.valueOf(resourceId), AuditActions.RESULT_OK, null); }
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void ok(String action, String resource, Object resourceId, String details) { record(action, resource, String.valueOf(resourceId), AuditActions.RESULT_OK, details); }
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void denied(String action, String resource, Object resourceId, String details) { record(action, resource, String.valueOf(resourceId), AuditActions.RESULT_DENIED, details); }
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failed(String action, String resource, Object resourceId, String details) { record(action, resource, String.valueOf(resourceId), AuditActions.RESULT_FAILED, details); }
  private static String truncate(String s, int max) { return s == null ? null : (s.length() <= max ? s : s.substring(0, max)); }
}
