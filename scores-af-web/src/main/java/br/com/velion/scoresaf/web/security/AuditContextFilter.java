package br.com.velion.scoresaf.web.security;

import br.com.velion.scoresaf.domain.common.AuditContext;
import br.com.velion.scoresaf.web.support.ClientIpResolver;
import br.com.velion.scoresaf.web.support.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propaga ator (JWT subject) e correlation-id ({@link CorrelationIdFilter}) para AuditContext/MDC, usados pelos metadados
 * das entidades e pelo AuditService.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AuditContextFilter extends OncePerRequestFilter {
  private final ClientIpResolver ips;

  public AuditContextFilter(ClientIpResolver ips) { this.ips = ips; }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String cid = CorrelationIdFilter.current();
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String actor = (auth != null && auth.getPrincipal() instanceof Jwt jwt) ? jwt.getSubject() : "anonymous";
    AuditContext.setActor(actor);
    AuditContext.setCorrelationId(cid);
    AuditContext.setRequest(ips.resolve(req), req.getHeader("User-Agent")); // nunca X-Forwarded-For
    MDC.put("actor", actor);
    try { chain.doFilter(req, res); }
    finally { AuditContext.clear(); MDC.remove("actor"); }
  }
}
