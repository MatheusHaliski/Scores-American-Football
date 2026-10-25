package br.com.velion.scoresaf.application.common.exception;

/**
 * Base das exceções de negócio (mapeadas para RFC 7807 na camada web). O {@link #code()} é a chave de tradução
 * ({@code error.<CODE>} nos catálogos {@code i18n/messages_*.properties}, RF46 CA05); {@link #args()} são os parâmetros ICU/
 * MessageFormat. A mensagem em pt-BR é o fallback quando a chave não existe. {@link #details()} são dados estruturados para o
 * cliente (bloco 6a: versão atual no conflito do depth chart, lista de violações).
 */
public abstract class ScoresException extends RuntimeException {
  private final String code;
  private final transient Object[] args;
  /** Detalhes estruturados do erro (vão em {@code details} do corpo RFC 7807), ex.: versão atual no 409, violações no 422. */
  private final transient java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
  protected ScoresException(String code, String message) { this(code, message, new Object[0]); }
  protected ScoresException(String code, String message, Object... args) { super(message); this.code = code; this.args = args == null ? new Object[0] : args.clone(); }
  public String code() { return code; }
  public Object[] args() { return args.clone(); }
  public java.util.Map<String, Object> details() { return java.util.Collections.unmodifiableMap(details); }
  /** Acrescenta um detalhe estruturado; devolve a própria exceção para {@code throw e.withDetail(...)}. */
  public ScoresException withDetail(String key, Object value) { details.put(key, value); return this; }
}
