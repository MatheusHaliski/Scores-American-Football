package br.com.velion.scoresaf.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * O domínio é Java puro (D-3): regras, motor e políticas não dependem do Spring nem da web. Um import de framework aqui
 * acopla o motor determinístico à infraestrutura — o teste falha e diz o arquivo.
 *
 * <p>Bloco 3 (D-9): as entidades da conta vêm do basquete com as anotações {@code jakarta.persistence} (mapeamento) e
 * {@code jakarta.validation}, que são só metadados e ficam permitidas. Continua proibido qualquer Spring (inclusive o
 * {@code Persistable} do Spring Data que o basquete usava, por nome qualificado), servlet/web e Jackson — por isso o teste
 * procura a referência em qualquer lugar do código, não só no {@code import}.
 */
class DomainPurityTest {
  private static final List<String> FORBIDDEN = List.of("org.springframework", "jakarta.servlet", "javax.servlet", "jakarta.ws.rs",
      "tools.jackson", "com.fasterxml");
  private static final List<String> ALLOWED_JAKARTA = List.of("jakarta.persistence", "jakarta.validation");

  @Test
  void dominio_nao_importa_framework() throws IOException {
    Path src = Path.of("src", "main", "java");
    try (Stream<Path> files = Files.walk(src)) {
      List<String> offenders = files.filter(p -> p.toString().endsWith(".java")).filter(p -> {
        try {
          String code = Files.readString(p);
          return FORBIDDEN.stream().anyMatch(code::contains) || otherJakarta(code);
        } catch (IOException e) { throw new IllegalStateException(e); }
      }).map(Path::toString).toList();
      assertThat(offenders).as("arquivos do domínio com import de framework").isEmpty();
    }
  }

  /** Qualquer {@code jakarta.*} fora das anotações permitidas (ex.: {@code jakarta.servlet}, {@code jakarta.annotation}). */
  static boolean otherJakarta(String code) {
    int i = code.indexOf("jakarta.");
    while (i >= 0) {
      int at = i;
      if (ALLOWED_JAKARTA.stream().noneMatch(a -> code.startsWith(a, at))) return true;
      i = code.indexOf("jakarta.", i + 1);
    }
    return false;
  }

  @Test
  void a_regra_pega_spring_por_nome_qualificado_e_jakarta_fora_da_lista() {
    assertThat(FORBIDDEN.stream().anyMatch("class X implements org.springframework.data.domain.Persistable<UUID> {}"::contains)).isTrue();
    assertThat(otherJakarta("import jakarta.servlet.http.HttpServletRequest;")).isTrue();
    assertThat(otherJakarta("import jakarta.persistence.Entity;\nimport jakarta.validation.constraints.NotNull;")).isFalse();
  }
}
