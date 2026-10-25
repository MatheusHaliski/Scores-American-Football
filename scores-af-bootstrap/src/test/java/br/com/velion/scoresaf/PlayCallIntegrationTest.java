package br.com.velion.scoresaf;

import static br.com.velion.scoresaf.CareerFixtures.assertProblem;
import static br.com.velion.scoresaf.CareerFixtures.body;
import static br.com.velion.scoresaf.CareerFixtures.call;
import static br.com.velion.scoresaf.CareerFixtures.club;
import static br.com.velion.scoresaf.CareerFixtures.create;
import static br.com.velion.scoresaf.CareerFixtures.id;
import static br.com.velion.scoresaf.CareerFixtures.map;
import static br.com.velion.scoresaf.CareerFixtures.ok;
import static br.com.velion.scoresaf.MatchFixtures.advance;
import static br.com.velion.scoresaf.MatchFixtures.decision;
import static br.com.velion.scoresaf.MatchFixtures.level;
import static br.com.velion.scoresaf.MatchFixtures.live;
import static br.com.velion.scoresaf.MatchFixtures.play;
import static br.com.velion.scoresaf.MatchFixtures.state;
import static br.com.velion.scoresaf.MatchFixtures.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.domain.match.MatchSession;
import br.com.velion.scoresaf.web.security.TokenService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RF62 e RF29 de ponta a ponta: chamada de jogada (nível da conta respeitado, opção fora da lista, versão, decisão inexistente ou já
 * resolvida, prazo vencido com a IA chamando e o resultado gravado) e o playbook do intervalo (só com a bola parada, validação,
 * versão, efeito do próximo snap em diante, tempo técnico).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlayCallIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;
  @Autowired TokenService tokens;
  @Autowired MatchSessionRepository matchSessions;
  @Autowired TransactionTemplate tx;

  private record Game(String auth, String careerId, String mid) {}

  private Game game(String level, long seed) throws Exception {
    UUID defaultWorld = seeder.seedIfMissing().getId();
    String auth = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    level(mvc, auth, level);
    Map<String, Object> c = create(mvc, auth, body("Chamada " + UUID.randomUUID().toString().substring(0, 6), null, club(mvc, auth, defaultWorld, "PAV"), seed));
    return new Game(auth, id(c), (String) play(mvc, auth, id(c)).get("matchId"));
  }

  private MvcResult call(Game g, String decisionId, Object option, long version) throws Exception {
    return CareerFixtures.call(mvc, post("/api/v1/matches/" + g.mid() + "/play-calls/" + decisionId), g.auth(), Map.of("optionCode", option, "version", version));
  }

  /** Vence o prazo da decisão aberta direto no banco (o relógio do teste não espera 30 s). */
  private void expire(Game g) {
    tx.executeWithoutResult(t -> {
      MatchSession s = matchSessions.findById(UUID.fromString(g.mid())).orElseThrow();
      s.setDecisionDeadlineAt(Instant.now().minusSeconds(1));
      matchSessions.save(s);
    });
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> options(Map<String, Object> d) { return (List<Map<String, Object>>) d.get("options"); }

  @Test
  @SuppressWarnings("unchecked")
  void chamada_de_jogada_valida_e_controles() throws Exception {
    Game g = game("KEY_MOMENTS", 20261025L);
    Map<String, Object> s = advance(mvc, g.auth(), g.mid(), "TO_DECISION");
    assertThat(s).containsEntry("status", "AWAITING_DECISION").containsEntry("advanceModes", List.of()).containsKey("decisionDeadlineAt")
        .containsEntry("decisionTimeoutSeconds", 30);
    Map<String, Object> d = decision(s);
    String did = (String) d.get("id");
    assertThat((String) d.get("situation")).isIn("FOURTH_DOWN", "TWO_MINUTE", "OVERTIME", "CONVERSION");
    long v = version(s);

    // decisão aberta segura o avanço
    assertProblem(CareerFixtures.call(mvc, post("/api/v1/matches/" + g.mid() + "/advance"), g.auth(), Map.of("mode", "FULL")), 409, "DECISION_PENDING");
    // opção fora da lista, versão velha, decisão inexistente
    MvcResult notOffered = call(g, did, "HAIL_MARY", v);
    assertProblem(notOffered, 422, "PLAY_NOT_OFFERED");
    assertThat((List<Object>) ((Map<String, Object>) map(notOffered).get("details")).get("options")).isNotEmpty();
    assertProblem(call(g, did, options(d).get(0).get("code"), v - 1), 409, "MATCH_VERSION_CONFLICT");
    assertProblem(call(g, "dec-999999", options(d).get(0).get("code"), v), 404, "DECISION_NOT_FOUND");
    assertThat(state(mvc, g.auth(), g.mid())).containsEntry("status", "AWAITING_DECISION");

    // escolha válida: a jogada é resolvida na hora, chamada pelo manager
    String chosen = (String) options(d).get(options(d).size() - 1).get("code");
    Map<String, Object> after = ok(call(g, did, chosen, v));
    assertThat(version(after)).isGreaterThan(v);
    List<Map<String, Object>> last = (List<Map<String, Object>>) live(after).get("lastEvents");
    assertThat(last).anySatisfy(e -> assertThat(e).containsEntry("callSource", "USER"));
    // a mesma decisão de novo: já resolvida
    MvcResult closed = call(g, did, chosen, version(after));
    assertProblem(closed, 409, "DECISION_CLOSED");
    assertThat((Map<String, Object>) map(closed).get("details")).containsEntry("resolvedOption", chosen).containsEntry("auto", false);
  }

  @Test
  @SuppressWarnings("unchecked")
  void prazo_vencido_a_ia_chama_e_o_resultado_fica_gravado() throws Exception {
    Game g = game("KEY_MOMENTS", 20261025L);
    Map<String, Object> s = advance(mvc, g.auth(), g.mid(), "TO_DECISION");
    Map<String, Object> d = decision(s);
    String did = (String) d.get("id");
    long v = version(s);
    expire(g);
    // chamada fora do prazo: 409 DECISION_CLOSED, mas a decisão automática ficou gravada (a transação não é desfeita)
    MvcResult late = call(g, did, options(d).get(0).get("code"), v);
    assertProblem(late, 409, "DECISION_CLOSED");
    assertThat((Map<String, Object>) map(late).get("details")).containsEntry("auto", true).containsEntry("resolvedOption", d.get("autoOptionCode"));
    Map<String, Object> now = state(mvc, g.auth(), g.mid());
    assertThat(version(now)).isGreaterThan(v);
    assertThat(decision(now) == null || !did.equals(decision(now).get("id"))).isTrue();
    List<Map<String, Object>> events = MatchFixtures.events(mvc, g.auth(), g.mid(), 0, 1000);
    assertThat(events).anySatisfy(e -> assertThat(e).containsEntry("callSource", "AUTO"));

    // próximo momento decisivo: prazo vencido e avanço → a IA decide e a partida segue
    s = now;
    while (!"AWAITING_DECISION".equals(s.get("status"))) s = advance(mvc, g.auth(), g.mid(), "TO_DECISION");
    String next = (String) decision(s).get("id");
    expire(g);
    Map<String, Object> moved = advance(mvc, g.auth(), g.mid(), "PLAY");
    assertThat(decision(moved) == null || !next.equals(decision(moved).get("id"))).isTrue();
    assertThat(MatchFixtures.events(mvc, g.auth(), g.mid(), 0, 1000).stream().filter(e -> "AUTO".equals(e.get("callSource"))).count()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void nivel_da_conta_decide_quando_a_partida_para() throws Exception {
    Game all = game("ALL_PLAYS", 77L);
    Map<String, Object> s = advance(mvc, all.auth(), all.mid(), "TO_DECISION");
    assertThat(s).containsEntry("status", "AWAITING_DECISION").containsEntry("playCallingLevel", "ALL_PLAYS");
    assertThat(decision(s)).containsEntry("situation", "ALL_PLAYS");
    Game none = game("NONE", 77L);
    Map<String, Object> done = advance(mvc, none.auth(), none.mid(), "FULL");
    assertThat(done).containsEntry("status", "FINISHED").containsEntry("playCallingLevel", "NONE");
    assertThat(MatchFixtures.events(mvc, none.auth(), none.mid(), 0, 1000)).noneMatch(e -> "USER".equals(e.get("callSource")) || "AUTO".equals(e.get("callSource")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void playbook_do_intervalo_so_com_a_bola_parada_e_vale_do_proximo_snap() throws Exception {
    Game g = game("NONE", 515L);
    String base = "/api/v1/matches/" + g.mid() + "/decisions";
    Map<String, Object> s = state(mvc, g.auth(), g.mid());
    // antes do kickoff: tempo técnico não cabe; ajuste do plano cabe
    assertProblem(CareerFixtures.call(mvc, post(base), g.auth(), Map.of("version", version(s), "adjustments", List.of(Map.of("type", "TIMEOUT")))), 422,
        "TIMEOUT_NOT_ALLOWED");
    Map<String, Object> plan = Map.of("passRatio", 30, "fourthDownAggression", "AGGRESSIVE", "tempo", "NORMAL", "defensiveFocus", "STOP_RUN",
        "blitzFrequency", "HIGH", "baseDefense", "BASE_4_3");
    Map<String, Object> adjusted = ok(CareerFixtures.call(mvc, post(base), g.auth(), Map.of("version", version(s), "adjustments",
        List.of(Map.of("type", "GAME_PLAN", "gamePlan", plan)))));
    assertThat(version(adjusted)).isEqualTo(version(s) + 1);
    assertThat(adjusted).containsEntry("status", "NOT_STARTED");
    // rolando: ajuste recusado
    s = advance(mvc, g.auth(), g.mid(), "PLAY");
    assertThat(s).containsEntry("status", "LIVE");
    assertProblem(CareerFixtures.call(mvc, post(base), g.auth(), Map.of("version", version(s), "adjustments", List.of(Map.of("type", "GAME_PLAN", "gamePlan", plan)))),
        422, "ADJUSTMENT_NOT_ALLOWED");
    // intervalo: playbook incompleto recusado, versão velha recusada, válido aceito
    while (!"HALFTIME".equals(s.get("status"))) s = advance(mvc, g.auth(), g.mid(), "QUARTER");
    int halftimeSeq = ((Number) ok(CareerFixtures.call(mvc, get("/api/v1/matches/" + g.mid() + "/events?limit=1000"), g.auth(), null)).get("lastSeq")).intValue();
    Map<String, Object> noRun = Map.of("situations", List.of(Map.of("situation", "EARLY_DOWN", "concepts",
        List.of(Map.of("item", "INSIDE_RUN", "weight", 0), Map.of("item", "OUTSIDE_RUN", "weight", 0)))));
    assertProblem(CareerFixtures.call(mvc, post(base), g.auth(), Map.of("version", version(s), "adjustments", List.of(Map.of("type", "PLAYBOOK", "playbook", noRun)))),
        422, "PLAYBOOK_INCOMPLETE");
    List<Map<String, Object>> situations = new java.util.ArrayList<>();
    for (String sit : List.of("EARLY_DOWN", "THIRD_DOWN", "THIRD_AND_LONG", "RED_ZONE", "TWO_MINUTE")) {
      situations.add(Map.of("situation", sit, "concepts", List.of(Map.of("item", "DEEP_PASS", "weight", 0), Map.of("item", "MEDIUM_PASS", "weight", 0),
          Map.of("item", "PLAY_ACTION", "weight", 0))));
    }
    Map<String, Object> book = Map.of("situations", situations);
    assertProblem(CareerFixtures.call(mvc, post(base), g.auth(), Map.of("version", version(s) - 1, "adjustments", List.of(Map.of("type", "PLAYBOOK", "playbook", book)))),
        409, "MATCH_VERSION_CONFLICT");
    Map<String, Object> ht = ok(CareerFixtures.call(mvc, post(base), g.auth(), Map.of("version", version(s), "adjustments",
        List.of(Map.of("type", "PLAYBOOK", "playbook", book)))));
    assertThat(ht).containsEntry("status", "HALFTIME");
    assertProblem(CareerFixtures.call(mvc, post(base), g.auth(), Map.of("version", version(ht), "adjustments", List.of(Map.of("type", "SUBSTITUTION")))), 400,
        "INVALID_ADJUSTMENT");

    Map<String, Object> end = MatchFixtures.finish(mvc, g.auth(), g.mid(), "FULL");
    String userClub = (String) end.get("userClubId");
    List<Map<String, Object>> events = MatchFixtures.events(mvc, g.auth(), g.mid(), 0, 1000);
    List<Object> firstHalf = events.stream().filter(e -> ((Number) e.get("seq")).intValue() <= halftimeSeq && userClub.equals(e.get("possessionClubId"))
        && "AI".equals(e.get("callSource"))).map(e -> e.get("concept")).toList();
    List<Object> secondHalf = events.stream().filter(e -> ((Number) e.get("seq")).intValue() > halftimeSeq && userClub.equals(e.get("possessionClubId"))
        && "AI".equals(e.get("callSource"))).map(e -> e.get("concept")).toList();
    assertThat(firstHalf).containsAnyOf("MEDIUM_PASS", "DEEP_PASS", "PLAY_ACTION");
    assertThat(secondHalf).isNotEmpty().doesNotContain("MEDIUM_PASS", "DEEP_PASS", "PLAY_ACTION");
    Map<String, Object> report = ok(CareerFixtures.call(mvc, get("/api/v1/matches/" + g.mid() + "/report"), g.auth(), null));
    assertThat((List<Map<String, Object>>) report.get("decisions")).extracting(e -> e.get("optionCode")).containsExactly("PLAN_CHANGE", "PLAN_CHANGE");
  }
}
