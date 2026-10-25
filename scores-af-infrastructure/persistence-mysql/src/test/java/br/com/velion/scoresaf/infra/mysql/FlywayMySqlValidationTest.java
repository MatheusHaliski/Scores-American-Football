package br.com.velion.scoresaf.infra.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserSettings;
import br.com.velion.scoresaf.domain.common.Hashing;
import br.com.velion.scoresaf.infra.mysql.repository.UserAccountJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.UserSettingsJpaRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * O que o perfil {@code mysql} faz em produção, num MySQL 8 de verdade: Flyway aplica a V1 e o Hibernate sobe com
 * {@code ddl-auto=validate} e o dialeto do MySQL; depois grava e lê uma conta (e-mail cifrado, busca por hash) e as configurações
 * do futebol americano. Perfil {@code it} (Docker). A credencial do banco é a que o Testcontainers gera para o contêiner.
 */
@Tag("it")
@Testcontainers
@SpringBootTest(classes = PersistenceTestApplication.class)
class FlywayMySqlValidationTest {
  @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0").withDatabaseName("scoresaf");

  @DynamicPropertySource
  static void mysql(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", MYSQL::getJdbcUrl);
    r.add("spring.datasource.username", MYSQL::getUsername);
    r.add("spring.datasource.password", MYSQL::getPassword);
    r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    r.add("spring.flyway.enabled", () -> "true");
    r.add("spring.flyway.locations", () -> "classpath:db/migration");
    r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
  }

  @Autowired UserAccountJpaRepository users;
  @Autowired UserSettingsJpaRepository settings;

  @Test
  void schema_da_v1_valida_contra_as_entidades_no_mysql() {
    UserAccount u = new UserAccount(); u.setEmail("mysql@example.com"); u.setDisplayName("MySQL"); u.setPasswordHash("x");
    users.saveAndFlush(u);
    UserSettings s = new UserSettings(); s.setUser(u);
    settings.saveAndFlush(s);
    assertThat(users.findByEmailHash(Hashing.emailHash("mysql@example.com"))).get().satisfies(x -> assertThat(x.getEmail()).isEqualTo("mysql@example.com"));
    assertThat(settings.findByUserId(u.getId())).get().satisfies(x -> assertThat(x.getQuarterDurationMinutes()).isEqualTo(UserSettings.DEFAULT_QUARTER_MINUTES));
  }
}
