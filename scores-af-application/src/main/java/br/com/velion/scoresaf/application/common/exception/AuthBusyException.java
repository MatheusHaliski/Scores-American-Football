package br.com.velion.scoresaf.application.common.exception;

/** Teto de hashes de senha simultâneos atingido (bloco 3): a API responde 503 AUTH_BUSY com Retry-After, nunca 500. */
public class AuthBusyException extends ScoresException {
  public AuthBusyException() { super("AUTH_BUSY", "Muitas autenticações ao mesmo tempo. Tente novamente em instantes."); }
}
