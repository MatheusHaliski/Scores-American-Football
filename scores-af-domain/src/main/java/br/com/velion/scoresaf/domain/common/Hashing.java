package br.com.velion.scoresaf.domain.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Hashes utilitários sem segredo (busca por e-mail, refresh token). Senhas usam Argon2id na camada web. */
public final class Hashing {
  private Hashing() {}

  /** SHA-256 em hexadecimal minúsculo (64 chars). */
  public static String sha256Hex(String value) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(d);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Normaliza e-mail (trim + minúsculas) antes de cifrar/hashear, para que a busca seja estável. */
  public static String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  /** Hash de busca do e-mail (coluna única {@code user_account.email_hash}). */
  public static String emailHash(String email) {
    return sha256Hex(normalizeEmail(email));
  }
}
