package br.com.velion.scoresaf.infra.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserSettings;
import br.com.velion.scoresaf.infra.mysql.repository.UserAccountJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.UserSettingsJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Executa as migrations Flyway ({@code V1__plataforma.sql}, escrita para MySQL 8) em H2 modo MySQL e sobe o Hibernate com
 * {@code ddl-auto=validate}: prova que o schema versionado é equivalente às entidades JPA. No basquete o teste também conferia
 * os catálogos semeados pela V2; aqui a semente do jogo chega no bloco 4.
 */
@SpringBootTest(classes = PersistenceTestApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:scoresaf_flyway;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.dialect=br.com.velion.scoresaf.infra.mysql.TestH2Dialect"})
@ActiveProfiles("test")
class FlywayBaselineValidationTest {

  @Autowired UserAccountJpaRepository users;
  @Autowired UserSettingsJpaRepository settings;

  @Test
  void deve_validar_entidades_contra_schema_do_flyway_e_gravar_pela_v1() {
    UserAccount u = new UserAccount(); u.setEmail("flyway@example.com"); u.setDisplayName("Flyway"); u.setPasswordHash("x");
    users.saveAndFlush(u);
    UserSettings s = new UserSettings(); s.setUser(u);
    settings.saveAndFlush(s);
    assertThat(settings.findByUserId(u.getId())).get().satisfies(x -> {
      assertThat(x.getPlayCallingLevel()).isEqualTo(PlayCallingLevel.KEY_MOMENTS);
      assertThat(x.getQuarterDurationMinutes()).isEqualTo(UserSettings.DEFAULT_QUARTER_MINUTES);
    });
  }
}
