package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.application.port.out.notify.EmailPort;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;

/**
 * Contrato da plataforma que o front do bloco 3 usa e que é próprio do futebol americano: RF10 com duração do quarto e nível de
 * chamada de jogada (D-10; fora de 1–15 é 400 INVALID_QUARTER_DURATION, traduzido), cadastro 201 {userId} e o link de
 * redefinição de senha em {@code /auth/reset?token=…} no front.
 */
@SpringBootTest(properties = {"scores.security.rate-limit.limits.login=10000", "scores.security.rate-limit.limits.register=10000",
    "scores.app.cors-allowed-origins=https://front.scoresaf.test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountPlatformIntegrationTest {
  // senha gerada na execução: nenhum segredo fixo em teste (docs/01 §6)
  private static final String SENHA = "T" + UUID.randomUUID();

  @Autowired MockMvc mvc;
  @MockitoBean EmailPort email;

  private static String json(Object o) { return Jsons.MAPPER.writeValueAsString(o); }

  private static Map<String, Object> body(MvcResult r) throws Exception {
    return Jsons.MAPPER.readValue(r.getResponse().getContentAsString(), new TypeReference<>() {});
  }

  private String register() throws Exception {
    String mail = "plat-" + UUID.randomUUID() + "@scoresaf.test";
    MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", mail, "password", SENHA, "displayName", "Jogadora", "locale", "pt-BR", "marketingConsent", false)))).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(201);
    assertThat(body(r)).containsOnlyKeys("userId");
    return mail;
  }

  private String bearer(String mail) throws Exception {
    MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", mail, "password", SENHA)))).andReturn();
    return "Bearer " + body(r).get("accessToken");
  }

  @Test
  void configuracoes_aceitam_duracao_do_quarto_e_nivel_de_chamada_de_jogada() throws Exception {
    String auth = bearer(register());
    MvcResult r = mvc.perform(put("/api/v1/me/settings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("quarterDurationMinutes", 5, "playCallingLevel", "ALL_PLAYS")))).andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    assertThat(body(r)).containsEntry("quarterDurationMinutes", 5).containsEntry("playCallingLevel", "ALL_PLAYS").doesNotContainKey("matchSpeed");
    assertThat(body(mvc.perform(get("/api/v1/me/settings").header("Authorization", auth)).andReturn()))
        .containsEntry("quarterDurationMinutes", 5).containsEntry("playCallingLevel", "ALL_PLAYS");
  }

  @Test
  void duracao_do_quarto_fora_da_faixa_responde_400_traduzido() throws Exception {
    String auth = bearer(register());
    for (int minutes : new int[] {0, 16}) {
      MvcResult r = mvc.perform(put("/api/v1/me/settings").header("Authorization", auth).header("Accept-Language", "en-US")
          .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("quarterDurationMinutes", minutes)))).andReturn();
      assertThat(r.getResponse().getStatus()).isEqualTo(400);
      assertThat(body(r)).containsEntry("code", "INVALID_QUARTER_DURATION").containsEntry("locale", "en-US");
      assertThat(String.valueOf(body(r).get("message"))).contains("between 1 and 15");
    }
    MvcResult nivel = mvc.perform(put("/api/v1/me/settings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("playCallingLevel", "SOME")))).andReturn();
    assertThat(nivel.getResponse().getStatus()).isEqualTo(422);
    assertThat(body(nivel)).containsEntry("code", "INVALID_VALUE");
  }

  @Test
  void link_de_redefinicao_de_senha_aponta_para_auth_reset_no_front() throws Exception {
    String mail = register();
    assertThat(mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", mail))))
        .andReturn().getResponse().getStatus()).isEqualTo(202);
    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(email).send(eq(mail), anyString(), text.capture());
    assertThat(text.getValue()).containsPattern("https://front\\.scoresaf\\.test/auth/reset\\?token=[A-Za-z0-9_-]{20,}");
  }
}
