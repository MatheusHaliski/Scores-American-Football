package br.com.velion.scoresaf.application.usecase.rf46;

import br.com.velion.scoresaf.application.dto.AccountDto;
import br.com.velion.scoresaf.application.dto.SettingsDto;
import br.com.velion.scoresaf.application.port.out.persistence.AuditLogRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ConsentRecordRepository;
import br.com.velion.scoresaf.application.port.out.persistence.NotificationPreferenceRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSettingsRepository;
import br.com.velion.scoresaf.domain.account.UserAccount;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Reúne <b>todas</b> as entidades do titular para o direito de acesso/portabilidade (RF46 CA07, LGPD Art. 18, II e V): conta,
 * configurações, consentimentos (histórico completo), preferências de notificação, sessões e atividade recente, mais as seções
 * dos módulos do jogo pelos {@link AccountDataContributor}s (carreiras, compras, "Meu jogador"; D-12). Nunca inclui hashes de
 * senha/token nem chaves de arquivo.
 */
@Component
public class AccountDataCollector {
  static final int ACTIVITY_LIMIT = 500;

  private final UserSettingsRepository settings; private final ConsentRecordRepository consents; private final NotificationPreferenceRepository prefs;
  private final UserSessionRepository sessions; private final AuditLogRepository auditLogs; private final List<AccountDataContributor> contributors;

  public AccountDataCollector(UserSettingsRepository settings, ConsentRecordRepository consents, NotificationPreferenceRepository prefs,
      UserSessionRepository sessions, AuditLogRepository auditLogs, List<AccountDataContributor> contributors) {
    this.settings = settings; this.consents = consents; this.prefs = prefs; this.sessions = sessions; this.auditLogs = auditLogs;
    this.contributors = contributors == null ? List.of() : List.copyOf(contributors);
  }

  public Map<String, Object> collect(UserAccount u) {
    UUID userId = u.getId();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("schema", "scores-af-data-export/v1");
    out.put("exportedAt", Instant.now().toString());
    out.put("account", AccountDto.of(u));
    settings.findByUserId(userId).ifPresent(s -> out.put("settings", SettingsDto.of(s, u)));
    out.put("consents", consents.findByUserIdOrderByRecordedAtDesc(userId).stream().map(r -> row(
        "purpose", r.getPurpose().name(), "granted", r.isGranted(), "textVersion", r.getTextVersion(), "source", r.getSource().name(),
        "recordedAt", String.valueOf(r.getRecordedAt()))).toList());
    out.put("notificationPreferences", prefs.findByUserId(userId).stream().map(p -> row("type", p.getType().name(), "enabled", p.isEnabled())).toList());
    for (AccountDataContributor c : contributors) c.contribute(userId, out);
    out.put("sessions", sessions.findByUserIdAndRevokedAtIsNull(userId).stream().map(s -> row("id", String.valueOf(s.getId()),
        "createdAt", String.valueOf(s.getCreatedAt()), "expiresAt", String.valueOf(s.getExpiresAt()))).toList());
    out.put("activity", auditLogs.findByActorOrderByTimestampDesc(userId.toString(), PageRequest.of(0, ACTIVITY_LIMIT)).getContent().stream()
        .map(a -> row("action", a.getAction(), "resource", a.getResource(), "result", a.getResult(), "timestamp", String.valueOf(a.getTimestamp()))).toList());
    return out;
  }

  /** Linha de uma seção (pares chave, valor), na ordem dada; os {@link AccountDataContributor}s podem usar. */
  public static Map<String, Object> row(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
    return m;
  }

  /** CSV resumido (seção;campo;valor) — legível em planilhas; o JSON é o formato completo. */
  @SuppressWarnings("unchecked")
  public static String toCsv(Map<String, Object> data) {
    StringBuilder sb = new StringBuilder("section;index;field;value\n");
    for (Map.Entry<String, Object> e : data.entrySet()) {
      Object v = e.getValue();
      if (v instanceof List<?> list) {
        for (int i = 0; i < list.size(); i++) {
          Object item = list.get(i);
          if (item instanceof Map<?, ?> m) for (Map.Entry<?, ?> f : m.entrySet()) line(sb, e.getKey(), i, String.valueOf(f.getKey()), f.getValue());
          else line(sb, e.getKey(), i, "value", item);
        }
      } else if (v instanceof Map<?, ?> m) {
        for (Map.Entry<?, ?> f : m.entrySet()) line(sb, e.getKey(), 0, String.valueOf(f.getKey()), f.getValue());
      } else if (v instanceof Record r) {
        for (var c : r.getClass().getRecordComponents()) {
          try { line(sb, e.getKey(), 0, c.getName(), c.getAccessor().invoke(r)); } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
        }
      } else line(sb, e.getKey(), 0, "value", v);
    }
    return sb.toString();
  }

  private static void line(StringBuilder sb, String section, int idx, String field, Object value) {
    sb.append(csv(section)).append(';').append(idx).append(';').append(csv(field)).append(';').append(csv(value == null ? "" : String.valueOf(value))).append('\n');
  }

  private static String csv(String s) {
    String v = s.replace("\"", "\"\"");
    // neutraliza injeção de fórmulas em planilhas (CSV injection)
    if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
    return (v.contains(";") || v.contains("\"") || v.contains("\n")) ? "\"" + v + "\"" : v;
  }
}
