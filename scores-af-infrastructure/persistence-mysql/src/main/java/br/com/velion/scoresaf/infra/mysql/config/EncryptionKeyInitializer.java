package br.com.velion.scoresaf.infra.mysql.config;

import br.com.velion.scoresaf.domain.common.EncryptionKeyHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Lê {@code scores.security.encryption.data-key} (AES-256, Base64 de 32 bytes) e a entrega ao
 * {@link EncryptionKeyHolder} antes de qualquer acesso a entidades cifradas (RNF LGPD).
 * Vazio ⇒ chave efêmera gerada na subida (aviso no log; o perfil prod não sobe sem a chave).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EncryptionKeyInitializer implements InitializingBean {
  private static final Logger log = LoggerFactory.getLogger(EncryptionKeyInitializer.class);

  private final String dataKey;

  public EncryptionKeyInitializer(@Value("${scores.security.encryption.data-key:}") String dataKey) {
    this.dataKey = dataKey;
  }

  @Override
  public void afterPropertiesSet() {
    if (dataKey == null || dataKey.isBlank()) {
      log.warn("DATA_ENCRYPTION_KEY ausente — usando chave AES EFÊMERA: o que for cifrado fica ilegível ao reiniciar (não use em produção)");
      EncryptionKeyHolder.key();
      return;
    }
    EncryptionKeyHolder.setKeyBase64(dataKey);
    log.info("Chave de cifra de dados pessoais configurada (AES-GCM)");
  }
}
