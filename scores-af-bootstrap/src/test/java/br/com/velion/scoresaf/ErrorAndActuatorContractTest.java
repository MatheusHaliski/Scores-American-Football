package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.web.security.TokenService;
import java.util.List;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bloco 2 (D-5): corpo de erro único {@code {code, message, details, correlationId, locale}} em todo caminho (controller e
 * Spring Security), mensagens no idioma do {@code Accept-Language} e actuator público só com o health, sem detalhes.
 * Bloco 3: os casos do basquete com usuário de verdade (token inválido, 403 de admin, 404 de recurso do titular, 429 do filtro
 * de limite e actuator negado também ao ADMIN). O limite de login fica em 2 para o 429 sair rápido.
 */
@SpringBootTest(properties = "scores.security.rate-limit.limits.login=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorAndActuatorContractTest {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired TokenService tokens;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;

  private String bearer(UserRole role) { return TestSessions.bearer(users, sessions, tokens, role); }

  private Map<String, Object> body(MvcResult r) throws Exception {
    return json.readValue(r.getResponse().getContentAsString(), new TypeReference<>() {});
  }

  private void assertErrorContract(MvcResult r, int status, String code) throws Exception {
    assertThat(r.getResponse().getStatus()).isEqualTo(status);
    Map<String, Object> b = body(r);
    assertThat(b).containsKeys("code", "message", "details", "correlationId", "locale");
    assertThat(b.get("code")).isEqualTo(code);
    assertThat(b.get("correlationId")).isNotNull().isEqualTo(r.getResponse().getHeader("X-Correlation-Id"));
    assertThat(r.getResponse().getContentAsString()).doesNotContain("trace").doesNotContain("Exception");
  }

  @Test
  void meta_e_publico() throws Exception {
    MvcResult r = mvc.perform(get("/api/v1/meta")).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(r)).containsEntry("sport", "AMERICAN_FOOTBALL").containsEntry("apiVersion", "v1");
  }

  @Test
  void rota_protegida_sem_token_responde_401_no_formato_padrao() throws Exception {
    MvcResult r = mvc.perform(get("/api/v1/me")).andReturn();
    assertErrorContract(r, 401, "UNAUTHENTICATED");
    assertThat(r.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
  }

  @Test
  void escrita_sem_token_responde_401_e_nao_403_de_csrf() throws Exception {
    assertErrorContract(mvc.perform(post("/api/v1/meta")).andReturn(), 401, "UNAUTHENTICATED");
  }

  @Test
  void mensagem_segue_o_accept_language() throws Exception {
    Map<String, Object> pt = body(mvc.perform(get("/api/v1/me")).andReturn());
    Map<String, Object> en = body(mvc.perform(get("/api/v1/me").header("Accept-Language", "en-US")).andReturn());
    Map<String, Object> es = body(mvc.perform(get("/api/v1/me").header("Accept-Language", "es")).andReturn());
    assertThat(pt.get("locale")).isEqualTo("pt-BR");
    assertThat(en.get("locale")).isEqualTo("en-US");
    assertThat(es.get("locale")).isEqualTo("es-ES");
    assertThat(List.of(pt.get("message"), en.get("message"), es.get("message"))).doesNotHaveDuplicates().doesNotContain("UNAUTHENTICATED");
  }

  @Test
  void correlation_id_seguro_do_cliente_e_mantido() throws Exception {
    MvcResult r = mvc.perform(get("/api/v1/me").header("X-Correlation-Id", "teste-af-1")).andReturn();
    assertThat(r.getResponse().getHeader("X-Correlation-Id")).isEqualTo("teste-af-1");
    assertThat(body(r).get("correlationId")).isEqualTo("teste-af-1");
  }

  @Test
  void correlation_id_inseguro_do_cliente_e_trocado() throws Exception {
    MvcResult r = mvc.perform(get("/api/v1/me").header("X-Correlation-Id", "x\r\nInjetado: sim")).andReturn();
    assertThat(r.getResponse().getHeader("X-Correlation-Id")).doesNotContain("Injetado").matches("[0-9a-f-]{36}");
  }

  @Test
  void actuator_publico_so_tem_health_sem_detalhes() throws Exception {
    MvcResult health = mvc.perform(get("/actuator/health")).andReturn();
    assertThat(health.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(health)).containsKey("status").doesNotContainKeys("components", "details");
    for (String path : List.of("/actuator/info", "/actuator/metrics", "/actuator/env", "/actuator/beans", "/actuator/heapdump")) {
      assertThat(mvc.perform(get(path)).andReturn().getResponse().getStatus()).as("anônimo em %s", path).isNotEqualTo(200);
    }
  }

  @Test
  void token_invalido_responde_401_no_formato_padrao() throws Exception {
    assertErrorContract(mvc.perform(get("/api/v1/me/settings").header("Authorization", "Bearer invalido")).andReturn(), 401, "UNAUTHENTICATED");
  }

  @Test
  void rota_de_admin_com_usuario_comum_responde_403_no_formato_padrao() throws Exception {
    assertErrorContract(mvc.perform(get("/api/v1/admin/audit").header("Authorization", bearer(UserRole.PLAYER))).andReturn(), 403, "FORBIDDEN");
  }

  @Test
  void recurso_inexistente_responde_404_no_formato_padrao() throws Exception {
    assertErrorContract(mvc.perform(get("/api/v1/me/export/" + UUID.randomUUID()).header("Authorization", bearer(UserRole.PLAYER))).andReturn(), 404, "NOT_FOUND");
  }

  @Test
  void limite_excedido_responde_429_com_correlation_id_e_retry_after() throws Exception {
    MvcResult last = null;
    String wrong = "E" + UUID.randomUUID(); // gerada na execução (docs/01 §6)
    for (int i = 0; i < 3; i++) {
      last = mvc.perform(post("/api/v1/auth/login").header("X-Correlation-Id", "teste-limite-" + i)
          .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@scoresaf.test\",\"password\":\"" + wrong + "\"}")).andReturn();
    }
    assertErrorContract(last, 429, "RATE_LIMITED");
    assertThat(last.getResponse().getHeader("Retry-After")).isNotBlank();
    assertThat(last.getResponse().getHeader("X-Correlation-Id")).isEqualTo("teste-limite-2");
  }

  @Test
  void actuator_alem_do_health_e_negado_ate_ao_admin() throws Exception {
    String admin = bearer(UserRole.ADMIN);
    for (String path : List.of("/actuator/info", "/actuator/metrics", "/actuator/prometheus", "/actuator/env", "/actuator/beans")) {
      assertThat(mvc.perform(get(path).header("Authorization", admin)).andReturn().getResponse().getStatus()).as("ADMIN em %s", path).isNotEqualTo(200);
    }
  }

  @Test
  void respostas_trazem_os_cabecalhos_de_seguranca() throws Exception {
    MvcResult r = mvc.perform(get("/api/v1/meta").secure(true)).andReturn();
    assertThat(r.getResponse().getHeader("Strict-Transport-Security")).contains("max-age=");
    assertThat(r.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
    assertThat(r.getResponse().getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
  }
}
