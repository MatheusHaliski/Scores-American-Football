package br.com.velion.scoresaf;

import static br.com.velion.scoresaf.CareerFixtures.body;
import static br.com.velion.scoresaf.CareerFixtures.call;
import static br.com.velion.scoresaf.CareerFixtures.club;
import static br.com.velion.scoresaf.CareerFixtures.create;
import static br.com.velion.scoresaf.CareerFixtures.id;
import static br.com.velion.scoresaf.MatchFixtures.level;
import static br.com.velion.scoresaf.MatchFixtures.play;
import static br.com.velion.scoresaf.MatchFixtures.state;
import static br.com.velion.scoresaf.MatchFixtures.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.application.port.out.persistence.AuditLogRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.domain.account.AuditLog;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.web.security.TokenService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;

/**
 * Escritas concorrentes na partida (revisão do bloco 6b, D-91): dois {@code FULL} com a mesma versão, ou dois {@code play} da mesma
 * semana, chegando juntos. A segunda escrita espera a trava da primeira e recebe 409 (partida encerrada, versão velha ou
 * {@code CONCURRENT_UPDATE}), nunca 500 de chave única; a auditoria de sucesso só fica gravada para a transação que confirmou.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchConcurrencyIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;
  @Autowired TokenService tokens;
  @Autowired MatchSessionRepository matchSessions;
  @Autowired AuditLogRepository auditLog;
  @Autowired AuditService audit;
  @Autowired TransactionTemplate tx;

  private record Career(String auth, String id) {}
  private record Reply(int status, String body) {}

  private Career career(String name, String club, long seed) throws Exception {
    UUID defaultWorld = seeder.seedIfMissing().getId();
    String auth = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    level(mvc, auth, "NONE");
    Map<String, Object> c = create(mvc, auth, body(name, null, club(mvc, auth, defaultWorld, club), seed));
    return new Career(auth, id(c));
  }

  /** Dispara as chamadas juntas (latch de largada) e devolve status e corpo de cada uma. */
  private static List<Reply> together(int n, Callable<MvcResult> call) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(n);
    CountDownLatch largada = new CountDownLatch(1);
    List<Future<Reply>> respostas = new ArrayList<>();
    try {
      for (int i = 0; i < n; i++) {
        Callable<Reply> tarefa = () -> {
          largada.await();
          MvcResult r = call.call();
          return new Reply(r.getResponse().getStatus(), r.getResponse().getContentAsString());
        };
        respostas.add(pool.submit(tarefa));
      }
      largada.countDown();
      List<Reply> out = new ArrayList<>();
      for (Future<Reply> f : respostas) out.add(f.get());
      return out;
    } finally {
      pool.shutdownNow();
    }
  }

  private long audited(String action, String resourceId) {
    return auditLog.findByActionOrderByTimestampDesc(action, PageRequest.of(0, 500)).stream().map(AuditLog::getResourceId).filter(resourceId::equals).count();
  }

  @Test
  void dois_full_com_a_mesma_versao_dao_um_200_e_um_409_nunca_500() throws Exception {
    Career c = career("Corrida " + UUID.randomUUID().toString().substring(0, 6), "NES", 31L);
    String mid = (String) play(mvc, c.auth(), c.id()).get("matchId");
    long v = version(state(mvc, c.auth(), mid));

    List<Reply> replies = together(2, () -> call(mvc, post("/api/v1/matches/" + mid + "/advance"), c.auth(), Map.of("mode", "FULL", "version", v)));

    assertThat(replies).as("%s", replies).extracting(Reply::status).containsExactlyInAnyOrder(200, 409);
    Reply conflict = replies.stream().filter(r -> r.status() == 409).findFirst().orElseThrow();
    assertThat((String) map(conflict).get("code")).isIn("MATCH_FINISHED", "MATCH_VERSION_CONFLICT", "CONCURRENT_UPDATE");
    assertThat(state(mvc, c.auth(), mid)).containsEntry("status", "FINISHED");
    // o fim da partida foi gravado uma vez só: a transação desfeita não deixa MATCH_SIMULATED de sucesso na auditoria
    String fixtureId = (String) state(mvc, c.auth(), mid).get("fixtureId");
    assertThat(audited(AuditActions.MATCH_SIMULATED, fixtureId)).isEqualTo(1);
  }

  @Test
  void dois_play_da_mesma_semana_criam_uma_partida_so_e_nunca_500() throws Exception {
    Career c = career("Largada " + UUID.randomUUID().toString().substring(0, 6), "CRU", 32L);

    List<Reply> replies = together(2, () -> call(mvc, post("/api/v1/careers/" + c.id() + "/weeks/current/play"), c.auth(), null));

    assertThat(replies).as("%s", replies).extracting(Reply::status).allMatch(s -> s == 200 || s == 409).contains(200);
    List<Object> ids = new ArrayList<>();
    for (Reply r : replies) if (r.status() == 200) ids.add(map(r).get("matchId"));
    assertThat(ids).doesNotContainNull().allMatch(ids.get(0)::equals);
    String mid = (String) ids.get(0);
    assertThat(matchSessions.findByCareerIdOrderByCreatedAtAsc(UUID.fromString(c.id()))).singleElement()
        .satisfies(s -> assertThat(s.getId()).hasToString(mid));
    assertThat(audited(AuditActions.MATCH_STARTED, mid)).as("partida iniciada auditada uma vez").isEqualTo(1);
  }

  @Test
  void auditoria_de_sucesso_so_fica_gravada_se_a_transacao_confirmar() {
    String desfeita = UUID.randomUUID().toString();
    String confirmada = UUID.randomUUID().toString();
    tx.executeWithoutResult(s -> {
      audit.ok(AuditActions.MATCH_SIMULATED, "Fixture", desfeita, "teste");
      s.setRollbackOnly();
    });
    tx.executeWithoutResult(s -> audit.ok(AuditActions.MATCH_SIMULATED, "Fixture", confirmada, "teste"));
    // recusa continua gravada na hora, mesmo com rollback (RNF7)
    String recusa = UUID.randomUUID().toString();
    tx.executeWithoutResult(s -> {
      audit.denied(AuditActions.ACCESS_DENIED, "Fixture", recusa, "teste");
      s.setRollbackOnly();
    });
    assertThat(audited(AuditActions.MATCH_SIMULATED, desfeita)).as("sucesso de transação desfeita").isZero();
    assertThat(audited(AuditActions.MATCH_SIMULATED, confirmada)).as("sucesso confirmado").isEqualTo(1);
    assertThat(audited(AuditActions.ACCESS_DENIED, recusa)).as("recusa com rollback").isEqualTo(1);
  }

  private static Map<String, Object> map(Reply r) throws Exception {
    return Jsons.MAPPER.readValue(r.body(), new TypeReference<>() {});
  }
}
