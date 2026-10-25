package br.com.velion.scoresaf.web.controller.account;

import br.com.velion.scoresaf.web.support.ClientIpResolver;
import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.usecase.rf01.RegisterUserUseCase;
import br.com.velion.scoresaf.application.usecase.rf02.ForgotPasswordUseCase;
import br.com.velion.scoresaf.application.usecase.rf02.LoginUseCase;
import br.com.velion.scoresaf.application.usecase.rf02.LogoutUseCase;
import br.com.velion.scoresaf.application.usecase.rf02.RefreshTokenUseCase;
import br.com.velion.scoresaf.application.usecase.rf02.ResetPasswordUseCase;
import br.com.velion.scoresaf.web.security.RefreshCookies;
import br.com.velion.scoresaf.web.security.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF1 / RF2 — cadastro, login, refresh rotativo, logout, recuperação de senha. Rotas públicas com rate-limit. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "RF1–RF2 — Autenticação")
public class AuthController {
  public record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8, max = 128) String password, @NotBlank @Size(max = 80) String displayName, String locale, boolean marketingConsent) {}
  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
  /** O refresh token vem do cookie HttpOnly (D-114); o corpo só existe para cliente que não é navegador. */
  public record RefreshRequest(String refreshToken) {}
  public record ForgotRequest(@NotBlank @Email String email) {}
  public record ResetRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 128) String newPassword) {}
  public record UserSummary(UUID id, String displayName, List<String> roles) {}
  /** {@code refreshToken} vem nulo quando o cookie o carrega (o padrão): o JavaScript nunca o vê. */
  public record TokenResponse(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt, UserSummary user) {}

  private final RegisterUserUseCase register; private final LoginUseCase login; private final RefreshTokenUseCase refresh; private final LogoutUseCase logout;
  private final ForgotPasswordUseCase forgot; private final ResetPasswordUseCase reset; private final TokenService tokens; private final String frontendUrl;
  private final RefreshCookies cookies; private final boolean refreshNoCorpo;

  private final ClientIpResolver ips;

  public AuthController(RegisterUserUseCase register, LoginUseCase login, RefreshTokenUseCase refresh, LogoutUseCase logout, ForgotPasswordUseCase forgot,
      ResetPasswordUseCase reset, TokenService tokens, @Value("${scores.app.cors-allowed-origins:http://localhost:3000}") String frontendUrl, ClientIpResolver ips,
      RefreshCookies cookies, @Value("${scores.auth.refresh-in-body:false}") boolean refreshNoCorpo) {
    this.register = register; this.login = login; this.refresh = refresh; this.logout = logout; this.forgot = forgot; this.reset = reset; this.tokens = tokens;
    this.frontendUrl = frontendUrl.split("\\s*,\\s*")[0]; this.ips = ips; this.cookies = cookies; this.refreshNoCorpo = refreshNoCorpo;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "RF1 — Cadastrar usuário", description = "HU-RF1: cria a conta (e-mail único, senha Argon2id ≥ 8) e registra o consentimento LGPD.")
  public Map<String, UUID> register(@Valid @RequestBody RegisterRequest r) {
    return Map.of("userId", register.execute(new RegisterUserUseCase.Command(r.email(), r.password(), r.displayName(), r.locale(), r.marketingConsent())).userId());
  }

  @PostMapping("/login")
  @Operation(summary = "RF2 — Autenticar", description = "HU-RF2: resposta uniforme em falha, bloqueio por tentativas, access JWT 15 min + refresh rotativo 7 dias em cookie HttpOnly.")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest r, HttpServletRequest http) {
    LoginUseCase.Result res = login.execute(new LoginUseCase.Command(r.email(), r.password(), ip(http), http.getHeader("User-Agent")));
    return comRefresh(res.userId(), res.displayName(), res.roles(), res.sessionFamily(), res.refreshToken(), res.refreshExpiresAt());
  }

  @PostMapping("/refresh")
  @Operation(summary = "RNF4 — Renovar sessão",
      description = "Refresh rotativo pelo cookie HttpOnly (com o cabeçalho X-Scores-Refresh); o token anterior é revogado e o reuso revoga a família inteira.")
  public ResponseEntity<TokenResponse> refresh(@RequestBody(required = false) RefreshRequest r, HttpServletRequest http) {
    String token = refreshTokenDoPedido(r, http);
    RefreshTokenUseCase.Result res = refresh.execute(token, ip(http), http.getHeader("User-Agent"));
    return comRefresh(res.userId(), res.displayName(), res.roles(), res.sessionFamily(), res.refreshToken(), res.refreshExpiresAt());
  }

  @PostMapping("/logout")
  @Operation(summary = "RF2 — Encerrar sessão",
      description = "HU-RF3 CA31: invalida o refresh token no servidor, apaga o cookie e manda Clear-Site-Data para o navegador limpar o que ficou.")
  public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest r, HttpServletRequest http) {
    String token = doCookie(http);
    if (token == null && r != null) token = vazioComoNulo(r.refreshToken());
    if (token != null) logout.execute(token);
    // OWASP Session Management: a invalidação que vale é a do servidor (acima); isto limpa o que o navegador guardou.
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.apaga().toString())
        .header("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"")
        .build();
  }

  /** Resposta com o access token no corpo e o refresh no cookie HttpOnly (D-114). */
  private ResponseEntity<TokenResponse> comRefresh(UUID userId, String displayName, List<String> roles, UUID family, String refreshToken, Instant refreshExpiresAt) {
    TokenService.IssuedToken access = tokens.issueAccessToken(userId, displayName, roles, family);
    ResponseCookie cookie = cookies.emite(refreshToken, refreshExpiresAt);
    TokenResponse corpo = new TokenResponse(access.value(), access.expiresAt(), refreshNoCorpo ? refreshToken : null, refreshExpiresAt,
        new UserSummary(userId, displayName, roles));
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(corpo);
  }

  /**
   * O refresh token do pedido: primeiro o cookie, que só vale com o cabeçalho próprio (CSRF, {@link RefreshCookies});
   * depois o corpo, para cliente que não é navegador. Sem nenhum dos dois, 401 — nunca um 500 por token nulo.
   */
  private String refreshTokenDoPedido(RefreshRequest r, HttpServletRequest http) {
    String doCookie = doCookie(http);
    if (doCookie != null) return doCookie;
    String doCorpo = r == null ? null : vazioComoNulo(r.refreshToken());
    if (doCorpo != null) return doCorpo;
    throw new AuthenticationFailedException("Sessão expirada; entre de novo.");
  }

  private String doCookie(HttpServletRequest http) {
    String valor = cookies.doPedido(http);
    if (valor == null) return null;
    if (!cookies.declarado(http)) {
      // cookie sem o cabeçalho = pedido de outro site; o cookie existe, mas não é usado.
      return null;
    }
    return valor;
  }

  private static String vazioComoNulo(String s) { return s == null || s.isBlank() ? null : s; }

  @PostMapping("/password/forgot")
  @Operation(summary = "RF2 — Esqueci minha senha", description = "HU-RF3 CA29: resposta uniforme (202) exista ou não a conta; link válido por 30 min.")
  public ResponseEntity<Map<String, String>> forgot(@Valid @RequestBody ForgotRequest r) {
    forgot.execute(r.email(), frontendUrl + "/auth/reset");
    return ResponseEntity.accepted().body(Map.of("message", "Se o e-mail existir, enviaremos as instruções de redefinição."));
  }

  @PostMapping("/password/reset")
  @Operation(summary = "RF2 — Redefinir senha", description = "HU-RF3 CA30/CA33: link inválido → 401; redefinição encerra todas as sessões.")
  public ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest r) { reset.execute(r.token(), r.newPassword()); return ResponseEntity.noContent().build(); }

  /** IP da sessão: o mesmo do limite e da auditoria (ClientIpResolver), nunca X-Forwarded-For. */
  private String ip(HttpServletRequest req) { return ips.resolve(req); }
}
