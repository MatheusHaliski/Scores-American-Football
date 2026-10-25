package br.com.velion.scoresaf.application.common.exception;

/**
 * Valor de entrada fora do domínio aceito, conferido no caso de uso (ex.: duração do quarto fora de 1–15) — 400 com o
 * {@code code} próprio, para o front mostrar a mensagem certa. Regra de negócio sobre um valor válido continua 422
 * ({@link BusinessRuleException}).
 */
public class InvalidInputException extends ScoresException {
  public InvalidInputException(String code, String message, Object... args) { super(code, message, args); }
}
