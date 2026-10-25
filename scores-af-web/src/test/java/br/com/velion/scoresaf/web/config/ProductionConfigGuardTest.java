package br.com.velion.scoresaf.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

/** Perfil prod com segredo vazio não sobe; a mensagem cita o nome da variável e nunca o valor. */
class ProductionConfigGuardTest {
  private static final String KEY32 = Base64.getEncoder().encodeToString(randomBytes(32));
  /** Chave dos links curtos gerada na hora (nunca um valor fixo no repositório). */
  private static final String LINK_SECRET = randomHex();

  /** Senhas de banco, cache e armazenamento de teste: geradas na execução (docs/01 §6). */
  private static final String DB_PASSWORD = randomHex(), CASSANDRA = randomHex(), OPENSEARCH = randomHex(), S3 = randomHex(), REDIS = randomHex(),
      PEM_PRIVATE = randomHex(), PEM_PUBLIC = randomHex();

  private static byte[] randomBytes(int n) {
    byte[] b = new byte[n];
    new java.security.SecureRandom().nextBytes(b);
    return b;
  }

  private static String randomHex() { return java.util.HexFormat.of().formatHex(randomBytes(32)); }

  private static MockEnvironment complete() {
    return new MockEnvironment()
        .withProperty("scores.security.jwt.private-key-pem", PEM_PRIVATE)
        .withProperty("scores.security.jwt.public-key-pem", PEM_PUBLIC)
        .withProperty("scores.security.encryption.data-key", KEY32)
        .withProperty("spring.datasource.url", "jdbc:mysql://mysql.internal:3306/scores?sslMode=REQUIRED")
        .withProperty("spring.datasource.password", DB_PASSWORD)
        .withProperty("scores.app.cors-allowed-origins", "https://scores.app")
        .withProperty("scores.media.link-secret", LINK_SECRET)
        .withProperty("scores.persistence.timeline", "cassandra").withProperty("spring.cassandra.password", CASSANDRA)
        .withProperty("scores.persistence.search", "opensearch").withProperty("scores.opensearch.password", OPENSEARCH)
        .withProperty("scores.persistence.media", "s3").withProperty("scores.s3.access-key-id", "id").withProperty("scores.s3.secret-access-key", S3)
        .withProperty("scores.persistence.cache", "redis").withProperty("spring.data.redis.url", "rediss://app:" + REDIS + "@redis.internal:6379");
  }

  @Test
  void configuracao_completa_passa() {
    assertThat(ProductionConfigGuard.problems(complete())).isEmpty();
  }

  @Test
  void segredo_vazio_ou_fraco_e_listado_pelo_nome() {
    MockEnvironment env = complete()
        .withProperty("scores.security.jwt.private-key-pem", "")
        .withProperty("scores.security.encryption.data-key", Base64.getEncoder().encodeToString(randomBytes(16)))
        .withProperty("spring.datasource.password", " ")
        .withProperty("scores.app.cors-allowed-origins", "http://localhost:3000")
        .withProperty("spring.cassandra.password", "")
        .withProperty("spring.data.redis.url", "redis://redis.internal:6379")
        .withProperty("scores.media.link-secret", "curto");
    assertThat(String.join("\n", ProductionConfigGuard.problems(env)))
        .contains("JWT_PRIVATE_KEY_PEM").contains("DATA_ENCRYPTION_KEY deve ser Base64 de 32 bytes").contains("MYSQL_PASSWORD")
        .contains("APP_CORS_ALLOWED_ORIGINS").contains("CASSANDRA_PASSWORD").contains("REDIS_URL").contains("MEDIA_LINK_SECRET deve ter 32+")
        .doesNotContain(PEM_PUBLIC).doesNotContain(OPENSEARCH).doesNotContain("curto");
    assertThat(ProductionConfigGuard.problems(complete().withProperty("scores.media.link-secret", ""))).containsExactly("MEDIA_LINK_SECRET vazia");
  }

  @Test
  void h2_nunca_em_producao() {
    assertThat(ProductionConfigGuard.problems(complete().withProperty("spring.datasource.url", "jdbc:h2:mem:scores")))
        .anySatisfy(m -> assertThat(m).contains("H2"));
  }

  @Test
  void perfil_prod_sem_segredos_nao_sobe() {
    new ApplicationContextRunner()
        .withUserConfiguration(ProductionConfigGuard.class)
        .withPropertyValues("spring.profiles.active=prod")
        .run(ctx -> assertThat(ctx).hasFailed()
            .getFailure().rootCause().hasMessageContaining("JWT_PRIVATE_KEY_PEM").hasMessageContaining("DATA_ENCRYPTION_KEY")
            .hasMessageContaining("MEDIA_LINK_SECRET"));
  }

  @Test
  void fora_de_prod_o_guarda_nao_atua() {
    new ApplicationContextRunner().withUserConfiguration(ProductionConfigGuard.class).run(ctx -> assertThat(ctx).hasNotFailed());
  }
}
