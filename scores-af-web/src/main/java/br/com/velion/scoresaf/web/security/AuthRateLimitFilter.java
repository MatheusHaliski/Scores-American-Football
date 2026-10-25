package br.com.velion.scoresaf.web.security;

import br.com.velion.scoresaf.application.port.out.cache.RateLimiterPort;
import br.com.velion.scoresaf.web.error.ErrorWriter;
import br.com.velion.scoresaf.web.support.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite por IP + método + rota, antes do Spring Security (bloco 3). O IP vem do {@link ClientIpResolver} (nunca de
 * {@code X-Forwarded-For}; IPv6 pelo /64). As rotas que calculam hash de senha têm limites mais baixos; o teto de hashes
 * simultâneos fica no {@code PasswordHasher}. Limites podem ser trocados por {@code scores.security.rate-limit.limits.<regra>}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

  /** Uma regra: quem casa, quantos pedidos por janela e se a rota calcula hash de senha. */
  public record Rule(String id, Predicate<HttpServletRequest> matches, int defaultLimit, Duration window, boolean computesHash) {}

  static List<Rule> rules(SecurityProperties.RateLimit cfg) {
    return List.of(
        route("login", "POST", "/api/v1/auth/login", 30, Duration.ofMinutes(10), true),
        route("register", "POST", "/api/v1/auth/register", 10, Duration.ofHours(1), true),
        route("refresh", "POST", "/api/v1/auth/refresh", 120, Duration.ofMinutes(10), false),
        route("password-forgot", "POST", "/api/v1/auth/password/forgot", 10, Duration.ofHours(1), false),
        route("password-reset", "POST", "/api/v1/auth/password/reset", 20, Duration.ofHours(1), true),
        route("email-verify", "POST", "/api/v1/auth/email/verify", 40, Duration.ofHours(1), false),
        route("password-change", "PUT", "/api/v1/me/password", 20, Duration.ofHours(1), true),
        route("email-change", "PUT", "/api/v1/me/email", 30, Duration.ofHours(1), true),
        route("account-deletion", "DELETE", "/api/v1/me", 10, Duration.ofHours(1), true),
        // as regras do jogo seguem os limites do basquete: o analista do sistema (bloco 6c, GET /analysis e POST /analysis/refresh,
        // D-104) e a loja, que ainda vai chegar (upload e links do Meu jogador no bloco 9)
        new Rule("analysis", r -> r.getRequestURI().contains("/analysis"), cfg == null ? 6 : cfg.analysisPerMinute(), Duration.ofMinutes(1), false),
        new Rule("store", r -> r.getRequestURI().startsWith("/api/v1/store/"), cfg == null ? 20 : cfg.storePerMinute(), Duration.ofMinutes(1), false));
  }

  private static Rule route(String id, String method, String path, int limit, Duration window, boolean hash) {
    return new Rule(id, r -> method.equalsIgnoreCase(r.getMethod()) && path.equals(r.getRequestURI()), limit, window, hash);
  }

  private final RateLimiterPort limiter;
  private final ClientIpResolver ips;
  private final ErrorWriter errors;
  private final List<Rule> rules;
  private final Map<String, Integer> overrides;

  public AuthRateLimitFilter(RateLimiterPort limiter, ClientIpResolver ips, ErrorWriter errors, SecurityProperties props) {
    this.limiter = limiter; this.ips = ips; this.errors = errors;
    this.rules = rules(props.rateLimit());
    this.overrides = props.rateLimit() == null || props.rateLimit().limits() == null ? Map.of() : props.rateLimit().limits();
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    for (Rule rule : rules) {
      if (!rule.matches().test(req)) continue;
      int limit = overrides.getOrDefault(rule.id(), rule.defaultLimit());
      if (!limiter.tryAcquire("rl:" + rule.id() + ":" + ips.bucketKey(req), limit, rule.window())) {
        long retry = rule.window().toSeconds();
        res.setHeader("Retry-After", String.valueOf(retry));
        errors.write(req, res, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Muitas requisições. Tente novamente em instantes.",
            Map.of("retryAfterSeconds", retry, "rule", rule.id()));
        return;
      }
      break;
    }
    chain.doFilter(req, res);
  }
}
