package br.com.velion.scoresaf.application.usecase.rf02;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Tokens opacos (refresh, reset): 32 bytes aleatórios em Base64URL; só o SHA-256 vai ao banco/cache. */
public final class TokenHashing {
  private static final SecureRandom RANDOM = new SecureRandom();
  private TokenHashing() {}
  public static String newToken() { byte[] b = new byte[32]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  public static String sha256Hex(String value) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(64); for (byte x : d) sb.append(String.format("%02x", x)); return sb.toString();
    } catch (Exception e) { throw new IllegalStateException(e); }
  }
}
