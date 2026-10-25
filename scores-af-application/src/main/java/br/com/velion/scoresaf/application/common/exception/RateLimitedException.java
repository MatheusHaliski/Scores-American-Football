package br.com.velion.scoresaf.application.common.exception;

public class RateLimitedException extends ScoresException {
  public RateLimitedException(String message) { super("RATE_LIMITED", message); }
}
