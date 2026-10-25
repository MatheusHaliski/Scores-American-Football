package br.com.velion.scoresaf.web.error;

import br.com.velion.scoresaf.web.support.CorrelationIdFilter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Erros em RFC 7807 ({@code application/problem+json}) com {@code code} estável, {@code message}, {@code details},
 * {@code correlationId} e textos traduzidos para o idioma da requisição ({@code problem.title.<status>},
 * {@code error.<code>}). Nunca vaza stack trace. Os casos de uso do domínio ganham handlers próprios no bloco 3.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private final MessageSource messages;

  public ApiExceptionHandler(MessageSource messages) { this.messages = messages; }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail notFound(NoResourceFoundException e) { return base(HttpStatus.NOT_FOUND, "NOT_FOUND"); }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ProblemDetail method(HttpRequestMethodNotSupportedException e) { return base(HttpStatus.NOT_FOUND, "NOT_FOUND"); }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail forbidden(AccessDeniedException e) { return base(HttpStatus.FORBIDDEN, "FORBIDDEN"); }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail validation(MethodArgumentNotValidException e) {
    ProblemDetail p = base(HttpStatus.BAD_REQUEST, "VALIDATION");
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
    details(p).put("fields", fields);
    return p;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail unexpected(Exception e) {
    log.error("Erro inesperado [{}]", CorrelationIdFilter.current(), e);
    return base(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL");
  }

  private ProblemDetail base(HttpStatus status, String code) {
    Locale locale = LocaleContextHolder.getLocale();
    String detail = messages.getMessage("error." + code, null, code, locale);
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setTitle(messages.getMessage("problem.title." + status.value(), null, status.getReasonPhrase(), locale));
    p.setProperty("code", code);
    p.setProperty("message", detail);
    p.setProperty("details", new LinkedHashMap<String, Object>());
    p.setProperty("correlationId", CorrelationIdFilter.current());
    p.setProperty("locale", locale.toLanguageTag());
    return p;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> details(ProblemDetail p) { return (Map<String, Object>) p.getProperties().get("details"); }
}
