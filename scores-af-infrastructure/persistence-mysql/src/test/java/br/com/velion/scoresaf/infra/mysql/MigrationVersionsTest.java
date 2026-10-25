package br.com.velion.scoresaf.infra.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Duas migrations com a mesma versão quebram o deploy (o Flyway recusa subir). Acontece quando dois PRs paralelos criam o
 * mesmo V-número: antes de cada merge, renumere contra o {@code main}. Varre SQL e Java ({@code db/migration}), ignorando
 * classes internas; {@code V7}, {@code V07} e {@code V7.0} são a mesma versão para o Flyway.
 */
class MigrationVersionsTest {
  private static final Pattern NAME = Pattern.compile("^V([0-9]+(?:[._][0-9]+)*)__.+\\.(sql|class)$");

  @Test
  void nenhuma_versao_de_migration_repetida() throws IOException {
    List<String> names = scan();
    assertThat(names).as("migrations encontradas no classpath").isNotEmpty();
    assertThat(duplicates(names)).as("versões repetidas (renumere contra o main)").isEmpty();
  }

  @Test
  void detecta_repeticao_e_ignora_classes_internas() {
    assertThat(duplicates(List.of("V5__a.sql", "V05__b.sql", "V6__x.class", "V6__x$Passo.class", "V7_1__y.sql", "V7.1__z.sql", "V8__ok.sql", "R__view.sql")))
        .containsOnlyKeys("5", "7.1");
  }

  static List<String> scan() throws IOException {
    List<String> names = new ArrayList<>();
    for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/**/V*__*")) {
      if (r.getFilename() != null) names.add(r.getFilename());
    }
    return names;
  }

  static Map<String, List<String>> duplicates(List<String> names) {
    Map<String, List<String>> byVersion = new TreeMap<>();
    for (String n : names) {
      if (n.contains("$")) continue;
      Matcher m = NAME.matcher(n);
      if (!m.matches()) continue;
      byVersion.computeIfAbsent(normalize(m.group(1)), k -> new ArrayList<>()).add(n);
    }
    byVersion.values().removeIf(l -> l.size() < 2);
    return byVersion;
  }

  /** Mesma regra do Flyway: partes numéricas, zeros à esquerda e zeros finais não contam. */
  static String normalize(String version) {
    List<BigInteger> parts = new ArrayList<>(Arrays.stream(version.split("[._]")).map(BigInteger::new).toList());
    while (parts.size() > 1 && parts.get(parts.size() - 1).signum() == 0) parts.remove(parts.size() - 1);
    return parts.stream().map(BigInteger::toString).collect(Collectors.joining("."));
  }
}
