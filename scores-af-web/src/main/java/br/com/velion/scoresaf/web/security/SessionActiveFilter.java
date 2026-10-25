package br.com.velion.scoresaf.web.security;

import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.web.error.ErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Recusa access token de família de sessão revogada (D-25). Depois do logout, da troca de senha (as outras sessões) ou de
 * um reuso de refresh detectado, o token deixa de valer na hora, sem esperar os 15 min do TTL. Uma consulta indexada por
 * pedido autenticado ({@code ix_user_session_family}).
 */
@Component
public class SessionActiveFilter extends OncePerRequestFilter {
  private final UserSessionRepository sessions;
  private final ErrorWriter errors;

  public SessionActiveFilter(UserSessionRepository sessions, ErrorWriter errors) { this.sessions = sessions; this.errors = errors; }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwt && !active(jwt.getToken().getClaimAsString("sid"))) {
      SecurityContextHolder.clearContext();
      res.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"session revoked\"");
      errors.write(req, res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Entre na sua conta para continuar.", null);
      return;
    }
    chain.doFilter(req, res);
  }

  private boolean active(String sid) {
    try {
      return sid != null && sessions.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(UUID.fromString(sid), Instant.now());
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
