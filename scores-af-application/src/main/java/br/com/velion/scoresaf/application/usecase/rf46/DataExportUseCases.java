package br.com.velion.scoresaf.application.usecase.rf46;

import br.com.velion.scoresaf.application.common.tx.AfterCommit;
import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.common.i18n.LocalizedMessages;
import br.com.velion.scoresaf.application.port.out.media.MediaStoragePort;
import br.com.velion.scoresaf.application.port.out.notify.EmailPort;
import br.com.velion.scoresaf.application.port.out.persistence.DataExportJobRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.domain.account.DataExportJob;
import br.com.velion.scoresaf.domain.account.ExportStatus;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.application.common.json.Jsons;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF46 CA07 — "Baixar meus dados" (LGPD Art. 18, II e V). O pedido gera JSON (completo) + CSV (resumo) em chaves privadas
 * {@code exports/{userId}/{jobId}.*}, disponíveis por 24 h só ao titular autenticado. Pedidos repetidos em menos de 1 h devolvem o
 * pedido pronto mais recente (evita abuso). Expirados são apagados de hora em hora.
 */
@Service
public class DataExportUseCases implements AccountErasureHandler {
  public static final Duration TTL = Duration.ofHours(24);
  static final Duration REUSE_WINDOW = Duration.ofHours(1);

  public record ExportJobDto(UUID id, String status, Instant requestedAt, Instant readyAt, Instant expiresAt, long sizeBytes,
      String jsonUrl, String csvUrl) {
    static ExportJobDto of(DataExportJob j, Instant now) {
      boolean ok = j.isDownloadable(now);
      String base = "/api/v1/me/export/" + j.getId() + "/download?format=";
      return new ExportJobDto(j.getId(), ok || j.getStatus() != ExportStatus.READY ? j.getStatus().name() : ExportStatus.EXPIRED.name(),
          j.getRequestedAt(), j.getReadyAt(), j.getExpiresAt(), j.getSizeBytes(), ok ? base + "json" : null, ok ? base + "csv" : null);
    }
  }
  public record Download(byte[] content, String contentType, String fileName) {}

  private final UserAccountRepository users; private final DataExportJobRepository jobs; private final AccountDataCollector collector;
  private final MediaStoragePort media; private final EmailPort email; private final LocalizedMessages messages; private final AuditService audit;
  private final ObjectMapper mapper = Jsons.configure(JsonMapper.builder()).enable(SerializationFeature.INDENT_OUTPUT).build();

  public DataExportUseCases(UserAccountRepository users, DataExportJobRepository jobs, AccountDataCollector collector, MediaStoragePort media,
      EmailPort email, LocalizedMessages messages, AuditService audit) {
    this.users = users; this.jobs = jobs; this.collector = collector; this.media = media; this.email = email; this.messages = messages; this.audit = audit;
  }

  @Transactional
  public ExportJobDto request(UUID userId) {
    UserAccount u = users.findById(userId).filter(UserAccount::isUsable).orElseThrow(() -> new NotFoundException("Conta", userId));
    Instant now = Instant.now();
    for (DataExportJob j : jobs.findByUserIdOrderByRequestedAtDesc(userId)) {
      if (j.isDownloadable(now) && j.getRequestedAt().isAfter(now.minus(REUSE_WINDOW))) return ExportJobDto.of(j, now);
      break;
    }
    DataExportJob job = new DataExportJob();
    job.setUser(u); job.setRequestedAt(now); job.setStatus(ExportStatus.REQUESTED);
    job = jobs.save(job);
    try {
      Map<String, Object> data = collector.collect(u);
      byte[] json = mapper.writeValueAsBytes(data);
      byte[] csv = AccountDataCollector.toCsv(mapper.convertValue(data, Map.class)).getBytes(StandardCharsets.UTF_8);
      String base = MediaStoragePort.EXPORTS + userId + "/" + job.getId();
      media.put(base + ".json", json, "application/json");
      AfterCommit.onRollback("media.delete(export.json)", () -> media.delete(base + ".json"));
      media.put(base + ".csv", csv, "text/csv");
      AfterCommit.onRollback("media.delete(export.csv)", () -> media.delete(base + ".csv"));
      job.setJsonKey(base + ".json"); job.setCsvKey(base + ".csv"); job.setSizeBytes(json.length + (long) csv.length);
      job.setStatus(ExportStatus.READY); job.setReadyAt(Instant.now()); job.setExpiresAt(job.getReadyAt().plus(TTL));
      u.setExportRequestedAt(now); users.save(u);
      email.send(u.getEmail(), messages.forUser(u, "mail.export.ready.subject"), messages.forUser(u, "mail.export.ready.body", u.getDisplayName(), 24));
      audit.ok(AuditActions.DATA_EXPORT_REQUESTED, "DataExportJob", job.getId(), "bytes=" + job.getSizeBytes());
    } catch (Exception e) {
      job.setStatus(ExportStatus.FAILED); job.setFailureReason(e.getClass().getSimpleName());
      audit.failed(AuditActions.DATA_EXPORT_REQUESTED, "DataExportJob", job.getId(), e.getClass().getSimpleName());
    }
    return ExportJobDto.of(jobs.save(job), Instant.now());
  }

  @Transactional(readOnly = true)
  public ExportJobDto get(UUID userId, UUID jobId) { return ExportJobDto.of(load(userId, jobId), Instant.now()); }

  @Transactional(readOnly = true)
  public List<ExportJobDto> list(UUID userId) {
    Instant now = Instant.now();
    return jobs.findByUserIdOrderByRequestedAtDesc(userId).stream().limit(10).map(j -> ExportJobDto.of(j, now)).toList();
  }

  @Transactional
  public Download download(UUID userId, UUID jobId, String format) {
    DataExportJob j = load(userId, jobId);
    if (!j.isDownloadable(Instant.now())) throw new BusinessRuleException("EXPORT_EXPIRED", "O arquivo expirou; solicite uma nova exportação");
    boolean csv = "csv".equalsIgnoreCase(format);
    String key = csv ? j.getCsvKey() : j.getJsonKey();
    byte[] bytes = media.get(key).orElseThrow(() -> new BusinessRuleException("EXPORT_EXPIRED", "O arquivo expirou; solicite uma nova exportação"));
    audit.ok(AuditActions.DATA_EXPORT_DOWNLOADED, "DataExportJob", jobId, csv ? "csv" : "json");
    return new Download(bytes, csv ? "text/csv; charset=UTF-8" : "application/json", "scores-af-meus-dados-" + jobId + (csv ? ".csv" : ".json"));
  }

  /** Apaga arquivos expirados (hora em hora). */
  @Scheduled(cron = "${scores.privacy.export-purge-cron:0 5 * * * *}")
  @Transactional
  public int purgeExpired() {
    int n = 0;
    for (DataExportJob j : jobs.findByStatusAndExpiresAtBefore(ExportStatus.READY, Instant.now())) { deleteFiles(j); j.setStatus(ExportStatus.EXPIRED); jobs.save(j); n++; }
    return n;
  }

  /** Eliminação da conta: os arquivos de exportação saem junto (no basquete o método existia, mas ninguém o chamava). */
  @Override
  public void erase(UUID userId) { eraseAll(userId); }

  /** Remove todos os arquivos de exportação do titular (eliminação da conta). */
  @Transactional
  public void eraseAll(UUID userId) {
    for (DataExportJob j : jobs.findByUserIdOrderByRequestedAtDesc(userId)) { deleteFiles(j); j.setStatus(ExportStatus.EXPIRED); jobs.save(j); }
  }

  private void deleteFiles(DataExportJob j) {
    if (j.getJsonKey() != null) media.delete(j.getJsonKey());
    if (j.getCsvKey() != null) media.delete(j.getCsvKey());
  }

  private DataExportJob load(UUID userId, UUID jobId) {
    return jobs.findById(jobId).filter(j -> j.getUser().getId().equals(userId)).orElseThrow(() -> new NotFoundException("Exportação", jobId));
  }
}
