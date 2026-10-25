package br.com.velion.scoresaf.web.error;

import br.com.velion.scoresaf.application.common.exception.AuthBusyException;
import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.ConsentRequiredException;
import br.com.velion.scoresaf.application.common.exception.ForbiddenException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.common.exception.RateLimitedException;
import br.com.velion.scoresaf.application.common.exception.ScoresException;
import br.com.velion.scoresaf.application.common.exception.TooManyAttemptsException;
import br.com.velion.scoresaf.application.common.exception.UnsupportedMediaException;
import br.com.velion.scoresaf.web.support.CorrelationIdFilter;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Erros em RFC 7807 (application/problem+json) com {@code code} estável, {@code message}, {@code details}, {@code correlationId},
 * {@code locale} e textos <b>traduzidos</b> para o idioma da requisição (RF46 CA05, RNF13): título por
 * {@code problem.title.<status>}, detalhe por {@code error.<code>} com os parâmetros da exceção; sem chave, cai na mensagem pt-BR
 * da exceção. Nunca vaza stack trace. Fusão do handler do basquete (exceções dos casos de uso) com o do esqueleto (rota e método
 * inexistentes viram 404 no mesmo corpo); o contrato do corpo é o do esqueleto (ErrorAndActuatorContractTest).
 */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private final MessageSource messages;

  public ApiExceptionHandler(MessageSource messages) { this.messages = messages; }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail noRoute(NoResourceFoundException e) { return base(HttpStatus.NOT_FOUND, "NOT_FOUND", text("error.NOT_FOUND", null, "Recurso não encontrado")); }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ProblemDetail method(HttpRequestMethodNotSupportedException e) { return base(HttpStatus.NOT_FOUND, "NOT_FOUND", text("error.NOT_FOUND", null, "Recurso não encontrado")); }

  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail notFound(NotFoundException e) { return problem(HttpStatus.NOT_FOUND, e, e.getMessage()); }

  @ExceptionHandler(ConsentRequiredException.class)
  public ProblemDetail consent(ConsentRequiredException e) {
    ProblemDetail p = problem(HttpStatus.FORBIDDEN, e, e.getMessage());
    detail(p, "purpose", e.purpose().name());
    detail(p, "legalBasis", e.purpose().legalBasis());
    return p;
  }

  @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
  public ProblemDetail forbidden(Exception e) { return base(HttpStatus.FORBIDDEN, "FORBIDDEN", text("error.FORBIDDEN", null, "Você não tem acesso a este recurso")); }

  @ExceptionHandler(AuthenticationFailedException.class)
  public ProblemDetail authFailed(AuthenticationFailedException e) { return problem(HttpStatus.UNAUTHORIZED, e, e.getMessage()); }

  @ExceptionHandler(ConflictException.class)
  public ProblemDetail conflict(ConflictException e) { return problem(HttpStatus.CONFLICT, e, e.getMessage()); }

  /**
   * RNF1 CA3 — duas escritas simultâneas no mesmo agregado (ex.: a mesma carreira em duas abas): o JPA recusa a segunda
   * pela versão otimista. Nada dela foi gravado; o cliente relê o estado e tenta de novo. Antes caía no 500 genérico.
   */
  @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class,
      org.springframework.dao.PessimisticLockingFailureException.class, jakarta.persistence.PessimisticLockException.class,
      jakarta.persistence.LockTimeoutException.class})
  public ProblemDetail concurrentUpdate(Exception e) {
    log.info("Escrita concorrente recusada [{}]: {}", MDC.get("correlationId"), e.getClass().getSimpleName());
    return base(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", text("error.CONCURRENT_UPDATE", null, "Estes dados foram alterados em outra sessão. Atualize e tente de novo."));
  }

  /**
   * Chave única repetida (D-91): a corrida que a trava da escrita não cobriu (ex.: duas transações gravando o resultado do mesmo jogo)
   * — a segunda perdeu, nada dela ficou gravado, e o cliente relê o estado, como no {@code CONCURRENT_UPDATE} da versão otimista.
   * As outras violações de integridade (chave estrangeira, não nulo) são defeito, não corrida: seguem para o 500.
   */
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ProblemDetail integrity(org.springframework.dao.DataIntegrityViolationException e) {
    return duplicateKey(e) ? concurrentUpdate(e) : unexpected(e);
  }

  /** SQLState 23505 (padrão: H2, PostgreSQL) ou erro 1062/1586 do MySQL ({@code ER_DUP_ENTRY}) em qualquer causa, inclusive no lote. */
  static boolean duplicateKey(Throwable e) {
    java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    java.util.Deque<Throwable> todo = new java.util.ArrayDeque<>();
    todo.add(e);
    while (!todo.isEmpty()) {
      Throwable t = todo.poll();
      if (!seen.add(t)) continue;
      if (t instanceof org.springframework.dao.DuplicateKeyException) return true;
      if (t instanceof java.sql.SQLException s) {
        if ("23505".equals(s.getSQLState()) || s.getErrorCode() == 1062 || s.getErrorCode() == 1586) return true;
        if (s.getNextException() != null) todo.add(s.getNextException());
      }
      if (t.getCause() != null) todo.add(t.getCause());
    }
    return false;
  }

  /** Valor fora do domínio conferido no caso de uso: 400 com o código próprio (ex.: INVALID_QUARTER_DURATION). */
  @ExceptionHandler(InvalidInputException.class)
  public ProblemDetail invalidInput(InvalidInputException e) { return problem(HttpStatus.BAD_REQUEST, e, e.getMessage()); }

  @ExceptionHandler(BusinessRuleException.class)
  public ProblemDetail business(BusinessRuleException e) { return problem(HttpStatus.UNPROCESSABLE_ENTITY, e, e.getMessage()); }

  @ExceptionHandler(UnsupportedMediaException.class)
  public ProblemDetail media(UnsupportedMediaException e) { return problem(e.tooLarge() ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.UNSUPPORTED_MEDIA_TYPE, e, e.getMessage()); }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail uploadTooLarge(MaxUploadSizeExceededException e) {
    return base(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", text("error.UPLOAD_TOO_LARGE", null, "Arquivo acima do limite permitido"));
  }

  /** Reconferência de senha acima do limite (bloco 3): uma sessão roubada não testa senhas sem limite. */
  @ExceptionHandler(TooManyAttemptsException.class)
  public org.springframework.http.ResponseEntity<ProblemDetail> tooManyAttempts(TooManyAttemptsException e) {
    ProblemDetail p = problem(HttpStatus.TOO_MANY_REQUESTS, e, e.getMessage());
    detail(p, "retryAfterSeconds", e.retryAfterSeconds());
    return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", String.valueOf(e.retryAfterSeconds())).body(p);
  }

  /** Teto de hashes de senha simultâneos (bloco 3): 503 com Retry-After em vez de esgotar o pool e responder 500. */
  @ExceptionHandler(AuthBusyException.class)
  public org.springframework.http.ResponseEntity<ProblemDetail> authBusy(AuthBusyException e) {
    ProblemDetail p = problem(HttpStatus.SERVICE_UNAVAILABLE, e, e.getMessage());
    detail(p, "retryAfterSeconds", 2);
    return org.springframework.http.ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "2").body(p);
  }

  @ExceptionHandler(RateLimitedException.class)
  public ProblemDetail rateLimited(RateLimitedException e) { return problem(HttpStatus.TOO_MANY_REQUESTS, e, e.getMessage()); }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail validation(MethodArgumentNotValidException e) {
    ProblemDetail p = base(HttpStatus.BAD_REQUEST, "VALIDATION", text("error.VALIDATION", null, "Dados inválidos"));
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
    detail(p, "fields", fields);
    return p;
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail constraint(ConstraintViolationException e) { return base(HttpStatus.BAD_REQUEST, "VALIDATION", text("error.VALIDATION", null, "Dados inválidos")); }

  /** Corpo ilegível (JSON malformado, tipo errado): 400 no corpo padrão, nunca 500 nem a mensagem do parser. */
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ProblemDetail unreadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
    return base(HttpStatus.BAD_REQUEST, "VALIDATION", text("error.VALIDATION", null, "Dados inválidos"));
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail unexpected(Exception e) {
    log.error("Erro inesperado [{}]", MDC.get("correlationId"), e);
    return base(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", text("error.INTERNAL", null, "Erro interno. Informe o correlationId ao suporte."));
  }

  /** Códigos genéricos: em pt-BR a mensagem específica da exceção é mais útil que o texto genérico do catálogo. */
  static final java.util.Set<String> GENERIC_CODES = java.util.Set.of("NOT_FOUND", "BUSINESS_RULE", "CONFLICT", "FORBIDDEN", "AUTH_FAILED");

  private ProblemDetail problem(HttpStatus status, ScoresException e, String fallback) {
    boolean pt = "pt".equals(LocaleContextHolder.getLocale().getLanguage());
    ProblemDetail p = pt && GENERIC_CODES.contains(e.code()) && fallback != null ? base(status, e.code(), fallback)
        : base(status, e.code(), text("error." + e.code(), e.args(), fallback));
    e.details().forEach((k, v) -> detail(p, k, v));
    return p;
  }

  private ProblemDetail base(HttpStatus status, String code, String detail) {
    Locale locale = LocaleContextHolder.getLocale();
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setTitle(messages.getMessage("problem.title." + status.value(), null, status.getReasonPhrase(), locale));
    p.setProperty("code", code);
    p.setProperty("message", detail);
    p.setProperty("details", new LinkedHashMap<String, Object>());
    p.setProperty("correlationId", CorrelationIdFilter.current());
    p.setProperty("locale", locale.toLanguageTag());
    return p;
  }

  /** Detalhe estruturado: vai em {@code details} e, por compatibilidade com o front atual, também no topo do corpo. */
  @SuppressWarnings("unchecked")
  private static void detail(ProblemDetail p, String key, Object value) {
    p.setProperty(key, value);
    ((Map<String, Object>) p.getProperties().get("details")).put(key, value);
  }

  private String text(String key, Object[] args, String fallback) {
    Object[] a = args == null || args.length == 0 ? null : args;
    return messages.getMessage(key, a, fallback, LocaleContextHolder.getLocale());
  }
}
