package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Amostras douradas das respostas que o front lê, gravadas pela própria API (mesmo mapper, mesma serialização): o meta e o
 * corpo de erro. Os testes do front ({@code frontend/tests/api/*.test.mts}) leem estes arquivos, então os adaptadores de
 * {@code src/lib/api} são conferidos contra o formato Java, não contra um JSON escrito à mão (D-5). Mudou o formato? Regenere com
 * {@code mvn -pl scores-af-bootstrap -am test -Dtest=ApiGoldenTest -Dsurefire.failIfNoSpecifiedTests=false -Dgolden.update=true}
 * e rode {@code npm run test:unit}.
 */
@SpringBootTest(properties = "scores.app.version=0.0.0-golden")
@AutoConfigureMockMvc
class ApiGoldenTest {
  static final Path DIR = Path.of("..", "docs", "validation", "golden");

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;

  private void assertGolden(String file, MockHttpServletRequestBuilder request) throws Exception {
    Path golden = DIR.resolve(file);
    JsonNode now = json.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString());
    if (Boolean.getBoolean("golden.update") || "1".equals(System.getenv("GOLDEN_UPDATE"))) {
      Files.writeString(golden, json.writerWithDefaultPrettyPrinter().writeValueAsString(now) + "\n");
    }
    assertThat(golden).as("amostra %s (rodar a partir do módulo; regenerar com -Dgolden.update=true)", file).exists();
    if (!json.readTree(Files.readString(golden)).equals(now)) {
      fail("docs/validation/golden/" + file + " diverge da API: regenere com -Dgolden.update=true e rode o teste do front");
    }
  }

  @Test
  void meta_bate_com_a_amostra_dourada() throws Exception {
    assertGolden("meta.json", get("/api/v1/meta"));
  }

  @Test
  void corpo_de_erro_bate_com_a_amostra_dourada() throws Exception {
    // correlationId e idioma fixos para a amostra ser estável
    assertGolden("problem-401.json", get("/api/v1/me").header("X-Correlation-Id", "golden-401").header("Accept-Language", "pt-BR"));
  }
}
