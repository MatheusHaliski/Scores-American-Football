package br.com.velion.scoresaf.domain.common.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.velion.scoresaf.domain.common.EncryptionKeyHolder;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AesGcmConverterTest {
  private final AesGcmConverter converter = new AesGcmConverter();

  @AfterEach
  void tearDown() { EncryptionKeyHolder.clear(); }

  @Test
  void deve_cifrar_com_iv_aleatorio_e_decifrar_quando_chave_de_dev() {
    String a = converter.convertToDatabaseColumn("ana@example.com");
    String b = converter.convertToDatabaseColumn("ana@example.com");
    assertThat(a).isNotEqualTo("ana@example.com").isNotEqualTo(b);
    assertThat(Base64.getDecoder().decode(a)).hasSizeGreaterThan(AesGcmConverter.IV_BYTES + 16);
    assertThat(converter.convertToEntityAttribute(a)).isEqualTo("ana@example.com");
    assertThat(converter.convertToEntityAttribute(b)).isEqualTo("ana@example.com");
    assertThat(EncryptionKeyHolder.isConfigured()).isFalse();
  }

  @Test
  void deve_usar_chave_configurada_e_falhar_com_chave_diferente() {
    // chaves geradas na execução: nenhum segredo fixo em teste (D-11)
    byte[] k1 = new byte[32]; new java.security.SecureRandom().nextBytes(k1);
    EncryptionKeyHolder.setKeyBase64(Base64.getEncoder().encodeToString(k1));
    assertThat(EncryptionKeyHolder.isConfigured()).isTrue();
    String cipher = converter.convertToDatabaseColumn("Nome Sensível");
    assertThat(converter.convertToEntityAttribute(cipher)).isEqualTo("Nome Sensível");

    byte[] k2 = new byte[32]; new java.security.SecureRandom().nextBytes(k2);
    EncryptionKeyHolder.setKey(k2);
    assertThatThrownBy(() -> converter.convertToEntityAttribute(cipher)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void sem_chave_configurada_a_chave_e_efemera_e_nunca_fixa() {
    byte[] first = EncryptionKeyHolder.key().getEncoded();
    String cipher = converter.convertToDatabaseColumn("ana@example.com");
    EncryptionKeyHolder.clear();
    assertThat(EncryptionKeyHolder.key().getEncoded()).as("nova chave a cada subida sem DATA_ENCRYPTION_KEY").isNotEqualTo(first);
    assertThatThrownBy(() -> converter.convertToEntityAttribute(cipher)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void deve_rejeitar_chave_de_tamanho_invalido_e_ignorar_valor_vazio() {
    assertThatThrownBy(() -> EncryptionKeyHolder.setKey(new byte[5])).isInstanceOf(IllegalArgumentException.class);
    EncryptionKeyHolder.setKeyBase64("");
    assertThat(EncryptionKeyHolder.isConfigured()).isFalse();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }
}
