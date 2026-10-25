package br.com.velion.scoresaf.infra.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.DelayedDropRegistryNotAvailableImpl;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Utilitário: gera o DDL das entidades com {@code MySQLDialect} (naming Spring: snake_case) em
 * {@code target/generated-ddl/mysql-schema.sql}. Serve de base/revisão para {@code V1__plataforma.sql}
 * (o baseline oficial é o Flyway; este arquivo nunca é executado).
 */
class SchemaDdlGeneratorTest {

  @Test
  void deve_gerar_ddl_mysql_a_partir_das_entidades() throws Exception {
    Path out = Path.of("target", "generated-ddl", "mysql-schema.sql");
    Files.createDirectories(out.getParent());
    Files.deleteIfExists(out);

    Map<String, Object> settings = new HashMap<>();
    settings.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
    settings.put("hibernate.boot.allow_jdbc_metadata_access", "false");
    settings.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
    settings.put("hibernate.implicit_naming_strategy", "org.springframework.boot.hibernate.SpringImplicitNamingStrategy");
    settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
    settings.put("jakarta.persistence.schema-generation.scripts.create-target", out.toString());
    settings.put("hibernate.hbm2ddl.delimiter", ";");
    settings.put("hibernate.format_sql", "true");

    StandardServiceRegistry registry = new StandardServiceRegistryBuilder().applySettings(settings).build();
    try {
      MetadataSources sources = new MetadataSources(registry);
      ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AnnotationTypeFilter(jakarta.persistence.Entity.class));
      Set<String> classes = new java.util.TreeSet<>();
      scanner.findCandidateComponents("br.com.velion.scoresaf.domain").forEach(bd -> classes.add(bd.getBeanClassName()));
      for (String c : classes) sources.addAnnotatedClass(Class.forName(c));
      assertThat(classes).hasSizeGreaterThanOrEqualTo(8);
      var metadata = sources.buildMetadata();
      SchemaManagementToolCoordinator.process(metadata, registry, settings, DelayedDropRegistryNotAvailableImpl.INSTANCE);
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
    File f = out.toFile();
    assertThat(f).exists();
    assertThat(Files.readString(out)).contains("create table user_account").contains("create table user_session");
  }
}
