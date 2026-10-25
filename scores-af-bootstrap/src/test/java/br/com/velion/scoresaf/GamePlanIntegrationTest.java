package br.com.velion.scoresaf;

import static br.com.velion.scoresaf.CareerFixtures.assertProblem;
import static br.com.velion.scoresaf.CareerFixtures.body;
import static br.com.velion.scoresaf.CareerFixtures.call;
import static br.com.velion.scoresaf.CareerFixtures.club;
import static br.com.velion.scoresaf.CareerFixtures.create;
import static br.com.velion.scoresaf.CareerFixtures.id;
import static br.com.velion.scoresaf.CareerFixtures.map;
import static br.com.velion.scoresaf.CareerFixtures.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.web.security.TokenService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * RF61 e RF7 de ponta a ponta: plano de jogo e playbook (padrão, gravação, versão otimista com 409, validação com 422 e
 * {@code errors[]}), o plano salvo chegando ao motor no kickoff e a preparação do próximo jogo (adversário, confrontos-chave,
 * prontidão, clima). Carreira de outro usuário: 403 auditado, como no 6a.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GamePlanIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;
  @Autowired TokenService tokens;

  String auth;
  String base;
  String careerId;

  @BeforeEach
  void career() throws Exception {
    UUID defaultWorld = seeder.seedIfMissing().getId();
    auth = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    Map<String, Object> c = create(mvc, auth, body("Plano " + UUID.randomUUID().toString().substring(0, 8), null, club(mvc, auth, defaultWorld, "NES"), 61L));
    careerId = id(c);
    base = "/api/v1/careers/" + careerId;
  }

  private static Map<String, Object> plan(long version, Object passRatio, String aggression) {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("version", version);
    b.put("passRatio", passRatio);
    b.put("fourthDownAggression", aggression);
    b.put("tempo", "HURRY_UP");
    b.put("defensiveFocus", "STOP_PASS");
    b.put("blitzFrequency", "HIGH");
    b.put("baseDefense", "BASE_3_4");
    return b;
  }

  /** Playbook com {@code weight} em {@code concepts} em todas as situações. */
  private static Map<String, Object> playbook(long version, int weight, String... concepts) {
    List<Map<String, Object>> situations = new java.util.ArrayList<>();
    for (String s : List.of("EARLY_DOWN", "THIRD_DOWN", "THIRD_AND_LONG", "RED_ZONE", "TWO_MINUTE")) {
      List<Map<String, Object>> w = new java.util.ArrayList<>();
      for (String c : concepts) w.add(Map.of("item", c, "weight", weight));
      situations.add(Map.of("situation", s, "concepts", w));
    }
    return Map.of("version", version, "situations", situations, "coverages", List.of(Map.of("item", "COVER_0", "weight", 0)));
  }

  @Test
  @SuppressWarnings("unchecked")
  void plano_de_jogo_grava_com_versao_e_valida_com_422() throws Exception {
    Map<String, Object> g = ok(call(mvc, get(base + "/game-plan"), auth, null));
    assertThat(g).containsEntry("source", "COACH_STYLE").containsEntry("editable", true).containsEntry("version", 0).containsEntry("passRatio", 58);
    assertThat((Map<String, Object>) g.get("options")).containsEntry("minPassRatio", 20).containsEntry("maxPassRatio", 80);

    Map<String, Object> saved = ok(call(mvc, put(base + "/game-plan"), auth, plan(0, 64, "CONSERVATIVE")));
    assertThat(saved).containsEntry("source", "SAVED").containsEntry("version", 1).containsEntry("passRatio", 64)
        .containsEntry("fourthDownAggression", "CONSERVATIVE").containsEntry("baseDefense", "BASE_3_4");
    assertThat(ok(call(mvc, get(base + "/game-plan"), auth, null))).containsEntry("version", 1).containsEntry("tempo", "HURRY_UP");

    // versão velha: 409 com a versão atual
    MvcResult stale = call(mvc, put(base + "/game-plan"), auth, plan(0, 50, "NORMAL"));
    assertProblem(stale, 409, "GAME_PLAN_VERSION_CONFLICT");
    assertThat((Map<String, Object>) map(stale).get("details")).containsEntry("currentVersion", 1).containsEntry("expectedVersion", 0);

    // fora dos limites e fora do vocabulário: 422 com todos os erros
    MvcResult bad = call(mvc, put(base + "/game-plan"), auth, plan(1, 95, "YOLO"));
    assertProblem(bad, 422, "GAME_PLAN_INVALID");
    List<Map<String, Object>> errors = (List<Map<String, Object>>) ((Map<String, Object>) map(bad).get("details")).get("errors");
    assertThat(errors).extracting(e -> e.get("code") + ":" + e.get("field")).containsExactly("PASS_RATIO_OUT_OF_RANGE:passRatio", "UNKNOWN_VALUE:fourthDownAggression");
    Map<String, Object> nickel = plan(1, 50, "NORMAL");
    nickel.put("baseDefense", "NICKEL");
    assertProblem(call(mvc, put(base + "/game-plan"), auth, nickel), 422, "GAME_PLAN_INVALID");
    // campo obrigatório ausente: 400 da validação do corpo
    Map<String, Object> missing = plan(1, 50, "NORMAL");
    missing.remove("tempo");
    assertProblem(call(mvc, put(base + "/game-plan"), auth, missing), 400, "VALIDATION");
    assertThat(ok(call(mvc, get(base + "/game-plan"), auth, null))).containsEntry("version", 1).containsEntry("passRatio", 64);
  }

  @Test
  @SuppressWarnings("unchecked")
  void playbook_grava_com_versao_e_valida_com_422() throws Exception {
    Map<String, Object> p = ok(call(mvc, get(base + "/playbook"), auth, null));
    assertThat(p).containsEntry("version", 0).containsEntry("customWeights", 0).containsEntry("neutralWeight", 100);
    List<Map<String, Object>> situations = (List<Map<String, Object>>) p.get("situations");
    assertThat(situations).hasSize(5).allSatisfy(s -> assertThat((List<Map<String, Object>>) s.get("concepts")).hasSize(8)
        .allSatisfy(w -> assertThat(w).containsEntry("weight", 100)));

    Map<String, Object> saved = ok(call(mvc, put(base + "/playbook"), auth, playbook(0, 0, "DEEP_PASS")));
    assertThat(saved).containsEntry("version", 1).containsEntry("customWeights", 6);
    assertThat(ok(call(mvc, get(base + "/playbook"), auth, null))).containsEntry("version", 1).containsEntry("customWeights", 6);

    assertProblem(call(mvc, put(base + "/playbook"), auth, playbook(0, 50, "SCREEN")), 409, "PLAYBOOK_VERSION_CONFLICT");
    MvcResult invalid = call(mvc, put(base + "/playbook"), auth, playbook(1, 250, "SCREEN"));
    assertProblem(invalid, 422, "PLAYBOOK_INVALID");
    assertThat((List<Map<String, Object>>) ((Map<String, Object>) map(invalid).get("details")).get("errors")).hasSize(5)
        .allSatisfy(e -> assertThat(e).containsEntry("code", "WEIGHT_OUT_OF_RANGE"));
    MvcResult incomplete = call(mvc, put(base + "/playbook"), auth, playbook(1, 0, "INSIDE_RUN", "OUTSIDE_RUN"));
    assertProblem(incomplete, 422, "PLAYBOOK_INCOMPLETE");
    assertThat((List<Map<String, Object>>) ((Map<String, Object>) map(incomplete).get("details")).get("errors"))
        .extracting(e -> e.get("code")).containsOnly("NO_RUN_CONCEPT");
    assertThat(ok(call(mvc, get(base + "/playbook"), auth, null))).containsEntry("version", 1).containsEntry("customWeights", 6);
  }

  @Test
  @SuppressWarnings("unchecked")
  void plano_e_playbook_salvos_chegam_ao_motor_no_kickoff() throws Exception {
    MatchFixtures.level(mvc, auth, "NONE");
    ok(call(mvc, put(base + "/playbook"), auth, playbook(0, 0, "DEEP_PASS", "OUTSIDE_RUN")));
    Map<String, Object> board = MatchFixtures.play(mvc, auth, careerId);
    String mid = (String) board.get("matchId");
    String clubId = (String) ((Map<String, Object>) ok(call(mvc, get(base), auth, null))).get("clubId");
    MatchFixtures.finish(mvc, auth, mid, "FULL");
    List<Map<String, Object>> events = MatchFixtures.events(mvc, auth, mid, 0, 1000);
    List<Object> ourAiConcepts = events.stream().filter(e -> clubId.equals(e.get("possessionClubId")) && "AI".equals(e.get("callSource")))
        .map(e -> e.get("concept")).toList();
    assertThat(ourAiConcepts).contains("INSIDE_RUN", "SHORT_PASS").doesNotContain("DEEP_PASS", "OUTSIDE_RUN");
  }

  @Test
  @SuppressWarnings("unchecked")
  void preparacao_traz_adversario_confrontos_prontidao_e_clima() throws Exception {
    Map<String, Object> prep = ok(call(mvc, get(base + "/next-match/preparation"), auth, null));
    assertThat(prep).containsKeys("fixture", "opponent", "weather", "units", "keyMatchups", "keyPlayers", "opponentKeyPlayers", "readiness", "gamePlan")
        .containsEntry("playCallingLevel", "KEY_MOMENTS").doesNotContainKey("matchId");
    assertThat((Map<String, Object>) prep.get("fixture")).containsEntry("week", 1).containsEntry("userGame", true);
    assertThat((List<Map<String, Object>>) prep.get("keyMatchups")).extracting(m -> m.get("key") + "/" + m.get("side")).containsExactly(
        "WR_VS_CB/OUR_OFFENSE", "OL_VS_DL/OUR_OFFENSE", "QB_VS_PASS_RUSH/OUR_OFFENSE", "WR_VS_CB/THEIR_OFFENSE", "OL_VS_DL/THEIR_OFFENSE",
        "QB_VS_PASS_RUSH/THEIR_OFFENSE");
    assertThat((List<Map<String, Object>>) prep.get("keyMatchups")).allSatisfy(m -> {
      assertThat(((Number) m.get("edge")).intValue()).isEqualTo(((Number) m.get("ours")).intValue() - ((Number) m.get("theirs")).intValue());
      assertThat((List<?>) m.get("ourPlayers")).isNotEmpty();
      assertThat((List<?>) m.get("theirPlayers")).isNotEmpty();
    });
    assertThat((List<Map<String, Object>>) prep.get("units")).extracting(u -> u.get("unit")).containsExactly("OFFENSE", "DEFENSE", "SPECIAL_TEAMS");
    assertThat((Map<String, Object>) prep.get("readiness")).containsEntry("ready", true);
    assertThat((List<?>) prep.get("keyPlayers")).hasSize(3);
    // a partida iniciada aparece na preparação
    String mid = (String) MatchFixtures.play(mvc, auth, careerId).get("matchId");
    assertThat(ok(call(mvc, get(base + "/next-match/preparation"), auth, null))).containsEntry("matchId", mid);
  }

  @Test
  void carreira_de_outro_usuario_responde_403_como_no_6a() throws Exception {
    String intruder = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    for (String path : List.of("/game-plan", "/playbook", "/next-match/preparation", "/weeks/1")) {
      assertProblem(call(mvc, get(base + path), intruder, null), 403, "FORBIDDEN");
    }
    assertProblem(call(mvc, put(base + "/game-plan"), intruder, plan(0, 50, "NORMAL")), 403, "FORBIDDEN");
    assertProblem(call(mvc, put(base + "/playbook"), intruder, playbook(0, 0, "DEEP_PASS")), 403, "FORBIDDEN");
    assertProblem(call(mvc, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base + "/weeks/current/play"), intruder, null), 403,
        "FORBIDDEN");
    assertThat(ok(call(mvc, get(base + "/game-plan"), auth, null))).containsEntry("version", 0);
  }
}
