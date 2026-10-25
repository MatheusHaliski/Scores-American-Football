package br.com.velion.scoresaf.web.error;

import br.com.velion.scoresaf.web.support.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Corpo de erro para quem responde fora dos controllers (filtros, Spring Security). Mesmo formato do
 * {@link ApiExceptionHandler}: RFC 7807 com {@code code}, {@code message}, {@code details} e {@code correlationId}.
 */
@Component
public class ErrorWriter {
  private final MessageSource messages;
  private final LocaleResolver locales;
  private final JsonMapper json;

  public ErrorWriter(MessageSource messages, LocaleResolver locales, JsonMapper json) { this.messages = messages; this.locales = locales; this.json = json; }

  public void write(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String code, Map<String, Object> details) throws IOException {
    Locale locale = locales.resolveLocale(req);
    String message = messages.getMessage("error." + code, null, code, locale);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "about:blank");
    body.put("title", messages.getMessage("problem.title." + status.value(), null, status.getReasonPhrase(), locale));
    body.put("status", status.value());
    body.put("detail", message);
    body.put("code", code);
    body.put("message", message);
    body.put("details", details == null ? Map.of() : details);
    body.put("correlationId", CorrelationIdFilter.current());
    body.put("locale", locale.toLanguageTag());
    res.setStatus(status.value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    res.setCharacterEncoding("UTF-8");
    res.getWriter().write(json.writeValueAsString(body));
  }
}
