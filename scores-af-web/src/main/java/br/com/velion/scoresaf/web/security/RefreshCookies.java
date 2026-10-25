package br.com.velion.scoresaf.web.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh token em cookie <b>HttpOnly</b> (D-114, docs/38 §5 L3). Antes ele voltava no corpo do login e o front o
 * guardava em <code>localStorage</code>: qualquer XSS levava a sessão inteira, contra o NIST SP 800-63B-4 §5
 * ("avoid insecure storage such as HTML5 Local Storage"). Agora o navegador guarda o cookie e o JavaScript nunca o lê.
 *
 * <p>Atributos: <code>HttpOnly</code>, <code>Secure</code>, <code>SameSite=Strict</code>, <code>Path=/</code> e o
 * prefixo <code>__Host-</code>, que o navegador só aceita com <code>Secure</code>, sem <code>Domain</code> e com
 * <code>Path=/</code> — é o que impede um subdomínio hostil de plantar o cookie. Fora de HTTPS (desenvolvimento local)
 * o prefixo seria recusado, então ali o nome é simples: <code>scores.auth.refresh-cookie-secure=false</code>.
 *
 * <p><b>CSRF.</b> <code>SameSite</code> não substitui defesa de CSRF (OWASP). O cookie só é aceito em requisição que
 * traz o cabeçalho {@link #HEADER_PEDIDO}: um formulário de outro site não consegue acrescentá-lo, e a requisição com
 * cabeçalho próprio passa antes pelo CORS, que só libera as origens da allowlist.
 */
@Component
public class RefreshCookies {
  /** Cabeçalho que a página do Scores manda no refresh e no logout; é a defesa de CSRF do cookie. */
  public static final String HEADER_PEDIDO = "X-Scores-Refresh";

  // nome próprio do futebol americano (D-13): em dev os dois jogos rodam em localhost, e cookie não separa por porta
  private static final String NOME_SEGURO = "__Host-scoresaf_rt";
  private static final String NOME_LOCAL = "scoresaf_rt";

  private final boolean seguro;

  public RefreshCookies(@Value("${scores.auth.refresh-cookie-secure:true}") boolean seguro) {
    this.seguro = seguro;
  }

  public String nome() {
    return seguro ? NOME_SEGURO : NOME_LOCAL;
  }

  /** Cookie com o refresh token, vivo até o vencimento dele. */
  public ResponseCookie emite(String valor, Instant expiraEm) {
    Duration vida = Duration.between(Instant.now(), expiraEm);
    if (vida.isNegative()) vida = Duration.ZERO;
    return base(valor).maxAge(vida).build();
  }

  /** Cookie vazio e vencido: o logout apaga o que o navegador guardava. */
  public ResponseCookie apaga() {
    return base("").maxAge(0).build();
  }

  /** O refresh token que veio no cookie, ou {@code null}. */
  public String doPedido(HttpServletRequest req) {
    Cookie[] cookies = req.getCookies();
    if (cookies == null) return null;
    for (Cookie c : cookies) {
      if (nome().equals(c.getName())) {
        String valor = c.getValue();
        return valor == null || valor.isBlank() ? null : valor;
      }
    }
    return null;
  }

  /** O pedido declarou o cabeçalho próprio (defesa de CSRF). */
  public boolean declarado(HttpServletRequest req) {
    return req.getHeader(HEADER_PEDIDO) != null;
  }

  private ResponseCookie.ResponseCookieBuilder base(String valor) {
    return ResponseCookie.from(nome(), valor).httpOnly(true).secure(seguro).sameSite("Strict").path("/");
  }
}
