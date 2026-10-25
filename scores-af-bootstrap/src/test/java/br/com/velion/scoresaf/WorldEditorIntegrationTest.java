package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.application.port.out.persistence.AuditLogRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WorldRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.application.usecase.rf05.WorldEditorUseCases;
import br.com.velion.scoresaf.domain.account.AuditLog;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import br.com.velion.scoresaf.web.security.TokenService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;

/**
 * RF5 — editor de mundos de ponta a ponta (HTTP → caso de uso → H2): acesso (dono; mundo padrão só leitura; ADMIN lê, não edita),
 * auditoria das recusas e das alterações, estrutura de conferências e divisões, 8–32 franquias, limites de elenco de
 * {@code LeagueRules}, posição e altura/peso por posição, capacidade do estádio — sempre no contrato de erro do esqueleto, traduzido.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorldEditorIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;
  @Autowired TokenService tokens;
  @Autowired AuditLogRepository auditLog;
  @Autowired WorldRepository worlds;

  UUID defaultWorld;

  @BeforeEach
  void seed() { defaultWorld = seeder.seedIfMissing().getId(); }

  private String bearer(UserRole role) { return TestSessions.bearer(users, sessions, tokens, role); }

  private static String json(Object o) { return Jsons.MAPPER.writeValueAsString(o); }

  private MvcResult call(MockHttpServletRequestBuilder req, String auth, Object body) throws Exception {
    req.header("Authorization", auth).header("Accept-Language", "en-US");
    if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json(body));
    return mvc.perform(req).andReturn();
  }

  private static Map<String, Object> map(MvcResult r) throws Exception {
    return Jsons.MAPPER.readValue(r.getResponse().getContentAsString(), new TypeReference<>() {});
  }

  private static List<Map<String, Object>> list(MvcResult r) throws Exception {
    return Jsons.MAPPER.readValue(r.getResponse().getContentAsString(), new TypeReference<>() {});
  }

  private static void assertProblem(MvcResult r, int status, String code) throws Exception {
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(status);
    Map<String, Object> body = map(r);
    assertThat(body).containsEntry("code", code).containsEntry("locale", "en-US").containsKeys("message", "correlationId", "details");
  }

  private UUID cloneDefault(String auth) throws Exception {
    MvcResult r = call(post("/api/v1/worlds/" + defaultWorld + "/clone"), auth, Map.of("name", "Meu mundo"));
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    Map<String, Object> w = map(r);
    assertThat(w).containsEntry("editable", true).containsEntry("defaultTemplate", false).containsEntry("clubs", 32).containsEntry("players", 32 * 69);
    return UUID.fromString((String) w.get("id"));
  }

  private UUID firstLeague(UUID world, String auth) throws Exception {
    return UUID.fromString((String) list(call(get("/api/v1/worlds/" + world + "/leagues"), auth, null)).get(0).get("id"));
  }

  private List<Map<String, Object>> clubsOf(UUID league, String auth) throws Exception {
    return list(call(get("/api/v1/leagues/" + league + "/clubs"), auth, null));
  }

  private boolean audited(String action, String resourceId) {
    return auditLog.findByActionOrderByTimestampDesc(action, PageRequest.of(0, 200)).stream().map(AuditLog::getResourceId).anyMatch(resourceId::equals);
  }

  @Test
  void mundo_padrao_aparece_para_todos_e_e_so_leitura_inclusive_para_admin() throws Exception {
    String user = bearer(UserRole.PLAYER);
    List<Map<String, Object>> ws = list(call(get("/api/v1/worlds"), user, null));
    assertThat(ws).anySatisfy(w -> assertThat(w).containsEntry("id", defaultWorld.toString()).containsEntry("defaultTemplate", true)
        .containsEntry("editable", false).containsEntry("leagues", 1).containsEntry("clubs", 32).containsEntry("players", 32 * 69));
    UUID league = firstLeague(defaultWorld, user);
    Map<String, Object> l = list(call(get("/api/v1/worlds/" + defaultWorld + "/leagues"), user, null)).get(0);
    assertThat(l).containsEntry("ruleset", "PRO").containsEntry("teamCount", 32).containsEntry("conferenceCount", 2)
        .containsEntry("divisionsPerConference", 4).containsEntry("structureValid", true);

    assertProblem(call(put("/api/v1/leagues/" + league), user, Map.of("name", "Outra")), 422, "WORLD_READ_ONLY");
    assertProblem(call(put("/api/v1/leagues/" + league), bearer(UserRole.ADMIN), Map.of("name", "Outra")), 422, "WORLD_READ_ONLY");
    assertThat(audited(AuditActions.ACCESS_DENIED, defaultWorld.toString())).as("recusa auditada").isTrue();
    UUID club = UUID.fromString((String) clubsOf(league, user).get(0).get("id"));
    assertProblem(call(post("/api/v1/clubs/" + club + "/players"), user, Map.of("name", "X", "position", "QB")), 422, "WORLD_READ_ONLY");
  }

  @Test
  void so_o_dono_le_e_edita_o_mundo_e_admin_le_com_auditoria_mas_nao_edita() throws Exception {
    String owner = bearer(UserRole.PLAYER);
    UUID mine = cloneDefault(owner);
    assertThat(audited(AuditActions.WORLD_CLONED, mine.toString())).isTrue();

    String other = bearer(UserRole.PLAYER);
    assertProblem(call(get("/api/v1/worlds/" + mine), other, null), 403, "FORBIDDEN");
    assertProblem(call(get("/api/v1/worlds/" + mine + "/leagues"), other, null), 403, "FORBIDDEN");
    assertProblem(call(put("/api/v1/worlds/" + mine), other, Map.of("name", "Roubado")), 403, "FORBIDDEN");
    assertThat(audited(AuditActions.ACCESS_DENIED, mine.toString())).as("acesso negado auditado").isTrue();
    assertThat(list(call(get("/api/v1/worlds"), other, null))).noneSatisfy(w -> assertThat(w.get("id")).isEqualTo(mine.toString()));

    String admin = bearer(UserRole.ADMIN);
    MvcResult adminRead = call(get("/api/v1/worlds/" + mine), admin, null);
    assertThat(adminRead.getResponse().getStatus()).isEqualTo(200);
    assertThat(map(adminRead)).containsEntry("editable", false);
    assertThat(audited(AuditActions.ADMIN_WORLD_READ, mine.toString())).isTrue();
    assertProblem(call(put("/api/v1/worlds/" + mine), admin, Map.of("name", "Do admin")), 403, "FORBIDDEN");

    MvcResult renamed = call(put("/api/v1/worlds/" + mine), owner, Map.of("name", "Liga da casa"));
    assertThat(renamed.getResponse().getStatus()).isEqualTo(200);
    assertThat(map(renamed)).containsEntry("name", "Liga da casa");
    assertThat(audited(AuditActions.WORLD_UPDATED, mine.toString())).isTrue();

    assertThat(call(delete("/api/v1/worlds/" + mine), owner, null).getResponse().getStatus()).isEqualTo(204);
    assertThat(worlds.findById(mine)).isEmpty();
    assertThat(audited(AuditActions.WORLD_DELETED, mine.toString())).isTrue();
    assertThat(call(get("/api/v1/worlds/" + defaultWorld), owner, null).getResponse().getStatus()).as("o padrão continua lá").isEqualTo(200);
  }

  @Test
  void teto_de_mundos_por_usuario() throws Exception {
    TestSessions.Session session = TestSessions.open(users, sessions, tokens, UserRole.PLAYER);
    String auth = session.bearer();
    for (int i = 0; i < WorldEditorUseCases.MAX_WORLDS_PER_USER; i++) {
      World w = new World(); w.setName("vazio " + i); w.setKind(WorldKind.TEMPLATE); w.setOwnerUser(session.user());
      worlds.save(w);
    }
    assertProblem(call(post("/api/v1/worlds/" + defaultWorld + "/clone"), auth, null), 422, "WORLD_LIMIT");
  }

  @Test
  void estrutura_da_liga_aceita_2x2x8_e_recusa_estruturas_inconsistentes() throws Exception {
    String auth = bearer(UserRole.PLAYER);
    UUID world = cloneDefault(auth);
    UUID league = firstLeague(world, auth);
    String route = "/api/v1/worlds/" + world + "/leagues/" + league + "/structure";
    Map<String, Object> current = map(call(get(route), auth, null));
    List<Map<String, Object>> confs = cast(current.get("conferences"));
    assertThat(confs).hasSize(2).allSatisfy(c -> assertThat((List<?>) c.get("divisions")).hasSize(4)
        .allSatisfy(d -> assertThat((List<?>) ((Map<?, ?>) d).get("clubIds")).hasSize(4)));

    List<String> ids = clubsOf(league, auth).stream().map(c -> (String) c.get("id")).toList();
    assertThat(ids).hasSize(32);

    // válida: 2 conferências × 2 divisões × 8
    MvcResult ok = call(put(route), auth, structure(List.of(List.of(ids.subList(0, 8), ids.subList(8, 16)), List.of(ids.subList(16, 24), ids.subList(24, 32)))));
    assertThat(ok.getResponse().getStatus()).as(ok.getResponse().getContentAsString()).isEqualTo(200);
    Map<String, Object> l = list(call(get("/api/v1/worlds/" + world + "/leagues"), auth, null)).get(0);
    assertThat(l).containsEntry("conferenceCount", 2).containsEntry("divisionsPerConference", 2).containsEntry("structureValid", true);
    assertThat(audited(AuditActions.LEAGUE_STRUCTURE_UPDATED, league.toString())).isTrue();

    // 3 conferências
    assertProblem(call(put(route), auth, structure(List.of(List.of(ids.subList(0, 10)), List.of(ids.subList(10, 20)), List.of(ids.subList(20, 32))))),
        422, "STRUCTURE_CONFERENCES");
    // divisões diferentes por conferência (2 e 1)
    assertProblem(call(put(route), auth, structure(List.of(List.of(ids.subList(0, 8), ids.subList(8, 16)), List.of(ids.subList(16, 32))))),
        422, "STRUCTURE_DIVISIONS");
    // divisões desequilibradas (10 e 6)
    assertProblem(call(put(route), auth, structure(List.of(List.of(ids.subList(0, 10), ids.subList(10, 16)), List.of(ids.subList(16, 24), ids.subList(24, 32))))),
        422, "STRUCTURE_DIVISIONS");
    // franquia faltando
    assertProblem(call(put(route), auth, structure(List.of(List.of(ids.subList(0, 8), ids.subList(8, 16)), List.of(ids.subList(16, 24), ids.subList(24, 31))))),
        422, "STRUCTURE_CLUBS");
    // franquia repetida
    List<String> dup = new ArrayList<>(ids.subList(24, 31)); dup.add(ids.get(0));
    assertProblem(call(put(route), auth, structure(List.of(List.of(ids.subList(0, 8), ids.subList(8, 16)), List.of(ids.subList(16, 24), dup)))),
        422, "STRUCTURE_CLUBS");
    // liga cheia não aceita a 33ª franquia
    assertProblem(call(post("/api/v1/leagues/" + league + "/clubs"), auth, Map.of("name", "Extra", "shortName", "EXT")), 422, "LEAGUE_FULL");
  }

  @Test
  void liga_nova_college_exige_de_8_a_32_franquias_para_ter_estrutura() throws Exception {
    String auth = bearer(UserRole.PLAYER);
    UUID world = cloneDefault(auth);
    MvcResult created = call(post("/api/v1/worlds/" + world + "/leagues"), auth, Map.of("name", "Liga Universitária", "ruleset", "COLLEGE"));
    assertThat(created.getResponse().getStatus()).as(created.getResponse().getContentAsString()).isEqualTo(201);
    Map<String, Object> l = map(created);
    Map<String, Object> rules = cast(l.get("rules"));
    assertThat(rules).containsEntry("clockStopsOnFirstDown", true).containsEntry("oneFootInbounds", true).containsEntry("overtimeFormat", "ALTERNATING_POSSESSIONS");
    UUID league = UUID.fromString((String) l.get("id"));
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      MvcResult c = call(post("/api/v1/leagues/" + league + "/clubs"), auth, Map.of("name", "Universidade " + i, "shortName", "UN" + i, "primaryColor", "#123abc"));
      assertThat(c.getResponse().getStatus()).as(c.getResponse().getContentAsString()).isEqualTo(201);
      Map<String, Object> club = map(c);
      assertThat(club).containsEntry("primaryColor", "#123ABC").containsEntry("rosterMax", 105).containsEntry("practiceSquadMax", 0);
      ids.add((String) club.get("id"));
    }
    String route = "/api/v1/worlds/" + world + "/leagues/" + league + "/structure";
    assertProblem(call(put(route), auth, structure(List.of(List.of(ids)))), 422, "LEAGUE_TEAM_COUNT");
    MvcResult eighth = call(post("/api/v1/leagues/" + league + "/clubs"), auth, Map.of("name", "Universidade 7", "shortName", "UN7"));
    ids.add((String) map(eighth).get("id"));
    MvcResult ok = call(put(route), auth, structure(List.of(List.of(ids.subList(0, 4)), List.of(ids.subList(4, 8)))));
    assertThat(ok.getResponse().getStatus()).as(ok.getResponse().getContentAsString()).isEqualTo(200);
    // a 9ª entra sozinha na divisão menor (a estrutura continua equilibrada)
    MvcResult ninth = call(post("/api/v1/leagues/" + league + "/clubs"), auth, Map.of("name", "Universidade 8", "shortName", "UN8"));
    assertThat(map(ninth).get("divisionId")).isNotNull();
    assertThat(list(call(get("/api/v1/worlds/" + world + "/leagues"), auth, null))).anySatisfy(x ->
        assertThat(x).containsEntry("id", league.toString()).containsEntry("teamCount", 9).containsEntry("structureValid", true));
    // validações de entrada
    assertProblem(call(post("/api/v1/worlds/" + world + "/leagues"), auth, Map.of("name", "Liga Arena", "ruleset", "ARENA")), 400, "INVALID_RULESET");
    assertProblem(call(put("/api/v1/leagues/" + league), auth, Map.of("rules", Map.of("quarterMinutes", 0))), 400, "INVALID_RULES");
    assertProblem(call(put("/api/v1/clubs/" + ids.get(0)), auth, Map.of("primaryColor", "azul")), 400, "INVALID_COLOR");
    assertProblem(call(delete("/api/v1/leagues/" + league), auth, null), 422, "LEAGUE_HAS_CLUBS");
  }

  @Test
  void jogador_respeita_posicao_altura_peso_subatributos_e_limites_de_elenco() throws Exception {
    String auth = bearer(UserRole.PLAYER);
    UUID world = cloneDefault(auth);
    UUID league = firstLeague(world, auth);
    UUID club = UUID.fromString((String) clubsOf(league, auth).get(0).get("id"));
    String players = "/api/v1/clubs/" + club + "/players";

    assertProblem(call(post(players), auth, Map.of("name", "Armador", "position", "PG", "rosterStatus", "IR")), 400, "INVALID_POSITION");
    MvcResult shortQb = call(post(players), auth, Map.of("name", "Baixinho", "position", "QB", "heightCm", 150, "rosterStatus", "IR"));
    assertProblem(shortQb, 400, "INVALID_HEIGHT");
    assertThat((String) map(shortQb).get("message")).contains("QB").contains("180").contains("203");
    assertProblem(call(post(players), auth, Map.of("name", "Leve", "position", "LT", "weightKg", 90, "rosterStatus", "IR")), 400, "INVALID_WEIGHT");
    assertProblem(call(post(players), auth, Map.of("name", "Forte", "position", "DT", "rosterStatus", "IR", "attributes", Map.of("defense", Map.of("tackle", 101)))),
        400, "INVALID_ATTRIBUTE");
    assertProblem(call(post(players), auth, Map.of("name", "Sem nome de atributo", "position", "DT", "rosterStatus", "IR", "attributes", Map.of("dunk", 80))),
        400, "INVALID_ATTRIBUTE");

    // elenco de 53 e practice squad de 16 já cheios no mundo semente
    assertProblem(call(post(players), auth, Map.of("name", "Quinquagésimo quarto", "position", "WR")), 422, "ROSTER_FULL");
    assertProblem(call(post(players), auth, Map.of("name", "Décimo sétimo", "position", "WR", "rosterStatus", "PRACTICE_SQUAD")), 422, "PRACTICE_SQUAD_FULL");

    // na lista de lesionados não conta: entra, com número livre e overall por posição
    MvcResult ir = call(post(players), auth, Map.of("name", "Lesionado Teste", "position", "MLB", "rosterStatus", "IR", "heightCm", 188, "weightKg", 110,
        "attributes", Map.of("defense", Map.of("tackle", 90, "playRecognition", 88))));
    assertThat(ir.getResponse().getStatus()).as(ir.getResponse().getContentAsString()).isEqualTo(201);
    Map<String, Object> p = map(ir);
    UUID pid = UUID.fromString((String) p.get("id"));
    assertThat(p).containsEntry("unit", "DEFENSE").containsEntry("rosterStatus", "IR");
    Map<String, Object> rating = cast(p.get("rating"));
    Map<String, Object> byPos = cast(p.get("overallByPosition"));
    assertThat(rating).containsEntry("version", "OVR v1").containsEntry("family", "DEFENSE").containsEntry("overall", byPos.get("MLB"));
    assertThat(List.copyOf(((Map<String, ?>) rating.get("macros")).keySet())).containsExactly("TAC", "IMP", "COB", "PRS", "VEL", "LEI");
    assertThat(audited(AuditActions.CATALOG_CREATED, pid.toString())).isTrue();

    // voltar para o elenco ativo estoura o limite; virar agente livre solta o clube
    assertProblem(call(put("/api/v1/players/" + pid), auth, Map.of("rosterStatus", "ACTIVE")), 422, "ROSTER_FULL");
    int taken = ((Number) list(call(get(players), auth, null)).get(0).get("jerseyNumber")).intValue();
    assertProblem(call(put("/api/v1/players/" + pid), auth, Map.of("jerseyNumber", taken)), 422, "JERSEY_TAKEN");
    assertProblem(call(put("/api/v1/players/" + pid), auth, Map.of("jerseyNumber", 120)), 400, "INVALID_JERSEY_NUMBER");
    MvcResult freed = call(put("/api/v1/players/" + pid), auth, Map.of("rosterStatus", "FREE_AGENT"));
    assertThat(freed.getResponse().getStatus()).isEqualTo(200);
    assertThat(map(freed)).containsEntry("rosterStatus", "FREE_AGENT").doesNotContainKey("clubId");
    assertProblem(call(put("/api/v1/players/" + pid), auth, Map.of("rosterStatus", "IR")), 422, "PLAYER_WITHOUT_CLUB");
    assertThat(audited(AuditActions.CATALOG_UPDATED, pid.toString())).isTrue();

    // regra nova abaixo do elenco atual é recusada
    assertProblem(call(put("/api/v1/leagues/" + league), auth, Map.of("rules", Map.of("rosterMax", 50))), 422, "ROSTER_ABOVE_LIMIT");
  }

  @Test
  void estadio_fica_entre_60_e_80_mil_lugares() throws Exception {
    String auth = bearer(UserRole.PLAYER);
    UUID world = cloneDefault(auth);
    UUID club = UUID.fromString((String) clubsOf(firstLeague(world, auth), auth).get(0).get("id"));
    String route = "/api/v1/clubs/" + club + "/stadium";
    Map<String, Object> s = map(call(get(route), auth, null));
    int capacity = ((Number) s.get("capacity")).intValue();
    assertThat(capacity).isBetween(60_000, 80_000);
    assertThat((List<?>) s.get("sectors")).hasSize(5);

    assertProblem(call(put(route), auth, Map.of("sectors", List.of(Map.of("type", "LOWER_BOWL", "seats", 1_000)))), 400, "INVALID_STADIUM_CAPACITY");
    assertThat(((Number) map(call(get(route), auth, null)).get("capacity")).intValue()).as("nada gravado").isEqualTo(capacity);
    assertProblem(call(put(route), auth, Map.of("sectors", List.of(Map.of("type", "COURTSIDE", "seats", 10)))), 400, "INVALID_SECTOR");
    assertProblem(call(put(route), auth, Map.of("roof", "TENT")), 400, "INVALID_VALUE");

    MvcResult ok = call(put(route), auth, Map.of("roof", "DOME", "surface", "TURF", "sectors", List.of(Map.of("type", "END_ZONE", "seats", 7_000))));
    assertThat(ok.getResponse().getStatus()).as(ok.getResponse().getContentAsString()).isEqualTo(200);
    assertThat(map(ok)).containsEntry("roof", "DOME").containsEntry("surface", "TURF");
  }

  @Test
  void estrutura_recusa_ids_repetidos_de_divisao_e_de_conferencia_sem_gravar_nada() throws Exception {
    String auth = bearer(UserRole.PLAYER);
    UUID world = cloneDefault(auth);
    UUID league = firstLeague(world, auth);
    String route = "/api/v1/worlds/" + world + "/leagues/" + league + "/structure";
    Map<String, Object> before = map(call(get(route), auth, null));
    List<Map<String, Object>> confs = cast(before.get("conferences"));
    String conf0 = (String) confs.get(0).get("id"), conf1 = (String) confs.get(1).get("id");
    String div0 = (String) ((Map<?, ?>) ((List<?>) confs.get(0).get("divisions")).get(0)).get("id");
    String div1 = (String) ((Map<?, ?>) ((List<?>) confs.get(0).get("divisions")).get(1)).get("id");
    List<String> ids = clubsOf(league, auth).stream().map(c -> (String) c.get("id")).toList();

    // o mesmo id de divisão em duas entradas (uma em cada conferência), listas disjuntas e equilibradas
    Map<String, Object> dupDivision = Map.of("conferences", List.of(
        conf(conf0, "A", div(div0, "A1", ids.subList(0, 8)), div(div1, "A2", ids.subList(8, 16))),
        conf(conf1, "B", div(div0, "B1", ids.subList(16, 24)), div(null, "B2", ids.subList(24, 32)))));
    assertProblem(call(put(route), auth, dupDivision), 422, "STRUCTURE_DUPLICATE_ID");
    // o mesmo id de conferência nas duas entradas
    Map<String, Object> dupConference = Map.of("conferences", List.of(
        conf(conf0, "A", div(null, "A1", ids.subList(0, 8)), div(null, "A2", ids.subList(8, 16))),
        conf(conf0, "B", div(null, "B1", ids.subList(16, 24)), div(null, "B2", ids.subList(24, 32)))));
    assertProblem(call(put(route), auth, dupConference), 422, "STRUCTURE_DUPLICATE_ID");
    assertThat(map(call(get(route), auth, null))).as("nada gravado").isEqualTo(before);
  }

  @Test
  void trocar_o_ruleset_confere_os_elencos_contra_as_regras_resultantes() throws Exception {
    String auth = bearer(UserRole.PLAYER);
    UUID world = cloneDefault(auth);
    UUID pro = firstLeague(world, auth);
    // PRO → COLLEGE: o college não tem practice squad, e cada franquia tem 16
    assertProblem(call(put("/api/v1/leagues/" + pro), auth, Map.of("ruleset", "COLLEGE")), 422, "ROSTER_ABOVE_LIMIT");
    assertThat(list(call(get("/api/v1/worlds/" + world + "/leagues"), auth, null)).get(0)).containsEntry("ruleset", "PRO");

    // COLLEGE → PRO: elenco de 54 cabe no college (105), não no PRO (53)
    UUID college = UUID.fromString((String) map(call(post("/api/v1/worlds/" + world + "/leagues"), auth,
        Map.of("name", "Liga Universitária", "ruleset", "COLLEGE"))).get("id"));
    UUID club = UUID.fromString((String) map(call(post("/api/v1/leagues/" + college + "/clubs"), auth, Map.of("name", "Universidade A", "shortName", "UNA"))).get("id"));
    for (int i = 0; i < 54; i++) {
      MvcResult r = call(post("/api/v1/clubs/" + club + "/players"), auth, Map.of("name", "Calouro " + i, "position", "WR", "jerseyNumber", i));
      assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    }
    assertProblem(call(put("/api/v1/leagues/" + college), auth, Map.of("ruleset", "PRO")), 422, "ROSTER_ABOVE_LIMIT");
  }

  @Test
  void divisao_vazia_deixa_a_estrutura_invalida() throws Exception {
    String auth = bearer(UserRole.PLAYER);
    UUID world = cloneDefault(auth);
    UUID league = firstLeague(world, auth);
    Map<String, Object> structure = map(call(get("/api/v1/worlds/" + world + "/leagues/" + league + "/structure"), auth, null));
    List<Map<String, Object>> confs = cast(structure.get("conferences"));
    List<?> firstDivisionClubs = (List<?>) ((Map<?, ?>) ((List<?>) confs.get(0).get("divisions")).get(0)).get("clubIds");
    for (Object id : firstDivisionClubs) assertThat(call(delete("/api/v1/clubs/" + id), auth, null).getResponse().getStatus()).isEqualTo(204);
    assertThat(list(call(get("/api/v1/worlds/" + world + "/leagues"), auth, null)).get(0))
        .containsEntry("teamCount", 28).containsEntry("structureValid", false);
  }

  private static Map<String, Object> conf(String id, String name, Map<String, Object>... divisions) {
    Map<String, Object> m = new HashMap<>();
    if (id != null) m.put("id", id);
    m.put("name", name);
    m.put("divisions", List.of(divisions));
    return m;
  }

  private static Map<String, Object> div(String id, String name, List<String> clubIds) {
    Map<String, Object> m = new HashMap<>();
    if (id != null) m.put("id", id);
    m.put("name", name);
    m.put("clubIds", clubIds);
    return m;
  }

  // ---- apoio
  @SuppressWarnings("unchecked")
  private static <T> T cast(Object o) { return (T) o; }

  /** Conferências → divisões → ids de franquia. */
  private static Map<String, Object> structure(List<List<List<String>>> confs) {
    List<Map<String, Object>> cs = new ArrayList<>();
    for (int c = 0; c < confs.size(); c++) {
      List<Map<String, Object>> ds = new ArrayList<>();
      for (int d = 0; d < confs.get(c).size(); d++) {
        Map<String, Object> div = new LinkedHashMap<>();
        div.put("name", "Divisão " + c + "-" + d);
        div.put("clubIds", confs.get(c).get(d));
        ds.add(div);
      }
      Map<String, Object> conf = new HashMap<>();
      conf.put("name", "Conferência " + c);
      conf.put("divisions", ds);
      cs.add(conf);
    }
    return Map.of("conferences", cs);
  }
}
