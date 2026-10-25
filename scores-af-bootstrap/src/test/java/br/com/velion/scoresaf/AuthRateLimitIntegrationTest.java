package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

/** Limite por IP + rota (bloco 3): o 31º login em 10 min recebe 429; X-Forwarded-For forjado e o fim do IPv6 não abrem balde novo. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitIntegrationTest {
  // senhas geradas na execução: nenhum segredo fixo em teste (docs/01 §6)
  private static final String SENHA = "T" + UUID.randomUUID();
  @Autowired MockMvc mvc;

  private MvcResult login(String remoteAddr, String forwardedFor) throws Exception {
    MockHttpServletRequestBuilder b = post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"ninguem@scoresaf.test\",\"password\":\"" + SENHA + "\"}")
        .with(r -> { r.setRemoteAddr(remoteAddr); return r; });
    if (forwardedFor != null) b.header("X-Forwarded-For", forwardedFor);
    return mvc.perform(b).andReturn();
  }

  @Test
  void trigesimo_primeiro_login_do_mesmo_ip_recebe_429_e_xff_forjado_nao_muda_o_balde() throws Exception {
    for (int i = 0; i < 30; i++) assertThat(login("198.51.100.10", "10.0.0." + i).getResponse().getStatus()).isEqualTo(401);
    MvcResult blocked = login("198.51.100.10", "203.0.113.99");
    assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
    assertThat(blocked.getResponse().getHeader("Retry-After")).isEqualTo("600");
    assertThat(login("198.51.100.11", null).getResponse().getStatus()).as("outro IP, outro balde").isEqualTo(401);
  }

  @Test
  void ipv6_no_mesmo_64_divide_o_balde() throws Exception {
    for (int i = 0; i < 30; i++) assertThat(login("2001:db8:aa:1::" + Integer.toHexString(i + 1), null).getResponse().getStatus()).isEqualTo(401);
    assertThat(login("2001:db8:aa:1:ffff::1", null).getResponse().getStatus()).isEqualTo(429);
    assertThat(login("2001:db8:aa:2::1", null).getResponse().getStatus()).as("outro /64").isEqualTo(401);
  }
}
