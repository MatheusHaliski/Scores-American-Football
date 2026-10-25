package br.com.velion.scoresaf.infra.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.application.seed.DefaultWorld;
import br.com.velion.scoresaf.application.seed.DefaultWorldFactory;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.EnumSet;
import br.com.velion.scoresaf.domain.account.UserSettings;
import br.com.velion.scoresaf.domain.common.Hashing;
import br.com.velion.scoresaf.infra.mysql.repository.UserAccountJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.UserSettingsJpaRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * O que o perfil {@code mysql} faz em produção, num MySQL 8 de verdade: Flyway aplica a V1 e o Hibernate sobe com
 * {@code ddl-auto=validate} e o dialeto do MySQL; depois grava e lê uma conta (e-mail cifrado, busca por hash), as configurações
 * do futebol americano e o mundo semente inteiro pela V2 (bloco 4). Perfil {@code it} (Docker). A credencial do banco é a que o Testcontainers gera para o contêiner.
 */
@Tag("it")
@Testcontainers
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import({CatalogGraph.class, CareerGraph.class})
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
  @Autowired CatalogGraph catalog;
  @Autowired CareerGraph career;

  @Test
  void schema_da_v1_valida_contra_as_entidades_no_mysql() {
    UserAccount u = new UserAccount(); u.setEmail("mysql@example.com"); u.setDisplayName("MySQL"); u.setPasswordHash("x");
    users.saveAndFlush(u);
    UserSettings s = new UserSettings(); s.setUser(u);
    settings.saveAndFlush(s);
    assertThat(users.findByEmailHash(Hashing.emailHash("mysql@example.com"))).get().satisfies(x -> assertThat(x.getEmail()).isEqualTo("mysql@example.com"));
    assertThat(settings.findByUserId(u.getId())).get().satisfies(x -> assertThat(x.getQuarterDurationMinutes()).isEqualTo(UserSettings.DEFAULT_QUARTER_MINUTES));
  }

  /** A V2 aceita o mundo semente inteiro (CHECKs de posição, situação, camisa e capacidade; FKs) e devolve o que foi gravado. */
  @Test
  void mundo_semente_grava_pela_v2_e_rele() {
    DefaultWorld w = new DefaultWorldFactory().build(DefaultWorldFactory.DEFAULT_SEED + 1);
    catalog.persist(w);
    Player p = w.players().get(100);
    assertThat(catalog.players.findById(p.getId())).get().satisfies(x -> {
      assertThat(x.getAttributes().asMap()).isEqualTo(p.getAttributes().asMap());
      assertThat(x.getContract().getBaseSalaryByYear()).isEqualTo(p.getContract().getBaseSalaryByYear());
    });
    assertThat(catalog.players.countByWorldId(w.world().getId())).isEqualTo(32 * 69);
    assertThat(catalog.players.countByClubIdAndRosterStatusIn(p.getClub().getId(), EnumSet.of(RosterStatus.PRACTICE_SQUAD))).isEqualTo(16);
    assertThat(catalog.leagues.findById(w.leagues().get(0).getId())).get().satisfies(l -> assertThat(l.getRules().getPracticeSquadMax()).isEqualTo(16));
  }

  /** A V3 aceita a carreira inteira (CHECKs de slot, situação, horário, vaga e profundidade; FKs) e devolve o que foi gravado. */
  @Test
  void carreira_grava_pela_v3_e_rele() {
    DefaultWorld w = new DefaultWorldFactory().build(DefaultWorldFactory.DEFAULT_SEED + 2);
    catalog.persist(w);
    CareerGraph.Saved saved = career.persist(w, "v3-" + java.util.UUID.randomUUID() + "@example.com");
    assertThat(career.saves.findById(saved.career().getId())).get().satisfies(c -> {
      assertThat(c.getSlot()).isEqualTo(1);
      assertThat(c.getSeed()).isEqualTo(42L);
    });
    assertThat(career.fixtures.countBySeasonId(saved.season().getId())).isEqualTo(1);
    assertThat(career.slots.findByDepthChartId(saved.chart().getId())).hasSize(1);
    assertThat(career.moves.findByCareerIdOrderByCreatedAtAsc(saved.career().getId())).singleElement()
        .satisfies(m -> assertThat(m.getToStatus()).isEqualTo(RosterStatus.WAIVED));
    assertThat(catalog.players.findById(saved.slot().getPlayer().getId())).get().satisfies(p -> assertThat(p.getInjuryWeeksOut()).isZero());
  }
}
