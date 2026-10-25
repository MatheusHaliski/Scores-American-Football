package br.com.velion.scoresaf.domain.common.converters;

import br.com.velion.scoresaf.domain.common.EncryptionKeyHolder;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Cifra campos pessoais sensíveis (RNF LGPD: e-mail e nome de exibição) com AES-256-GCM.
 * Formato da coluna: Base64( IV[12 bytes] || ciphertext || tag[16 bytes] ). IV aleatório por escrita.
 * A chave vem de {@link EncryptionKeyHolder}.
 */
@Converter
public class AesGcmConverter implements AttributeConverter<String, String> {
  static final int IV_BYTES = 12;
  static final int TAG_BITS = 128;
  private static final SecureRandom RANDOM = new SecureRandom();

  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) return null;
    try {
      byte[] iv = new byte[IV_BYTES];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, EncryptionKeyHolder.key(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length + ct.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(ct, 0, out, iv.length, ct.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("falha ao cifrar atributo", e);
    }
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) return null;
    try {
      byte[] in = Base64.getDecoder().decode(dbData);
      if (in.length < IV_BYTES + TAG_BITS / 8) throw new IllegalArgumentException("payload cifrado curto demais");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, EncryptionKeyHolder.key(), new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
      byte[] pt = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
      return new String(pt, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("falha ao decifrar atributo (chave incorreta?)", e);
    }
  }
}
