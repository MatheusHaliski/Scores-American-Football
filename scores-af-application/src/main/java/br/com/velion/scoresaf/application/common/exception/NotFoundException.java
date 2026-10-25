package br.com.velion.scoresaf.application.common.exception;

public class NotFoundException extends ScoresException {
  public NotFoundException(String resource, Object id) { super("NOT_FOUND", resource + " não encontrado: " + id); }
  public NotFoundException(String message) { super("NOT_FOUND", message); }
}
