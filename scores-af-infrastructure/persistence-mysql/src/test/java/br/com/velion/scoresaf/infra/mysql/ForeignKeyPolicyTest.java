package br.com.velion.scoresaf.infra.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.infra.mysql.ForeignKeyPolicy.Fk;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Migra do zero (Flyway V1 → última) e compara toda FK do banco com {@link ForeignKeyPolicy#POLICY} (D-23). FK nova sem
 * entrada na política, entrada sem FK ou regra diferente deixam o CI vermelho. Usa {@link DatabaseMetaData#getImportedKeys}
 * (portável entre H2 e MySQL). RESTRICT e NO ACTION contam como RESTRICT: no InnoDB os dois recusam a exclusão na hora.
 */
@SpringBootTest(classes = PersistenceTestApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:scoresaf_fk_policy;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.jpa.hibernate.ddl-auto=none"})
@ActiveProfiles("test")
class ForeignKeyPolicyTest {
  @Autowired DataSource dataSource;

  @Test
  void toda_fk_do_banco_segue_a_politica() throws Exception {
    try (Connection c = dataSource.getConnection()) { assertMatchesPolicy(c); }
  }

  /** Compara as FKs da conexão com a política; usado também pelo teste contra MySQL real ({@code ForeignKeyPolicyMySqlTest}). */
  static void assertMatchesPolicy(Connection c) throws Exception {
    Set<String> actual = new TreeSet<>(foreignKeys(c));
    Set<String> expected = new TreeSet<>();
    for (Fk fk : ForeignKeyPolicy.POLICY) expected.add(key(fk.table(), fk.column(), fk.refTable(), fk.refColumn(), fk.onDelete()));

    Set<String> missingFromPolicy = new TreeSet<>(actual);
    missingFromPolicy.removeAll(expected);
    Set<String> missingFromDatabase = new TreeSet<>(expected);
    missingFromDatabase.removeAll(actual);

    assertThat(actual).as("FKs encontradas").isNotEmpty();
    assertThat(missingFromPolicy).as("FK no banco sem entrada igual na política (FK nova ou regra diferente)").isEmpty();
    assertThat(missingFromDatabase).as("entrada da política sem FK igual no banco").isEmpty();
  }

  @Test
  void conta_nasce_real_e_test_account_e_derivada_da_origem() throws Exception {
    try (Connection c = dataSource.getConnection(); var st = c.createStatement()) {
      st.execute("INSERT INTO user_account (id, created_at, updated_at, display_name, email, email_hash, failed_login_attempts, locale, "
          + "marketing_consent, password_hash, role, status) VALUES (X'00000000000000000000000000000001', NOW(), NOW(), 'a', 'a', 'h1', 0, 'pt-BR', "
          + "FALSE, 'x', 'USER', 'ACTIVE')");
      st.execute("INSERT INTO user_account (id, created_at, updated_at, display_name, email, email_hash, failed_login_attempts, locale, "
          + "marketing_consent, password_hash, role, status, account_origin, fixture_key) VALUES (X'00000000000000000000000000000002', NOW(), NOW(), "
          + "'b', 'b', 'h2', 0, 'pt-BR', FALSE, 'x', 'USER', 'ACTIVE', 'DEMO', 'demo_ana')");
      try (ResultSet rs = st.executeQuery("SELECT account_origin, test_account FROM user_account ORDER BY email_hash")) {
        rs.next();
        assertThat(rs.getString(1)).isEqualTo("REAL");
        assertThat(rs.getBoolean(2)).isFalse();
        rs.next();
        assertThat(rs.getString(1)).isEqualTo("DEMO");
        assertThat(rs.getBoolean(2)).isTrue();
      }
      st.execute("DELETE FROM user_account");
    }
  }

  static List<String> foreignKeys(Connection c) throws Exception {
    List<String> out = new ArrayList<>();
    {
      DatabaseMetaData md = c.getMetaData();
      List<String> tables = new ArrayList<>();
      try (ResultSet rs = md.getTables(c.getCatalog(), c.getSchema(), "%", new String[] {"TABLE", "BASE TABLE"})) {
        while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
      }
      for (String t : tables) {
        if (t.toLowerCase(Locale.ROOT).startsWith("flyway_")) continue;
        try (ResultSet rs = md.getImportedKeys(c.getCatalog(), c.getSchema(), t)) {
          while (rs.next()) {
            out.add(key(rs.getString("FKTABLE_NAME"), rs.getString("FKCOLUMN_NAME"), rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME"),
                rule(rs.getShort("DELETE_RULE"))));
          }
        }
      }
    }
    return out;
  }

  private static String rule(short jdbcRule) {
    return switch (jdbcRule) {
      case DatabaseMetaData.importedKeyCascade -> "CASCADE";
      case DatabaseMetaData.importedKeySetNull -> "SET NULL";
      case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
      default -> "RESTRICT"; // importedKeyRestrict e importedKeyNoAction
    };
  }

  private static String key(String table, String column, String refTable, String refColumn, String onDelete) {
    return (table + "." + column + " -> " + refTable + "." + refColumn + " ON DELETE " + onDelete).toLowerCase(Locale.ROOT);
  }
}
