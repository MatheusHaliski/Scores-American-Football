package br.com.velion.scoresaf.domain.common;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Chave AES-256 usada por {@link br.com.velion.scoresaf.domain.common.converters.AesGcmConverter} (RNF: dados
 * pessoais cifrados em repouso — e-mail e nome do {@code UserAccount}).
 *
 * <p>Estático e sem Spring: a infraestrutura ({@code EncryptionKeyInitializer}) lê
 * {@code scores.security.encryption.data-key} (Base64 de 32 bytes) e chama {@link #setKeyBase64(String)} na subida.
 * Se nenhuma chave for configurada, uma chave <b>efêmera aleatória</b> é gerada na execução e um aviso é registrado no log:
 * o que for cifrado com ela fica ilegível depois de reiniciar (serve ao H2 em memória de dev e testes). Nenhuma chave fixa
 * existe no código (D-11); o perfil prod não sobe sem {@code DATA_ENCRYPTION_KEY} ({@code ProductionConfigGuard}).
 */
public final class EncryptionKeyHolder {
  private static final System.Logger LOG = System.getLogger(EncryptionKeyHolder.class.getName());
  private static volatile SecretKey key;
  private static volatile boolean configured;
  private static volatile boolean warned;

  private EncryptionKeyHolder() {}

  /** Define a chave (16, 24 ou 32 bytes). */
  public static synchronized void setKey(byte[] raw) {
    if (raw == null || (raw.length != 16 && raw.length != 24 && raw.length != 32)) {
      throw new IllegalArgumentException("chave AES deve ter 16, 24 ou 32 bytes");
    }
    key = new SecretKeySpec(raw, "AES");
    configured = true;
  }

  /** Define a chave a partir de Base64 (padrão ou URL-safe). Valor vazio/nulo é ignorado (fica a chave de dev). */
  public static void setKeyBase64(String base64) {
    if (base64 == null || base64.isBlank()) return;
    String v = base64.trim();
    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(v);
    } catch (IllegalArgumentException e) {
      raw = Base64.getUrlDecoder().decode(v);
    }
    setKey(raw);
  }

  /** Chave ativa; se não configurada, gera uma chave efêmera aleatória (com aviso único). */
  public static SecretKey key() {
    SecretKey k = key;
    if (k == null) {
      synchronized (EncryptionKeyHolder.class) {
        if (key == null) {
          byte[] raw = new byte[32];
          new SecureRandom().nextBytes(raw);
          key = new SecretKeySpec(raw, "AES");
          if (!warned) {
            warned = true;
            LOG.log(System.Logger.Level.WARNING,
                "scores.security.encryption.data-key não configurada: usando chave AES EFÊMERA (dados cifrados ficam ilegíveis ao reiniciar). Não use em produção.");
          }
        }
        k = key;
      }
    }
    return k;
  }

  public static boolean isConfigured() { return configured; }

  /** Volta ao estado inicial (uso em testes). */
  public static synchronized void clear() { key = null; configured = false; }

}
