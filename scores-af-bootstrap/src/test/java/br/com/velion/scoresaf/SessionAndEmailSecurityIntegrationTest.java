package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.usecase.rf46.EmailChangeUseCases;
import br.com.velion.scoresaf.domain.common.Hashing;
import br.com.velion.scoresaf.web.security.RefreshCookies;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Achados da revisão do bloco 3 (#3): reuso de refresh revoga de verdade, refresh simultâneo não bifurca a sessão e o link
 * de troca de e-mail só confirma o endereço que o recebeu.
 */
@SpringBootTest(properties = {"scores.security.rate-limit.limits.login=10000", "scores.security.rate-limit.limits.register=10000",
    "scores.security.rate-limit.limits.refresh=10000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionAndEmailSecurityIntegrationTest {
  // senha gerada na execução: nenhum segredo fixo em teste (docs/01 §6)
  private static final String SENHA = "T" + UUID.randomUUID();
  @Autowired MockMvc mvc;
  @Autowired RefreshCookies cookies;
  @Autowired UserAccountRepository users;
  @Autowired EmailChangeUseCases emailChange;

  private static String json(Object o) { return Jsons.MAPPER.writeValueAsString(o); }

  private String register() throws Exception {
    String email = "sec-" + UUID.randomUUID() + "@scoresaf.test";
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", email, "password", SENHA, "displayName", "Jogadora", "locale", "pt-BR", "marketingConsent", false))))
        .andReturn();
    return email;
  }

  private Cookie login(String email) throws Exception {
    MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", email, "password", SENHA)))).andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return r.getResponse().getCookie(cookies.nome());
  }

  private MvcResult refresh(Cookie c) throws Exception {
    return mvc.perform(post("/api/v1/auth/refresh").cookie(c).header(RefreshCookies.HEADER_PEDIDO, "1")).andReturn();
  }

  @Test
  void reuso_de_refresh_revoga_a_sessao_sucessora_de_verdade() throws Exception {
    Cookie primeiro = login(register());
    MvcResult rotacao = refresh(primeiro);
    assertThat(rotacao.getResponse().getStatus()).isEqualTo(200);
    Cookie sucessor = rotacao.getResponse().getCookie(cookies.nome());

    assertThat(refresh(primeiro).getResponse().getStatus()).as("token já rotacionado").isEqualTo(401);
    // a revogação feita na detecção de reuso precisa ficar gravada apesar do 401
    assertThat(refresh(sucessor).getResponse().getStatus()).as("sucessor depois do reuso").isEqualTo(401);
  }

  @Test
  void refresh_simultaneo_do_mesmo_token_cria_no_maximo_uma_sessao() throws Exception {
    Cookie c = login(register());
    int n = 8;
    ExecutorService pool = Executors.newFixedThreadPool(n);
    CountDownLatch largada = new CountDownLatch(1);
    List<Future<Integer>> respostas = new ArrayList<>();
    try {
      for (int i = 0; i < n; i++) {
        Callable<Integer> tarefa = () -> { largada.await(); return refresh(c).getResponse().getStatus(); };
        respostas.add(pool.submit(tarefa));
      }
      largada.countDown();
      int ok = 0;
      for (Future<Integer> f : respostas) if (f.get() == 200) ok++;
      assertThat(ok).as("refreshes aceitos com o mesmo token").isLessThanOrEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void link_de_troca_de_email_so_confirma_o_endereco_que_o_recebeu() {
    String email;
    try { email = register(); } catch (Exception e) { throw new IllegalStateException(e); }
    UUID userId = users.findByEmailHash(Hashing.sha256Hex(Hashing.normalizeEmail(email))).orElseThrow().getId();
    String enderecoA = "a-" + UUID.randomUUID() + "@scoresaf.test";
    String enderecoB = "b-" + UUID.randomUUID() + "@scoresaf.test";

    String tokenA = emailChange.request(userId, enderecoA, SENHA);
    String tokenB = emailChange.request(userId, enderecoB, SENHA);

    assertThatThrownBy(() -> emailChange.verify(tokenA)).as("link enviado para A não confirma B")
        .isInstanceOf(BusinessRuleException.class).hasMessageContaining("inválido");
    emailChange.verify(tokenB);
    assertThat(users.findById(userId).orElseThrow().getEmail()).isEqualTo(Hashing.normalizeEmail(enderecoB));
  }
}
