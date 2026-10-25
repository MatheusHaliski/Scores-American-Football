package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Amostras douradas das respostas que o front lê, gravadas pela própria API (mesmo mapper, mesma serialização): o meta, o
 * corpo de erro e, desde o bloco 3, as respostas de conta (login, {@code GET /api/v1/me}, {@code GET /api/v1/me/settings} e o
 * corpo do 429 do limite). Os testes do front ({@code frontend/tests/api/*.test.mts}) leem estes arquivos, então os adaptadores
 * de {@code src/lib/api} são conferidos contra o formato Java, não contra um JSON escrito à mão (D-5). Mudou o formato? Regenere
 * com {@code mvn -pl scores-af-bootstrap -am test -Dtest=ApiGoldenTest -Dsurefire.failIfNoSpecifiedTests=false -Dgolden.update=true}
 * e rode {@code npm run test:unit}.
 *
 * <p>Valores que mudam a cada execução (ids, instantes, token, e-mail gerado) são trocados por valores fixos do mesmo formato
 * antes de gravar e de comparar ({@link #STABLE}); as chaves, os tipos e os demais valores são os reais. A senha da conta de
 * teste é gerada na execução e nunca aparece na resposta.
 */
@SpringBootTest(properties = {"scores.app.version=0.0.0-golden", "scores.security.rate-limit.limits.password-forgot=1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiGoldenTest {
  static final Path DIR = Path.of("..", "docs", "validation", "golden");
  /** Campo volátil → valor fixo do mesmo formato (UUID, Instant ISO-8601, JWT opaco, e-mail). */
  static final Map<String, String> STABLE = Map.of(
      "id", "00000000-0000-4000-8000-000000000001",
      "accessToken", "golden.access.token",
      "accessExpiresAt", "2026-01-01T00:15:00Z",
      "refreshExpiresAt", "2026-01-08T00:00:00Z",
      "createdAt", "2026-01-01T00:00:00Z",
      "lastLoginAt", "2026-01-01T00:00:00Z",
      "dataProcessingConsentAt", "2026-01-01T00:00:00Z",
      "email", "golden@scoresaf.test");

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;

  private void assertGolden(String file, MockHttpServletRequestBuilder request) throws Exception {
    assertGolden(file, mvc.perform(request).andReturn());
  }

  private void assertGolden(String file, MvcResult result) throws Exception {
    Path golden = DIR.resolve(file);
    JsonNode now = stable(json.readTree(result.getResponse().getContentAsString()));
    if (Boolean.getBoolean("golden.update") || "1".equals(System.getenv("GOLDEN_UPDATE"))) {
      Files.writeString(golden, json.writerWithDefaultPrettyPrinter().writeValueAsString(now) + "\n");
    }
    assertThat(golden).as("amostra %s (rodar a partir do módulo; regenerar com -Dgolden.update=true)", file).exists();
    if (!json.readTree(Files.readString(golden)).equals(now)) {
      fail("docs/validation/golden/" + file + " diverge da API: regenere com -Dgolden.update=true e rode o teste do front");
    }
  }

  /** Troca os valores voláteis (em qualquer nível) por {@link #STABLE}; campo ausente continua ausente. */
  static JsonNode stable(JsonNode node) {
    if (node instanceof ObjectNode o) {
      for (String name : o.propertyNames()) {
        JsonNode v = o.get(name);
        if (STABLE.containsKey(name) && v != null && !v.isNull()) o.put(name, STABLE.get(name));
        else stable(v);
      }
    } else if (node != null && node.isArray()) {
      node.forEach(ApiGoldenTest::stable);
    }
    return node;
  }

  /** Cadastra uma conta nova (senha gerada na execução) e devolve a resposta do login. */
  private MvcResult registerAndLogin() throws Exception {
    String email = "golden-" + UUID.randomUUID() + "@scoresaf.test";
    String password = "T" + UUID.randomUUID();
    MvcResult reg = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
        Map.of("email", email, "password", password, "displayName", "Jogadora Dourada", "locale", "pt-BR", "marketingConsent", false)))).andReturn();
    assertThat(reg.getResponse().getStatus()).as(reg.getResponse().getContentAsString()).isEqualTo(201);
    MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
    assertThat(login.getResponse().getStatus()).as(login.getResponse().getContentAsString()).isEqualTo(200);
    return login;
  }

  private String bearer() throws Exception {
    return "Bearer " + json.readTree(registerAndLogin().getResponse().getContentAsString()).get("accessToken").asString();
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

  @Test
  void login_bate_com_a_amostra_dourada() throws Exception {
    // o mesmo TokenResponse do refresh: access no corpo, refresh só no cookie HttpOnly
    assertGolden("auth-login.json", registerAndLogin());
  }

  @Test
  void minha_conta_bate_com_a_amostra_dourada() throws Exception {
    assertGolden("me.json", get("/api/v1/me").header("Authorization", bearer()).header("Accept-Language", "pt-BR"));
  }

  @Test
  void configuracoes_batem_com_a_amostra_dourada() throws Exception {
    assertGolden("me-settings.json", get("/api/v1/me/settings").header("Authorization", bearer()).header("Accept-Language", "pt-BR"));
  }

  @Test
  void limite_excedido_bate_com_a_amostra_dourada() throws Exception {
    // limite do "esqueci a senha" em 1 neste contexto: o 2º pedido do mesmo IP recebe o 429 do filtro
    MockHttpServletRequestBuilder forgot = post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"ninguem@scoresaf.test\"}").header("X-Correlation-Id", "golden-429").header("Accept-Language", "pt-BR")
        .with(r -> { r.setRemoteAddr("198.51.100.42"); return r; });
    assertThat(mvc.perform(forgot).andReturn().getResponse().getStatus()).isEqualTo(202);
    MvcResult limited = mvc.perform(forgot).andReturn();
    assertThat(limited.getResponse().getStatus()).isEqualTo(429);
    assertGolden("problem-429.json", limited);
  }
}
