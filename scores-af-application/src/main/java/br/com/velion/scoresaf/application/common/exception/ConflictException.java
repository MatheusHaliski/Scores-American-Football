package br.com.velion.scoresaf.application.common.exception;

/** Conflito de estado (recurso já existe, versão divergente, execução já ativa) — 409. */
public class ConflictException extends ScoresException {
  public ConflictException(String message) { super("CONFLICT", message); }
  public ConflictException(String code, String message, Object... args) { super(code, message, args); }
}
