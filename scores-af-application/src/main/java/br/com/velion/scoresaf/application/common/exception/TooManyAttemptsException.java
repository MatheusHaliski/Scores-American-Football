package br.com.velion.scoresaf.application.common.exception;

/** Excesso de tentativas de senha numa reconferência (step-up): 429 TOO_MANY_ATTEMPTS com Retry-After. */
public class TooManyAttemptsException extends ScoresException {
  private final long retryAfterSeconds;

  public TooManyAttemptsException(long retryAfterSeconds) {
    super("TOO_MANY_ATTEMPTS", "Muitas tentativas de senha. Tente novamente mais tarde.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() { return retryAfterSeconds; }
}
