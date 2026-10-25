package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.web.security.RefreshCookies;
import jakarta.servlet.http.Cookie;
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

/**
 * Bloco L3 (docs/38 §5, D-114): o refresh token vive num cookie HttpOnly e nunca volta para o JavaScript.
 *
 * <p>Cada caso aqui ficava vermelho antes: o login devolvia o refresh no corpo, o refresh só o aceitava no corpo, o
 * logout não apagava cookie nenhum e não existia defesa de CSRF para o cookie.
 */
@SpringBootTest(properties = {"scores.security.rate-limit.limits.login=10000", "scores.security.rate-limit.limits.register=10000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionCookieIntegrationTest {
  // senhas geradas na execução: nenhum segredo fixo em teste (docs/01 §6)
  private static final String SENHA = "T" + UUID.randomUUID();
  @Autowired MockMvc mvc;
  @Autowired RefreshCookies cookies;

  private static String json(Object o) { return Jsons.MAPPER.writeValueAsString(o); }

  private static Map<String, Object> body(MvcResult r) throws Exception {
    return Jsons.MAPPER.readValue(r.getResponse().getContentAsString(), new TypeReference<>() {});
  }

  private String register() throws Exception {
    String email = "cookie-" + UUID.randomUUID() + "@scoresaf.test";
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", email, "password", SENHA, "displayName", "Jogadora", "locale", "pt-BR", "marketingConsent", false))))
        .andReturn();
    return email;
  }

  private MvcResult login(String email) throws Exception {
    return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", email, "password", SENHA)))).andReturn();
  }

  @Test
  void login_poe_o_refresh_no_cookie_httponly_e_nao_no_corpo() throws Exception {
    MvcResult r = login(register());
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(r).get("refreshToken")).as("o JavaScript não pode ver o refresh token").isNull();
    assertThat(body(r).get("accessToken")).isNotNull();

    Cookie cookie = r.getResponse().getCookie(cookies.nome());
    assertThat(cookie).as("cookie %s ausente", cookies.nome()).isNotNull();
    assertThat(cookie.getValue()).isNotBlank();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.getMaxAge()).isPositive();
    assertThat(r.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict");
  }

  @Test
  void refresh_renova_pelo_cookie_com_o_cabecalho_proprio() throws Exception {
    MvcResult entrada = login(register());
    Cookie cookie = entrada.getResponse().getCookie(cookies.nome());

    MvcResult r = mvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header(RefreshCookies.HEADER_PEDIDO, "1")).andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    assertThat(body(r).get("accessToken")).isNotNull();
    assertThat(body(r).get("refreshToken")).isNull();

    Cookie rotacionado = r.getResponse().getCookie(cookies.nome());
    assertThat(rotacionado).isNotNull();
    assertThat(rotacionado.getValue()).as("rotação: o cookie novo não repete o anterior").isNotEqualTo(cookie.getValue());
  }

  @Test
  void cookie_sem_o_cabecalho_proprio_nao_renova_a_sessao() throws Exception {
    // é o pedido que um formulário de outro site conseguiria montar: cookie vai junto, cabeçalho não.
    MvcResult entrada = login(register());
    Cookie cookie = entrada.getResponse().getCookie(cookies.nome());

    MvcResult r = mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(401);
    assertThat(r.getResponse().getCookie(cookies.nome())).as("nada de token novo").isNull();
  }

  @Test
  void logout_apaga_o_cookie_manda_clear_site_data_e_derruba_o_access_token() throws Exception {
    String email = register();
    MvcResult entrada = login(email);
    String access = "Bearer " + body(entrada).get("accessToken");
    Cookie cookie = entrada.getResponse().getCookie(cookies.nome());
    assertThat(mvc.perform(get("/api/v1/me/settings").header("Authorization", access)).andReturn().getResponse().getStatus()).isEqualTo(200);

    MvcResult saida = mvc.perform(post("/api/v1/auth/logout").cookie(cookie).header(RefreshCookies.HEADER_PEDIDO, "1")).andReturn();
    assertThat(saida.getResponse().getStatus()).isEqualTo(204);
    assertThat(saida.getResponse().getHeader("Clear-Site-Data")).isEqualTo("\"cache\", \"cookies\", \"storage\"");
    Cookie apagado = saida.getResponse().getCookie(cookies.nome());
    assertThat(apagado).isNotNull();
    assertThat(apagado.getMaxAge()).isZero();

    assertThat(mvc.perform(get("/api/v1/me/settings").header("Authorization", access)).andReturn().getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void refresh_sem_cookie_e_sem_corpo_responde_401_e_nao_500() throws Exception {
    MvcResult r = mvc.perform(post("/api/v1/auth/refresh")).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(401);
  }
}
