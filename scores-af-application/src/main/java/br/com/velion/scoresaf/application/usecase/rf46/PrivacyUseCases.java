package br.com.velion.scoresaf.application.usecase.rf46;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConsentRequiredException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.port.out.persistence.ConsentRecordRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.domain.account.ConsentPurpose;
import br.com.velion.scoresaf.domain.account.ConsentRecord;
import br.com.velion.scoresaf.domain.account.ConsentSource;
import br.com.velion.scoresaf.domain.account.ProfileVisibility;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserStatus;
import br.com.velion.scoresaf.domain.common.AuditContext;
import br.com.velion.scoresaf.domain.common.Hashing;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF46 / HU-RF46 CA03 — Privacidade: visibilidade do perfil (padrão "Somente eu", Art. 18, VII) e consentimentos granulares por
 * finalidade (Art. 7º, I; 8º §5º; 11). Regras anti-dark-pattern: sem registro ⇒ não consentido (nada pré-marcado); cada finalidade
 * é decidida em separado; revogar custa uma chamada, como conceder; cada decisão vira um {@link ConsentRecord} imutável com versão
 * do texto, origem, hash do IP e user-agent (prova do consentimento). Revogações disparam os {@link ConsentRevocationHandler}s.
 */
@Service
public class PrivacyUseCases {

  public record ConsentState(String purpose, boolean granted, String legalBasis, String textVersion, String currentTextVersion,
      boolean sensitive, boolean outdated, Instant updatedAt, String source) {}
  public record ConsentHistoryEntry(String purpose, boolean granted, String textVersion, String source, Instant recordedAt) {}
  public record PrivacyView(String profileVisibility, List<ConsentState> consents) {}
  public record UpdateCommand(Map<String, Boolean> consents, String profileVisibility, String source) {}

  private final UserAccountRepository users;
  private final ConsentRecordRepository records;
  private final AuditService audit;
  private final List<ConsentRevocationHandler> handlers;

  public PrivacyUseCases(UserAccountRepository users, ConsentRecordRepository records, AuditService audit, List<ConsentRevocationHandler> handlers) {
    this.users = users; this.records = records; this.audit = audit; this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
  }

  @Transactional(readOnly = true)
  public PrivacyView view(UUID userId) {
    UserAccount u = load(userId);
    return new PrivacyView(u.getProfileVisibility().name(), states(userId));
  }

  @Transactional(readOnly = true)
  public List<ConsentState> states(UUID userId) {
    Map<ConsentPurpose, ConsentRecord> latest = latestByPurpose(userId);
    List<ConsentState> out = new ArrayList<>();
    for (ConsentPurpose p : ConsentPurpose.values()) {
      ConsentRecord r = latest.get(p);
      boolean granted = r != null && r.isGranted();
      out.add(new ConsentState(p.name(), granted, p.legalBasis(), r == null ? null : r.getTextVersion(), p.currentTextVersion(), p.sensitive(),
          granted && !p.currentTextVersion().equals(r.getTextVersion()), r == null ? null : r.getRecordedAt(), r == null ? null : r.getSource().name()));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public List<ConsentHistoryEntry> history(UUID userId) {
    load(userId);
    return records.findByUserIdOrderByRecordedAtDesc(userId).stream()
        .map(r -> new ConsentHistoryEntry(r.getPurpose().name(), r.isGranted(), r.getTextVersion(), r.getSource().name(), r.getRecordedAt())).toList();
  }

  /**
   * Estado vigente (último registro) — usado para autorizar tratamentos dependentes de consentimento. Finalidade sensível no
   * texto antigo conta como não dada até o reaceite ({@link ConsentRecord#authorizes()}).
   */
  @Transactional(readOnly = true)
  public boolean isGranted(UUID userId, ConsentPurpose purpose) {
    return records.findFirstByUserIdAndPurposeOrderByRecordedAtDesc(userId, purpose).map(ConsentRecord::authorizes).orElse(false);
  }

  /** Lança {@link ConsentRequiredException} (403 CONSENT_REQUIRED) se a finalidade não estiver consentida. */
  public void require(UUID userId, ConsentPurpose purpose) {
    if (!isGranted(userId, purpose)) throw new ConsentRequiredException(purpose);
  }

  @Transactional
  public PrivacyView update(UUID userId, UpdateCommand c) {
    UserAccount u = load(userId);
    ConsentSource source = parseSource(c.source());
    if (c.profileVisibility() != null) {
      ProfileVisibility v;
      try { v = ProfileVisibility.valueOf(c.profileVisibility().trim().toUpperCase()); }
      catch (IllegalArgumentException e) { throw new BusinessRuleException("INVALID_PROFILE_VISIBILITY", "Visibilidade inválida: " + c.profileVisibility(), c.profileVisibility()); }
      if (v != u.getProfileVisibility()) {
        u.setProfileVisibility(v); users.save(u);
        audit.ok(AuditActions.PROFILE_VISIBILITY_CHANGED, "UserAccount", userId, v.name());
      }
    }
    if (c.consents() != null) {
      for (Map.Entry<String, Boolean> e : c.consents().entrySet()) {
        if (e.getValue() == null) continue;
        ConsentPurpose p = parsePurpose(e.getKey());
        set(u, p, e.getValue(), source);
      }
    }
    return new PrivacyView(u.getProfileVisibility().name(), states(userId));
  }

  /** Concede ou revoga uma finalidade. Idempotente: se o estado não muda, não grava registro. */
  @Transactional
  public ConsentState set(UUID userId, ConsentPurpose purpose, boolean granted, ConsentSource source) {
    UserAccount u = load(userId);
    set(u, purpose, granted, source);
    return states(userId).stream().filter(s -> s.purpose().equals(purpose.name())).findFirst().orElseThrow();
  }

  private void set(UserAccount u, ConsentPurpose p, boolean granted, ConsentSource source) {
    UUID userId = u.getId();
    ConsentRecord current = records.findFirstByUserIdAndPurposeOrderByRecordedAtDesc(userId, p).orElse(null);
    boolean currentGranted = current != null && current.isGranted();
    boolean sameVersion = current != null && p.currentTextVersion().equals(current.getTextVersion());
    if (currentGranted == granted && (!granted || sameVersion)) return;
    ConsentRecord r = new ConsentRecord();
    r.setUser(u); r.setPurpose(p); r.setGranted(granted); r.setTextVersion(p.currentTextVersion()); r.setSource(source);
    String ip = AuditContext.ip();
    r.setIpHash(ip == null ? null : Hashing.sha256Hex(ip));
    String ua = AuditContext.userAgent();
    r.setUserAgent(ua == null ? null : ua.substring(0, Math.min(120, ua.length())));
    r.setRecordedAt(Instant.now());
    records.save(r);
    if (p == ConsentPurpose.MARKETING_EMAIL && u.isMarketingConsent() != granted) { u.setMarketingConsent(granted); users.save(u); }
    audit.ok(granted ? AuditActions.CONSENT_GRANTED : AuditActions.CONSENT_REVOKED, "UserAccount", userId, p.name() + " v" + p.currentTextVersion());
    if (!granted && currentGranted) for (ConsentRevocationHandler h : handlers) if (h.purpose() == p) h.onRevoked(userId);
  }

  private Map<ConsentPurpose, ConsentRecord> latestByPurpose(UUID userId) {
    Map<ConsentPurpose, ConsentRecord> m = new EnumMap<>(ConsentPurpose.class);
    for (ConsentRecord r : records.findByUserIdOrderByRecordedAtDesc(userId)) m.putIfAbsent(r.getPurpose(), r);
    return m;
  }

  private UserAccount load(UUID userId) {
    return users.findById(userId).filter(u -> u.getStatus() != UserStatus.DELETED).orElseThrow(() -> new NotFoundException("Conta", userId));
  }

  static ConsentPurpose parsePurpose(String v) {
    try { return ConsentPurpose.valueOf(v.trim().toUpperCase()); }
    catch (RuntimeException e) { throw new BusinessRuleException("INVALID_CONSENT_PURPOSE", "Finalidade de consentimento inválida: " + v, v); }
  }

  static ConsentSource parseSource(String v) {
    if (v == null || v.isBlank()) return ConsentSource.SETTINGS;
    try { return ConsentSource.valueOf(v.trim().toUpperCase()); } catch (IllegalArgumentException e) { return ConsentSource.SETTINGS; }
  }
}
