package br.com.velion.scoresaf.infra.mysql.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.common.EncryptionKeyHolder;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** DATA_ENCRYPTION_KEY é a chave que cifra os dados pessoais; tamanho inválido derruba a subida. */
class EncryptionKeyInitializerTest {
  @AfterEach void reset() { EncryptionKeyHolder.clear(); }

  @Test
  void chave_configurada_e_aplicada_a_cifra() {
    byte[] raw = new byte[32];
    new SecureRandom().nextBytes(raw);
    new ApplicationContextRunner().withBean(EncryptionKeyInitializer.class)
        .withPropertyValues("scores.security.encryption.data-key=" + Base64.getEncoder().encodeToString(raw))
        .run(ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(EncryptionKeyHolder.isConfigured()).isTrue();
          assertThat(EncryptionKeyHolder.key().getEncoded()).isEqualTo(raw);
        });
  }

  @Test
  void chave_de_tamanho_invalido_derruba_a_subida() {
    new ApplicationContextRunner().withBean(EncryptionKeyInitializer.class)
        .withPropertyValues("scores.security.encryption.data-key=" + Base64.getEncoder().encodeToString(new byte[10]))
        .run(ctx -> assertThat(ctx).hasFailed());
  }
}
