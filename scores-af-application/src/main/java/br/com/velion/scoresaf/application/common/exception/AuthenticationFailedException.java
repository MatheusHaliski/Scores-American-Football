package br.com.velion.scoresaf.application.common.exception;

/** Credenciais inválidas ou conta bloqueada — 401 com resposta uniforme (não revela qual campo errou). */
public class AuthenticationFailedException extends ScoresException {
  public AuthenticationFailedException() { super("AUTH_FAILED", "Credenciais inválidas"); }
  public AuthenticationFailedException(String message) { super("AUTH_FAILED", message); }
}
