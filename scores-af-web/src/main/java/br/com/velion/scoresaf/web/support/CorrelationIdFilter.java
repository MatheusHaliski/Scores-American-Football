package br.com.velion.scoresaf.web.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Primeiro filtro da cadeia: todo pedido tem um {@code correlationId} no MDC e no cabeçalho da resposta, inclusive os
 * recusados pelo Spring Security. O valor do cliente só é aceito se for curto e sem caracteres de controle (evita injeção
 * no log); senão, um novo é gerado.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";
  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String incoming = req.getHeader(HEADER);
    String cid = incoming != null && SAFE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    MDC.put(MDC_KEY, cid);
    res.setHeader(HEADER, cid);
    try { chain.doFilter(req, res); }
    finally { MDC.remove(MDC_KEY); }
  }

  public static String current() { return MDC.get(MDC_KEY); }
}
