package br.com.velion.scoresaf.web.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Par RSA para assinar JWT (RS256): lido de PEM (PKCS#8 + SPKI, como gera {@code scripts/keys/generate_jwt_keys.sh}) ou gerado
 * efêmero fora de produção (aviso em log; o perfil prod recusa subir sem o par). Mínimo de 2048 bits.
 */
public final class RsaKeyProvider {
  private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);
  private final RSAPublicKey publicKey;
  private final RSAPrivateKey privateKey;
  static final int MIN_BITS = 2048;

  public RsaKeyProvider(String privatePem, String publicPem) {
    try {
      if (privatePem != null && !privatePem.isBlank() && publicPem != null && !publicPem.isBlank()) {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        privateKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(decode(privatePem, "PRIVATE KEY")));
        publicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(decode(publicPem, "PUBLIC KEY")));
        if (publicKey.getModulus().bitLength() < MIN_BITS) throw new IllegalStateException("JWT: chave RSA com menos de " + MIN_BITS + " bits");
        if (!publicKey.getModulus().equals(privateKey.getModulus())) throw new IllegalStateException("JWT: chave pública não corresponde à privada");
      } else {
        log.warn("JWT: nenhum par RSA configurado — gerando par EFÊMERO (tokens invalidam ao reiniciar). Não use em produção.");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        publicKey = (RSAPublicKey) kp.getPublic();
        privateKey = (RSAPrivateKey) kp.getPrivate();
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Não foi possível carregar/gerar o par RSA do JWT", e);
    }
  }

  private static byte[] decode(String pem, String label) {
    String body = pem.replace("-----BEGIN " + label + "-----", "").replace("-----END " + label + "-----", "")
        .replace("-----BEGIN RSA " + label + "-----", "").replace("-----END RSA " + label + "-----", "")
        .replaceAll("\\s", "");
    return Base64.getDecoder().decode(body);
  }

  public RSAPublicKey publicKey() { return publicKey; }
  public RSAPrivateKey privateKey() { return privateKey; }
}
