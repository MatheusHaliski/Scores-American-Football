package br.com.velion.scoresaf.application.usecase.rf02;

/** Política de autenticação (bean configurado pela camada web a partir de {@code scores.security.*}). */
public record AuthPolicy(int maxFailedAttempts, int lockMinutes, int refreshTtlDays, int resetTokenMinutes) {
  public static AuthPolicy defaults() { return new AuthPolicy(5, 15, 7, 30); }
}
