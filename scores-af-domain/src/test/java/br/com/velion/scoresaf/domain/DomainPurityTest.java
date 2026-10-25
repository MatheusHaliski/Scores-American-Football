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
 */
class DomainPurityTest {
  private static final List<String> FORBIDDEN = List.of("import org.springframework", "import jakarta.servlet", "import tools.jackson", "import com.fasterxml");

  @Test
  void dominio_nao_importa_framework() throws IOException {
    Path src = Path.of("src", "main", "java");
    try (Stream<Path> files = Files.walk(src)) {
      List<String> offenders = files.filter(p -> p.toString().endsWith(".java")).filter(p -> {
        try {
          String code = Files.readString(p);
          return FORBIDDEN.stream().anyMatch(code::contains);
        } catch (IOException e) { throw new IllegalStateException(e); }
      }).map(Path::toString).toList();
      assertThat(offenders).as("arquivos do domínio com import de framework").isEmpty();
    }
  }
}
