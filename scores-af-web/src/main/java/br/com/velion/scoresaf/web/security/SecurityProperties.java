package br.com.velion.scoresaf.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propriedades `scores.security.*` (ver application.yml e .env.example). */
@ConfigurationProperties(prefix = "scores.security")
public record SecurityProperties(Jwt jwt, Encryption encryption, Login login, RateLimit rateLimit) {
  public record Jwt(String issuer, String audience, int accessTtlMinutes, int refreshTtlDays, String privateKeyPem, String publicKeyPem) {
    public String audienceOrDefault() { return audience == null || audience.isBlank() ? "scores-api" : audience; }
  }
  public record Encryption(String dataKey) {}
  public record Login(int maxFailedAttempts, int lockMinutes) {}
  /** {@code limits}: troca o limite de uma regra do {@code AuthRateLimitFilter} pelo id (ex.: {@code login: 30}). */
  public record RateLimit(int analysisPerMinute, int storePerMinute, java.util.Map<String, Integer> limits) {}
}
