package br.com.velion.scoresaf.web.controller.account;

import br.com.velion.scoresaf.application.dto.AccountDto;
import br.com.velion.scoresaf.application.dto.SessionDto;
import br.com.velion.scoresaf.application.dto.SettingsDto;
import br.com.velion.scoresaf.application.usecase.rf03.AccountUseCases;
import br.com.velion.scoresaf.application.usecase.rf10.SettingsUseCases;
import br.com.velion.scoresaf.application.usecase.rf46.AccountDeletionUseCases;
import br.com.velion.scoresaf.application.usecase.rf46.ActivityUseCases;
import br.com.velion.scoresaf.application.usecase.rf46.DataExportUseCases;
import br.com.velion.scoresaf.application.usecase.rf46.EmailChangeUseCases;
import br.com.velion.scoresaf.application.usecase.rf46.PrivacyUseCases;
import br.com.velion.scoresaf.web.dto.PageResponse;
import br.com.velion.scoresaf.web.security.CurrentUser;
import br.com.velion.scoresaf.web.security.RefreshCookies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF3 / RF10 / RF46 — conta, sessões, privacidade (visibilidade + consentimentos granulares), aparência/idioma, exportação e
 * exclusão com prazo de arrependimento. Cada endpoint cita o CA do HU-RF46 que atende e o artigo da LGPD.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "RF3/RF10/RF46 — Conta, privacidade e configurações")
public class AccountController {
  public record ProfileRequest(@Size(max = 80) String displayName, @Size(max = 512) String avatarUrl, String locale, LocalDate birthDate) {}
  public record PasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 128) String newPassword, String currentRefreshToken) {}
  public record ConsentsRequest(Boolean marketingConsent, Map<String, Boolean> notifications, Map<String, Boolean> consents, String profileVisibility, String source) {}
  public record EmailChangeRequest(@NotBlank @Size(max = 254) String newEmail, @NotBlank String currentPassword) {}
  public record DeleteRequest(@NotBlank String currentPassword, String confirmation) {}
  /** RF10 do futebol americano (D-10): {@code quarterDurationMinutes} (1–15) e {@code playCallingLevel} no lugar do {@code matchSpeed}. */
  public record SettingsRequest(Integer quarterDurationMinutes, String playCallingLevel, String difficulty, String backgroundArt, String schemeArt,
      String soundtrack, String fontFamily, Boolean reducedMotion, Boolean notificationsEnabled, String theme, Boolean highContrast, String fontScale,
      String density, String preferredLocale, String regionalFormat, String simulationSpeed) {}

  private final AccountUseCases account; private final SettingsUseCases settings; private final PrivacyUseCases privacy;
  private final EmailChangeUseCases emailChange; private final DataExportUseCases exports; private final AccountDeletionUseCases deletion;
  private final ActivityUseCases activity; private final CurrentUser current; private final RefreshCookies cookies;

  public AccountController(AccountUseCases account, SettingsUseCases settings, PrivacyUseCases privacy, EmailChangeUseCases emailChange,
      DataExportUseCases exports, AccountDeletionUseCases deletion, ActivityUseCases activity, CurrentUser current, RefreshCookies cookies) {
    this.account = account; this.settings = settings; this.privacy = privacy; this.emailChange = emailChange; this.exports = exports;
    this.deletion = deletion; this.activity = activity; this.current = current; this.cookies = cookies;
  }

  // ---------------------------------------------------------------- Conta
  @GetMapping @Operation(summary = "RF3/RF46 — Minha conta") public AccountDto me() { return account.get(current.requireId()); }

  @PutMapping @Operation(summary = "RF46 CA02 — Corrigir dados pessoais (Art. 18, III)", description = "Nome de exibição, idioma e data de nascimento (só para idade mínima — Art. 6º, III). Auditoria SENSITIVE_DATA_CHANGED.")
  public AccountDto update(@Valid @RequestBody ProfileRequest r) {
    return account.updateProfile(current.requireId(), new AccountUseCases.UpdateProfileCommand(r.displayName(), r.avatarUrl(), r.locale(), r.birthDate()));
  }

  @PutMapping("/email") @Operation(summary = "RF46 CA02 — Trocar e-mail (verificação de posse, Art. 7º, V)", description = "Reautenticação por senha; link de verificação enviado ao novo endereço (24 h); aviso ao endereço antigo.")
  public ResponseEntity<Map<String, Object>> changeEmail(@Valid @RequestBody EmailChangeRequest r) {
    emailChange.request(current.requireId(), r.newEmail(), r.currentPassword());
    return ResponseEntity.accepted().body(Map.of("status", "VERIFICATION_SENT"));
  }

  @PutMapping("/password") @Operation(summary = "RF3/RF46 — Trocar senha", description = "HU-RF3 CA11/CA33: encerra as demais sessões, menos a de quem está trocando.")
  public ResponseEntity<Void> password(@Valid @RequestBody PasswordRequest r, HttpServletRequest http) {
    // a sessão que fica de pé é a do cookie HttpOnly (D-114); o corpo só serve a cliente que não é navegador
    String atual = sessaoAtual(http, r.currentRefreshToken());
    account.changePassword(current.requireId(), new AccountUseCases.ChangePasswordCommand(r.currentPassword(), r.newPassword(), atual));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/sessions") @Operation(summary = "RF3/RF46 — Sessões ativas")
  public List<SessionDto> sessions(@RequestHeader(value = "X-Refresh-Token", required = false) String refresh, HttpServletRequest http) {
    // marcar "esta sessão" na lista vem do cookie; o cabeçalho continua para cliente que não é navegador
    return account.listSessions(current.requireId(), sessaoAtual(http, refresh));
  }

  /** O refresh token da sessão de quem chamou: o cookie HttpOnly primeiro, depois o valor que o cliente mandou. */
  private String sessaoAtual(HttpServletRequest http, String informado) {
    String doCookie = cookies.doPedido(http);
    if (doCookie != null) return doCookie;
    return informado == null || informado.isBlank() ? null : informado;
  }
  @DeleteMapping("/sessions/{id}") @Operation(summary = "RF3/RF46 — Encerrar sessão") public ResponseEntity<Void> revoke(@PathVariable UUID id) { account.revokeSession(current.requireId(), id); return ResponseEntity.noContent().build(); }

  // ---------------------------------------------------------------- Privacidade
  @GetMapping("/consents") @Operation(summary = "RF46 CA03 — Privacidade: visibilidade e consentimentos por finalidade", description = "Sem registro ⇒ não consentido (privacidade por padrão). Inclui base legal, versão do texto e preferências de notificação.")
  public Map<String, Object> consents() {
    UUID id = current.requireId();
    PrivacyUseCases.PrivacyView v = privacy.view(id);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("profileVisibility", v.profileVisibility());
    out.put("consents", v.consents());
    out.put("notifications", account.notificationPreferences(id));
    out.put("marketingConsent", account.get(id).marketingConsent());
    return out;
  }

  @PutMapping("/consents") @Operation(summary = "RF46 CA03 — Conceder/revogar consentimentos e visibilidade", description = "Cada finalidade em separado (sem consentimento agrupado); revogar custa o mesmo que conceder (Art. 8º §5º); revogar BIOMETRIC_AVATAR apaga foto, textura e modelo 3D do Meu jogador.")
  public Map<String, Object> updateConsents(@RequestBody ConsentsRequest r) {
    UUID id = current.requireId();
    Map<String, Boolean> consents = r.consents() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(r.consents());
    if (r.marketingConsent() != null) consents.putIfAbsent("MARKETING_EMAIL", r.marketingConsent());
    privacy.update(id, new PrivacyUseCases.UpdateCommand(consents, r.profileVisibility(), r.source()));
    if (r.notifications() != null) account.updateConsents(id, new AccountUseCases.ConsentsCommand(null, r.notifications()));
    return consents();
  }

  @GetMapping("/consents/history") @Operation(summary = "RF46 CA03 — Histórico de consentimentos (prova do consentimento)")
  public List<PrivacyUseCases.ConsentHistoryEntry> consentHistory() { return privacy.history(current.requireId()); }

  // ---------------------------------------------------------------- Seus dados
  @PostMapping("/export") @Operation(summary = "RF46 CA07 — Solicitar exportação dos meus dados (Art. 18, II e V)", description = "Gera JSON completo + CSV resumido, disponíveis por 24 h. Auditoria DATA_EXPORT_REQUESTED.")
  public ResponseEntity<DataExportUseCases.ExportJobDto> requestExport() { return ResponseEntity.status(HttpStatus.ACCEPTED).body(exports.request(current.requireId())); }

  @GetMapping("/export") @Operation(summary = "RF3 — Exportar meus dados (JSON imediato, compatibilidade)")
  public Map<String, Object> export() { return account.export(current.requireId()); }

  @GetMapping("/export/jobs") @Operation(summary = "RF46 CA07 — Pedidos de exportação recentes")
  public List<DataExportUseCases.ExportJobDto> exportJobs() { return exports.list(current.requireId()); }

  @GetMapping("/export/{jobId}") @Operation(summary = "RF46 CA07 — Situação de um pedido de exportação")
  public DataExportUseCases.ExportJobDto exportJob(@PathVariable UUID jobId) { return exports.get(current.requireId(), jobId); }

  @GetMapping("/export/{jobId}/download") @Operation(summary = "RF46 CA07 — Baixar a exportação (json | csv)")
  public ResponseEntity<byte[]> download(@PathVariable UUID jobId, @RequestParam(defaultValue = "json") String format) {
    DataExportUseCases.Download d = exports.download(current.requireId(), jobId, format);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.fileName()).build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store").body(d.content());
  }

  @GetMapping("/activity") @Operation(summary = "RF46 CA07 — Histórico de atividades (Art. 9º)")
  public PageResponse<ActivityUseCases.ActivityEntry> activity(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return PageResponse.of(activity.list(current.requireId(), page, size), x -> x);
  }

  @DeleteMapping @Operation(summary = "RF46 CA07 — Excluir conta (Art. 18, VI)", description = "Dupla confirmação: senha + palavra EXCLUIR/DELETE/ELIMINAR. Prazo de arrependimento de 7 dias; depois, anonimização irreversível.")
  public ResponseEntity<AccountDeletionUseCases.DeletionStatus> delete(@Valid @RequestBody DeleteRequest r) {
    return ResponseEntity.accepted().body(deletion.request(current.requireId(), r.currentPassword(), r.confirmation()));
  }

  @GetMapping("/deletion") @Operation(summary = "RF46 CA07 — Situação da exclusão") public AccountDeletionUseCases.DeletionStatus deletionStatus() { return deletion.status(current.requireId()); }
  @PostMapping("/deletion/cancel") @Operation(summary = "RF46 CA07 — Cancelar exclusão dentro do prazo") public AccountDeletionUseCases.DeletionStatus cancelDeletion() { return deletion.cancel(current.requireId()); }

  // ---------------------------------------------------------------- Aparência / Idioma / RF10
  @GetMapping("/settings") @Operation(summary = "RF10/RF46 — Configurações, aparência e idioma") public SettingsDto settings() { return settings.get(current.requireId()); }

  @PutMapping("/settings") @Operation(summary = "RF10/RF46 CA04/CA05 — Atualizar configurações", description = "Duração do quarto na partida ao vivo (1–15 min), nível de chamada de jogada (KEY_MOMENTS | ALL_PLAYS | NONE), tema, alto contraste, fonte, reduzir movimento, densidade, idioma (preferredLocale: pt-BR | en-US | es-ES), formato regional, velocidade padrão da simulação (RF36).")
  public SettingsDto settings(@RequestBody SettingsRequest r) {
    return settings.update(current.requireId(), new SettingsUseCases.UpdateCommand(r.quarterDurationMinutes(), r.playCallingLevel(), r.difficulty(),
        r.backgroundArt(), r.schemeArt(), r.soundtrack(), r.fontFamily(), r.reducedMotion(), r.notificationsEnabled(), r.theme(), r.highContrast(),
        r.fontScale(), r.density(), r.preferredLocale(), r.regionalFormat(), r.simulationSpeed()));
  }
}
