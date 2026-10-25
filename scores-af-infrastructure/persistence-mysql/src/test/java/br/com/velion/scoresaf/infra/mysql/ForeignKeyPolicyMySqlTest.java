package br.com.velion.scoresaf.infra.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Mesma conferência do {@link ForeignKeyPolicyTest}, num MySQL 8 de verdade: migra V1 → última e compara cada FK com a
 * política (D-23 do basquete). Prova também que a V1 da plataforma roda no MySQL (coluna gerada, CHECK). Perfil {@code it} (Docker).
 */
@Tag("it")
@Testcontainers
class ForeignKeyPolicyMySqlTest {
  @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0").withDatabaseName("scoresaf");

  @Test
  void banco_migrado_do_zero_no_mysql_segue_a_politica() throws Exception {
    Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).locations("classpath:db/migration").load().migrate();
    try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
      ForeignKeyPolicyTest.assertMatchesPolicy(c);
      try (ResultSet rs = c.createStatement().executeQuery(
          "SELECT DELETE_RULE, COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = 'scoresaf' GROUP BY DELETE_RULE")) {
        int total = 0;
        while (rs.next()) {
          assertThat(rs.getString(1)).as("regra explícita (nada de NO ACTION implícito)").isIn("CASCADE", "RESTRICT", "SET NULL");
          total += rs.getInt(2);
        }
        assertThat(total).isEqualTo(ForeignKeyPolicy.POLICY.size());
      }
    }
  }
}
