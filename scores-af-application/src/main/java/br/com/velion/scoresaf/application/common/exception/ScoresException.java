package br.com.velion.scoresaf.application.common.exception;

/**
 * Base das exceções de negócio (mapeadas para RFC 7807 na camada web). O {@link #code()} é a chave de tradução
 * ({@code error.<CODE>} nos catálogos {@code i18n/messages_*.properties}, RF46 CA05); {@link #args()} são os parâmetros ICU/
 * MessageFormat. A mensagem em pt-BR é o fallback quando a chave não existe.
 */
public abstract class ScoresException extends RuntimeException {
  private final String code;
  private final transient Object[] args;
  protected ScoresException(String code, String message) { this(code, message, new Object[0]); }
  protected ScoresException(String code, String message, Object... args) { super(message); this.code = code; this.args = args == null ? new Object[0] : args.clone(); }
  public String code() { return code; }
  public Object[] args() { return args.clone(); }
}
