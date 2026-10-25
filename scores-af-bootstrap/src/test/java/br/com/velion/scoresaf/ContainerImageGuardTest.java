package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * As duas etapas da imagem da API (build e execução) rodam na JVM LTS 25. No basquete o Dependabot trocou a etapa de build
 * por uma tag sem LTS achando que era atualização; aqui a regra nasce como teste (D-5).
 */
class ContainerImageGuardTest {
  private static final Pattern FROM = Pattern.compile("(?im)^FROM\\s+(\\S+)");
  private static final Pattern TEMURIN = Pattern.compile("temurin[-:](\\d+)");

  @Test
  void build_e_execucao_usam_a_jvm_lts_25() throws Exception {
    String dockerfile = Files.readString(Path.of("..", "Dockerfile"));
    List<String> images = FROM.matcher(dockerfile).results().map(r -> r.group(1)).toList();
    assertThat(images).as("etapas do Dockerfile").hasSize(2);
    assertThat(images).allSatisfy(image -> {
      Matcher jvm = TEMURIN.matcher(image);
      assertThat(jvm.find()).as("imagem com temurin: %s", image).isTrue();
      assertThat(jvm.group(1)).as("JVM da imagem %s", image).isEqualTo("25");
    });
  }

  @Test
  void imagem_roda_sem_root_e_empacota_o_bootstrap_do_af() throws Exception {
    String dockerfile = Files.readString(Path.of("..", "Dockerfile"));
    assertThat(dockerfile).contains("-pl scores-af-bootstrap").containsPattern("(?m)^USER\\s+10001");
  }
}
