package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.port.out.persistence.AuditLogRepository;
import br.com.velion.scoresaf.application.security.PasswordHasher;
import br.com.velion.scoresaf.application.usecase.rf02.ForgotPasswordUseCase;
import br.com.velion.scoresaf.application.usecase.rf02.TokenHashing;
import br.com.velion.scoresaf.domain.account.AuditLog;
import br.com.velion.scoresaf.application.common.json.Jsons;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;

/**
 * Bloco 3 pela API: bloqueio por conta com reserva atômica, teto de hashes (0 respostas 500), reconferência de senha com 429,
 * token recusado depois do logout (família de sessão) e auditoria do login falho persistida. Os limites por IP ficam altos
 * aqui para isolar o bloqueio por conta (o limite por IP tem teste próprio).
 */
@SpringBootTest(properties = {"scores.security.rate-limit.limits.login=10000", "scores.security.rate-limit.limits.register=10000",
    "scores.security.rate-limit.limits.password-change=10000", "scores.security.rate-limit.limits.password-reset=10000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthHardeningIntegrationTest {
  // senhas geradas na execução: nenhum segredo fixo em teste (docs/01 §6)
  private static final String SENHA = "T" + UUID.randomUUID();
  private static final String NOVA = "N" + UUID.randomUUID();
  private static final String ERRADA = "E" + UUID.randomUUID();
  @Autowired MockMvc mvc;
  @Autowired PasswordHasher hasher;
  @Autowired SessionCachePort sessionCache;
  @Autowired AuditLogRepository auditLog;
  @Autowired br.com.velion.scoresaf.web.security.RefreshCookies cookies;

  private static String json(Object o) { return Jsons.MAPPER.writeValueAsString(o); }

  private static Map<String, Object> body(MvcResult r) throws Exception {
    return Jsons.MAPPER.readValue(r.getResponse().getContentAsString(), new TypeReference<>() {});
  }

  private String register(String password) throws Exception {
    String email = "u-" + UUID.randomUUID() + "@scoresaf.test";
    MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", email, "password", password, "displayName", "Jogadora", "locale", "pt-BR", "marketingConsent", false)))).andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isIn(200, 201);
    return email;
  }

  private MvcResult login(String email, String password) throws Exception {
    return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email, "password", password)))).andReturn();
  }

  @Test
  void conta_bloqueada_recebe_401_generico_ate_com_a_senha_certa_e_a_redefinicao_por_email_libera() throws Exception {
    String email = register(SENHA);
    for (int i = 0; i < 5; i++) assertThat(login(email, ERRADA + i).getResponse().getStatus()).isEqualTo(401);
    long hashes = hasher.evaluations();
    MvcResult blocked = login(email, SENHA);
    assertThat(blocked.getResponse().getStatus()).isEqualTo(401);
    assertThat(body(blocked).get("code")).isEqualTo("AUTH_FAILED");
    assertThat(hasher.evaluations()).as("bloqueada: nenhum hash").isEqualTo(hashes);

    // redefinição por e-mail (o token iria por e-mail; aqui entra direto no cache) tira a conta do bloqueio
    String token = "t-" + UUID.randomUUID();
    UUID userId = userIdOf(email);
    sessionCache.put(ForgotPasswordUseCase.RESET_PREFIX + TokenHashing.sha256Hex(token), userId, Duration.ofMinutes(5));
    assertThat(mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("token", token, "newPassword", NOVA)))).andReturn().getResponse().getStatus()).isEqualTo(204);
    assertThat(login(email, NOVA).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void quarenta_logins_paralelos_com_senha_errada_avaliam_no_maximo_5_hashes() throws Exception {
    String email = register(SENHA);
    long before = hasher.evaluations();
    List<Integer> statuses = parallel(40, () -> login(email, ERRADA).getResponse().getStatus());
    assertThat(statuses).containsOnly(401);
    assertThat(hasher.evaluations() - before).isLessThanOrEqualTo(5);
  }

  @Test
  void sessenta_logins_paralelos_nunca_respondem_500() throws Exception {
    List<String> emails = new ArrayList<>();
    for (int i = 0; i < 6; i++) emails.add(register(SENHA));
    List<Integer> statuses = parallel(60, new java.util.concurrent.Callable<>() {
      final java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
      @Override public Integer call() throws Exception { return login(emails.get(n.getAndIncrement() % emails.size()), SENHA).getResponse().getStatus(); }
    });
    assertThat(statuses).doesNotContain(500).allSatisfy(s -> assertThat(s).isIn(200, 401, 503));
  }

  @Test
  void sexto_chute_da_senha_atual_na_troca_de_senha_recebe_429() throws Exception {
    String email = register(SENHA);
    String access = "Bearer " + body(login(email, SENHA)).get("accessToken");
    for (int i = 0; i < 5; i++) {
      assertThat(mvc.perform(put("/api/v1/me/password").header("Authorization", access).contentType(MediaType.APPLICATION_JSON)
          .content(json(Map.of("currentPassword", ERRADA + i, "newPassword", NOVA)))).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
    MvcResult sixth = mvc.perform(put("/api/v1/me/password").header("Authorization", access).contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("currentPassword", SENHA, "newPassword", NOVA)))).andReturn();
    assertThat(sixth.getResponse().getStatus()).isEqualTo(429);
    assertThat(body(sixth).get("code")).isEqualTo("TOO_MANY_ATTEMPTS");
    assertThat(sixth.getResponse().getHeader("Retry-After")).isEqualTo("900");
  }

  @Test
  void token_de_sessao_encerrada_e_recusado_na_hora() throws Exception {
    String email = register(SENHA);
    MvcResult login = login(email, SENHA);
    String access = "Bearer " + body(login).get("accessToken");
    assertThat(mvc.perform(get("/api/v1/me/settings").header("Authorization", access)).andReturn().getResponse().getStatus()).isEqualTo(200);
    // o refresh vive no cookie HttpOnly (D-114): o logout manda o cookie e o cabeçalho próprio, não o token no corpo
    mvc.perform(post("/api/v1/auth/logout").cookie(login.getResponse().getCookie(cookies.nome()))
        .header(br.com.velion.scoresaf.web.security.RefreshCookies.HEADER_PEDIDO, "1"));
    MvcResult after = mvc.perform(get("/api/v1/me/settings").header("Authorization", access)).andReturn();
    assertThat(after.getResponse().getStatus()).isEqualTo(401);
    assertThat(body(after).get("code")).isEqualTo("UNAUTHENTICATED");
  }

  @Test
  void login_falho_fica_na_auditoria_mesmo_com_rollback() throws Exception {
    String email = register(SENHA);
    login(email, ERRADA);
    List<AuditLog> failed = auditLog.findByActionOrderByTimestampDesc("LOGIN_FAILED", PageRequest.of(0, 50)).getContent();
    assertThat(failed).anySatisfy(a -> assertThat(a.getResourceId()).isEqualTo(userIdOf(email).toString()));
  }

  @Test
  void nome_reservado_no_cadastro_recebe_422() throws Exception {
    MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", "adm-" + UUID.randomUUID() + "@scoresaf.test", "password", SENHA, "displayName", "Ádmin", "locale", "pt-BR", "marketingConsent", false)))).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    assertThat(body(r).get("code")).isEqualTo("NAME_RESERVED");
  }

  @Autowired br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository users;

  private UUID userIdOf(String email) {
    return users.findByEmailHash(br.com.velion.scoresaf.domain.common.Hashing.sha256Hex(email)).orElseThrow().getId();
  }

  private static List<Integer> parallel(int n, java.util.concurrent.Callable<Integer> call) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(n);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> futures = new ArrayList<>();
    for (int i = 0; i < n; i++) futures.add(pool.submit(() -> { start.await(); return call.call(); }));
    start.countDown();
    List<Integer> out = new ArrayList<>();
    for (Future<Integer> f : futures) out.add(f.get());
    pool.shutdown();
    return out;
  }
}
