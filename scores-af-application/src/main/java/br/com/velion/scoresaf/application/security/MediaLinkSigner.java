package br.com.velion.scoresaf.application.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Links curtos assinados para mídia privada do titular (bloco E): {@code <img src>} não manda o Bearer, então a foto de
 * referência é aberta por um token de vida curta. Token = base64url({@code v1|recurso|dono|expira}) + "." +
 * base64url(HMAC-SHA256). Chave em {@code MEDIA_LINK_SECRET} (32+ caracteres; o perfil prod não sobe sem ela). Sem a
 * variável fora de produção, a chave é efêmera: os links deixam de valer ao reiniciar. O token nunca vai para log.
 */
@Component
public class MediaLinkSigner {
  public static final Duration DEFAULT_TTL = Duration.ofSeconds(120);
  public static final int MIN_SECRET_LENGTH = 32;
  private static final Logger log = LoggerFactory.getLogger(MediaLinkSigner.class);
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

  public record SignedLink(String token, Instant expiresAt) {}
  public record Claims(UUID subjectId, UUID ownerId, Instant expiresAt) {}

  private final byte[] key;
  private final Duration ttl;
  private final Clock clock;

  @Autowired
  public MediaLinkSigner(@Value("${scores.media.link-secret:}") String secret, @Value("${scores.media.link-ttl-seconds:120}") long ttlSeconds) {
    this(secret, Duration.ofSeconds(ttlSeconds), Clock.systemUTC());
  }

  public MediaLinkSigner(String secret, Duration ttl, Clock clock) {
    if (secret == null || secret.isBlank() || secret.length() < MIN_SECRET_LENGTH) {
      log.warn("MEDIA_LINK_SECRET {}: chave efêmera (links de mídia deixam de valer ao reiniciar). Em produção a subida falha.",
          secret == null || secret.isBlank() ? "ausente" : "com menos de " + MIN_SECRET_LENGTH + " caracteres");
      byte[] k = new byte[32];
      new SecureRandom().nextBytes(k);
      this.key = k;
    } else {
      this.key = secret.getBytes(StandardCharsets.UTF_8);
    }
    this.ttl = ttl;
    this.clock = clock;
  }

  public Duration ttl() { return ttl; }

  public SignedLink sign(UUID subjectId, UUID ownerId) {
    Instant exp = clock.instant().plus(ttl).truncatedTo(ChronoUnit.SECONDS);
    byte[] payload = ("v1|" + subjectId + "|" + ownerId + "|" + exp.getEpochSecond()).getBytes(StandardCharsets.UTF_8);
    return new SignedLink(B64.encodeToString(payload) + "." + B64.encodeToString(hmac(payload)), exp);
  }

  /** Vazio para token malformado, adulterado, expirado ou com prazo maior que o TTL (nunca diz qual). */
  public Optional<Claims> verify(String token) {
    if (token == null || token.length() > 512) return Optional.empty();
    int dot = token.indexOf('.');
    if (dot <= 0 || dot != token.lastIndexOf('.')) return Optional.empty();
    byte[] payload, sig;
    try {
      payload = Base64.getUrlDecoder().decode(token.substring(0, dot));
      sig = Base64.getUrlDecoder().decode(token.substring(dot + 1));
    } catch (IllegalArgumentException e) { return Optional.empty(); }
    if (!MessageDigest.isEqual(hmac(payload), sig)) return Optional.empty();
    String[] p = new String(payload, StandardCharsets.UTF_8).split("\\|");
    if (p.length != 4 || !"v1".equals(p[0])) return Optional.empty();
    try {
      Instant exp = Instant.ofEpochSecond(Long.parseLong(p[3]));
      Instant now = clock.instant();
      if (!exp.isAfter(now) || exp.isAfter(now.plus(ttl).plusSeconds(1))) return Optional.empty();
      return Optional.of(new Claims(UUID.fromString(p[1]), UUID.fromString(p[2]), exp));
    } catch (IllegalArgumentException e) { return Optional.empty(); }
  }

  private byte[] hmac(byte[] payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(payload);
    } catch (Exception e) {
      throw new IllegalStateException("HMAC indisponível", e);
    }
  }
}
