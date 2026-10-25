package br.com.velion.scoresaf.application.usecase.rf46;

import br.com.velion.scoresaf.application.port.out.persistence.AuditLogRepository;
import br.com.velion.scoresaf.domain.account.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF46 CA07 — histórico de atividades do titular (transparência, Art. 9º): ações auditadas com data, resultado, dispositivo e IP
 * mascarado. Detalhes internos da auditoria não são expostos.
 */
@Service
public class ActivityUseCases {
  public record ActivityEntry(String action, String resource, String result, Instant timestamp, String ipMasked, String device) {}

  private final AuditLogRepository logs;
  public ActivityUseCases(AuditLogRepository logs) { this.logs = logs; }

  @Transactional(readOnly = true)
  public Page<ActivityEntry> list(UUID userId, int page, int size) {
    int s = Math.max(1, Math.min(size, 100));
    return logs.findByActorOrderByTimestampDesc(userId.toString(), PageRequest.of(Math.max(0, page), s)).map(ActivityUseCases::entry);
  }

  static ActivityEntry entry(AuditLog a) {
    return new ActivityEntry(a.getAction(), a.getResource(), a.getResult(), a.getTimestamp(), maskIp(a.getIp()), device(a.getUserAgent()));
  }

  static String maskIp(String ip) {
    if (ip == null || ip.isBlank()) return null;
    if (ip.contains(":")) { String[] p = ip.split(":"); return p.length >= 2 ? p[0] + ":" + p[1] + ":…" : "…"; }
    String[] p = ip.split("\\.");
    return p.length == 4 ? p[0] + "." + p[1] + ".x.x" : "…";
  }

  static String device(String ua) {
    if (ua == null || ua.isBlank()) return null;
    String os = ua.contains("Android") ? "Android" : ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Windows") ? "Windows"
        : ua.contains("Mac OS") ? "macOS" : ua.contains("Linux") ? "Linux" : "Outro";
    String br = ua.contains("Edg/") ? "Edge" : ua.contains("Chrome/") ? "Chrome" : ua.contains("Firefox/") ? "Firefox" : ua.contains("Safari/") ? "Safari" : "App";
    return br + " · " + os;
  }
}
