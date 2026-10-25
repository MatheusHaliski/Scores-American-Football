package br.com.velion.scoresaf.application.common.exception;

/** Recurso pertence a outro usuário (RNF3 / HU-RF3 CA14) — vira 403 e evento de auditoria. */
public class ForbiddenException extends ScoresException {
  public ForbiddenException(String message) { super("FORBIDDEN", message); }
}
