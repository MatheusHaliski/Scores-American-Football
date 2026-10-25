package br.com.velion.scoresaf.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Lê o par no formato do scripts/keys/generate_jwt_keys.sh (PKCS#8 + SPKI) e recusa chave fraca ou trocada. */
class RsaKeyProviderTest {
  private static KeyPair pair(int bits) throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(bits);
    return g.generateKeyPair();
  }

  private static String pem(String label, byte[] der) {
    return "-----BEGIN " + label + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der) + "\n-----END " + label + "-----\n";
  }

  @Test
  void le_par_pkcs8_e_spki() throws Exception {
    KeyPair kp = pair(3072);
    RsaKeyProvider p = new RsaKeyProvider(pem("PRIVATE KEY", kp.getPrivate().getEncoded()), pem("PUBLIC KEY", kp.getPublic().getEncoded()));
    assertThat(p.publicKey().getModulus().bitLength()).isEqualTo(3072);
  }

  @Test
  void recusa_chave_com_menos_de_2048_bits() throws Exception {
    KeyPair kp = pair(1024);
    assertThatThrownBy(() -> new RsaKeyProvider(pem("PRIVATE KEY", kp.getPrivate().getEncoded()), pem("PUBLIC KEY", kp.getPublic().getEncoded())))
        .hasMessageContaining("2048");
  }

  @Test
  void recusa_publica_de_outro_par() throws Exception {
    KeyPair a = pair(2048), b = pair(2048);
    assertThatThrownBy(() -> new RsaKeyProvider(pem("PRIVATE KEY", a.getPrivate().getEncoded()), pem("PUBLIC KEY", b.getPublic().getEncoded())))
        .hasMessageContaining("não corresponde");
  }
}
