package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

/**
 * Toda classe em {@code spring.autoconfigure.exclude} (em qualquer perfil do application.yml) existe e é uma
 * autoconfiguração. O Boot ignora em silêncio a exclusão de uma classe inexistente: depois de um rename (ex.: Boot 4,
 * {@code RedisAutoConfiguration} → {@code DataRedisAutoConfiguration}), Cassandra e Redis voltariam a subir no perfil dev.
 */
class AutoConfigurationExclusionsTest {

  @Test
  void cada_exclusao_aponta_para_uma_autoconfiguracao_existente() throws Exception {
    List<String> excluded = new ArrayList<>();
    for (PropertySource<?> doc : new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))) {
      for (String name : ((EnumerablePropertySource<?>) doc).getPropertyNames()) {
        if (name.startsWith("spring.autoconfigure.exclude")) {
          for (String cls : String.valueOf(doc.getProperty(name)).split(",")) if (!cls.isBlank()) excluded.add(cls.trim());
        }
      }
    }
    assertThat(excluded).as("exclusões declaradas").isNotEmpty();
    ClassLoader cl = getClass().getClassLoader();
    assertThat(excluded).allSatisfy(cls -> {
      assertThat(ClassUtils.isPresent(cls, cl)).as("classe existe: %s", cls).isTrue();
      assertThat(Class.forName(cls, false, cl).isAnnotationPresent(AutoConfiguration.class)).as("é @AutoConfiguration: %s", cls).isTrue();
    });
  }
}
