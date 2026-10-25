package br.com.velion.scoresaf.application.audit;

import br.com.velion.scoresaf.application.common.tx.AfterCommit;
import br.com.velion.scoresaf.application.port.out.persistence.AuditLogRepository;
import br.com.velion.scoresaf.domain.account.AuditLog;
import br.com.velion.scoresaf.domain.common.AuditContext;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Log de auditoria do RNF7 — explícito, em transação própria ({@code REQUIRES_NEW}). Recusas e falhas ({@code denied},
 * {@code failed}) são gravadas na hora, para sobreviver ao rollback da operação auditada; o sucesso ({@code ok}) só é gravado
 * depois de a transação de quem chamou confirmar ({@link AfterCommit}, D-91): uma operação desfeita (ex.: a segunda de duas
 * escritas concorrentes, que recebe 409) não deixa "sucesso" no log. Sem transação ativa, o sucesso também é gravado na hora.
 * Nunca recebe senha, token ou conteúdo de campo cifrado (só ids e rótulos).
 */
@Service
public class AuditService {
  private final AuditLogRepository repository;
  private final TransactionTemplate requiresNew;

  public AuditService(AuditLogRepository repository, PlatformTransactionManager transactions) {
    this.repository = repository;
    this.requiresNew = new TransactionTemplate(transactions);
    this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String action, String resource, String resourceId, String result, String details) {
    repository.save(entry(action, resource, resourceId, result, details));
  }
  public void ok(String action, String resource, Object resourceId) { ok(action, resource, resourceId, null); }
  public void ok(String action, String resource, Object resourceId, String details) {
    // o contexto (ator, IP, correlationId, hora) é lido agora, na thread da requisição; só a gravação espera a confirmação
    AuditLog entry = entry(action, resource, String.valueOf(resourceId), AuditActions.RESULT_OK, details);
    // a operação já confirmou quando a gravação roda: uma falha da auditoria vira log, não um 500 para dados já gravados
    AfterCommit.run("auditoria " + action, () -> save(entry));
  }
  // Cada atalho imediato tem o próprio @Transactional: a chamada interna a record() não passa pelo proxy, então sem isto o registro
  // entrava na transação de quem chamou e sumia no rollback (ex.: LOGIN_FAILED, que sempre termina em exceção).
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void denied(String action, String resource, Object resourceId, String details) { record(action, resource, String.valueOf(resourceId), AuditActions.RESULT_DENIED, details); }
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failed(String action, String resource, Object resourceId, String details) { record(action, resource, String.valueOf(resourceId), AuditActions.RESULT_FAILED, details); }

  private void save(AuditLog entry) { requiresNew.executeWithoutResult(s -> repository.save(entry)); }

  private static AuditLog entry(String action, String resource, String resourceId, String result, String details) {
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
    return log;
  }
  private static String truncate(String s, int max) { return s == null ? null : (s.length() <= max ? s : s.substring(0, max)); }
}
