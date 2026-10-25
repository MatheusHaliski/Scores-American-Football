package br.com.velion.scoresaf.application.common.exception;

/** Regra de negócio violada (ex.: elenco incompleto, caixa insuficiente) — 422. */
public class BusinessRuleException extends ScoresException {
  public BusinessRuleException(String code, String message) { super(code, message); }
  public BusinessRuleException(String code, String message, Object... args) { super(code, message, args); }
  public BusinessRuleException(String message) { super("BUSINESS_RULE", message); }
}
