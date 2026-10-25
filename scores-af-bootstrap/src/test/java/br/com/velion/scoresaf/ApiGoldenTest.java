package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.time.LocalDate;
import java.util.Comparator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Amostras douradas das respostas que o front lê, gravadas pela própria API (mesmo mapper, mesma serialização): o meta, o
 * corpo de erro e, desde o bloco 3, as respostas de conta (login, {@code GET /api/v1/me}, {@code GET /api/v1/me/settings} e o
 * corpo do 429 do limite). Bloco 4: a franquia e o jogador do editor de mundos ({@code GET /api/v1/clubs/{id}} e
 * {@code GET /api/v1/players/{id}}) no mundo padrão, que é determinístico pela semente (os ids do catálogo são estáveis). Os testes do front ({@code frontend/tests/api/*.test.mts}) leem estes arquivos, então os adaptadores
 * de {@code src/lib/api} são conferidos contra o formato Java, não contra um JSON escrito à mão (D-5). Mudou o formato? Regenere
 * com {@code mvn -pl scores-af-bootstrap -am test -Dtest=ApiGoldenTest -Dsurefire.failIfNoSpecifiedTests=false -Dgolden.update=true}
 * e rode {@code npm run test:unit}.
 *
 * <p>Valores que mudam a cada execução (ids, instantes, token, e-mail gerado) são trocados por valores fixos do mesmo formato
 * antes de gravar e de comparar ({@link #STABLE}); as chaves, os tipos e os demais valores são os reais. A senha da conta de
 * teste é gerada na execução e nunca aparece na resposta.
 *
 * <p>Bloco 6a: a carreira (lista, central, calendário), o elenco, o jogador, o depth chart e o 409 do depth chart. A carreira é
 * criada com semente fixa na franquia PAV do mundo padrão, então o conteúdo é determinístico; os ids (todos novos, do mundo copiado)
 * viram UUIDs sequenciais na ordem em que aparecem no arquivo ({@link #stableCareer}), o que preserva as referências internas
 * (ex.: {@code homeClubId} do jogo aponta para um {@code clubs[].id}).
 *
 * <p>Bloco 6b: plano de jogo, playbook, 409 do plano, preparação, painel da semana, estado ao vivo, play-by-play e relatório
 * pós-jogo, na mesma carreira dourada (a partida é determinística pela semente; o prazo da decisão vira instante fixo).
 *
 * <p>Bloco 6c: classificação, moral, analista, notícias, playoffs, chave, resumo, avanço e troféus, e os problemas 409/422 do avanço,
 * numa temporada dourada curta: o mundo padrão clonado com a liga em 2 jogos por franquia (o resto das regras igual: 7 sementes por
 * conferência, folga da 1ª, final neutra), a mesma franquia PAV e a semente 2026.
 */
@SpringBootTest(properties = {"scores.app.version=0.0.0-golden", "scores.security.rate-limit.limits.password-forgot=1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiGoldenTest {
  static final Path DIR = Path.of("..", "docs", "validation", "golden");
  /** Campo volátil → valor fixo do mesmo formato (UUID, Instant ISO-8601, JWT opaco, e-mail). */
  static final Map<String, String> STABLE = Map.of(
      "id", "00000000-0000-4000-8000-000000000001",
      "accessToken", "golden.access.token",
      "accessExpiresAt", "2026-01-01T00:15:00Z",
      "refreshExpiresAt", "2026-01-08T00:00:00Z",
      "createdAt", "2026-01-01T00:00:00Z",
      "lastLoginAt", "2026-01-01T00:00:00Z",
      "dataProcessingConsentAt", "2026-01-01T00:00:00Z",
      "email", "golden@scoresaf.test");

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired CareerSaveRepository saves;
  @Autowired WeekRepository weeks;
  @Autowired PlayerRepository players;
  @Autowired PlatformTransactionManager txManager;

  private void assertGolden(String file, MockHttpServletRequestBuilder request) throws Exception {
    assertGolden(file, mvc.perform(request).andReturn());
  }

  private void assertGolden(String file, MvcResult result) throws Exception {
    Path golden = DIR.resolve(file);
    JsonNode now = stable(json.readTree(result.getResponse().getContentAsString()));
    if (Boolean.getBoolean("golden.update") || "1".equals(System.getenv("GOLDEN_UPDATE"))) {
      Files.writeString(golden, json.writerWithDefaultPrettyPrinter().writeValueAsString(now) + "\n");
    }
    assertThat(golden).as("amostra %s (rodar a partir do módulo; regenerar com -Dgolden.update=true)", file).exists();
    if (!json.readTree(Files.readString(golden)).equals(now)) {
      fail("docs/validation/golden/" + file + " diverge da API: regenere com -Dgolden.update=true e rode o teste do front");
    }
  }

  /** Troca os valores voláteis (em qualquer nível) por {@link #STABLE}; campo ausente continua ausente. */
  static JsonNode stable(JsonNode node) {
    if (node instanceof ObjectNode o) {
      for (String name : o.propertyNames()) {
        JsonNode v = o.get(name);
        if (STABLE.containsKey(name) && v != null && !v.isNull()) o.put(name, STABLE.get(name));
        else stable(v);
      }
    } else if (node != null && node.isArray()) {
      node.forEach(ApiGoldenTest::stable);
    }
    return node;
  }

  /** Cadastra uma conta nova (senha gerada na execução) e devolve a resposta do login. */
  private MvcResult registerAndLogin() throws Exception {
    String email = "golden-" + UUID.randomUUID() + "@scoresaf.test";
    String password = "T" + UUID.randomUUID();
    MvcResult reg = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
        Map.of("email", email, "password", password, "displayName", "Jogadora Dourada", "locale", "pt-BR", "marketingConsent", false)))).andReturn();
    assertThat(reg.getResponse().getStatus()).as(reg.getResponse().getContentAsString()).isEqualTo(201);
    MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
    assertThat(login.getResponse().getStatus()).as(login.getResponse().getContentAsString()).isEqualTo(200);
    return login;
  }

  private String bearer() throws Exception {
    return "Bearer " + json.readTree(registerAndLogin().getResponse().getContentAsString()).get("accessToken").asString();
  }

  @Test
  void meta_bate_com_a_amostra_dourada() throws Exception {
    assertGolden("meta.json", get("/api/v1/meta"));
  }

  @Test
  void corpo_de_erro_bate_com_a_amostra_dourada() throws Exception {
    // correlationId e idioma fixos para a amostra ser estável
    assertGolden("problem-401.json", get("/api/v1/me").header("X-Correlation-Id", "golden-401").header("Accept-Language", "pt-BR"));
  }

  @Test
  void login_bate_com_a_amostra_dourada() throws Exception {
    // o mesmo TokenResponse do refresh: access no corpo, refresh só no cookie HttpOnly
    assertGolden("auth-login.json", registerAndLogin());
  }

  @Test
  void minha_conta_bate_com_a_amostra_dourada() throws Exception {
    assertGolden("me.json", get("/api/v1/me").header("Authorization", bearer()).header("Accept-Language", "pt-BR"));
  }

  @Test
  void configuracoes_batem_com_a_amostra_dourada() throws Exception {
    assertGolden("me-settings.json", get("/api/v1/me/settings").header("Authorization", bearer()).header("Accept-Language", "pt-BR"));
  }

  @Test
  void limite_excedido_bate_com_a_amostra_dourada() throws Exception {
    // limite do "esqueci a senha" em 1 neste contexto: o 2º pedido do mesmo IP recebe o 429 do filtro
    MockHttpServletRequestBuilder forgot = post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"ninguem@scoresaf.test\"}").header("X-Correlation-Id", "golden-429").header("Accept-Language", "pt-BR")
        .with(r -> { r.setRemoteAddr("198.51.100.42"); return r; });
    assertThat(mvc.perform(forgot).andReturn().getResponse().getStatus()).isEqualTo(202);
    MvcResult limited = mvc.perform(forgot).andReturn();
    assertThat(limited.getResponse().getStatus()).isEqualTo(429);
    assertGolden("problem-429.json", limited);
  }

  private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  private static final Set<String> INSTANTS = Set.of("createdAt", "lastPlayedAt", "updatedAt", "at", "decisionDeadlineAt", "generatedAt");

  /** Amostra da carreira: ids sequenciais na ordem de aparição e instantes fixos (ver o javadoc da classe). */
  private void assertCareerGolden(String file, MvcResult result) throws Exception {
    Path golden = DIR.resolve(file);
    JsonNode now = stableCareer(json.readTree(result.getResponse().getContentAsString()), new LinkedHashMap<>());
    if (Boolean.getBoolean("golden.update") || "1".equals(System.getenv("GOLDEN_UPDATE"))) {
      Files.writeString(golden, json.writerWithDefaultPrettyPrinter().writeValueAsString(now) + "\n");
    }
    assertThat(golden).as("amostra %s (rodar a partir do módulo; regenerar com -Dgolden.update=true)", file).exists();
    if (!json.readTree(Files.readString(golden)).equals(now)) {
      fail("docs/validation/golden/" + file + " diverge da API: regenere com -Dgolden.update=true e rode o teste do front");
    }
  }

  static JsonNode stableCareer(JsonNode node, Map<String, String> ids) {
    if (node instanceof ObjectNode o) {
      for (String name : o.propertyNames()) {
        JsonNode v = o.get(name);
        if (v != null && v.isString() && INSTANTS.contains(name)) o.put(name, "2026-01-01T00:00:00Z");
        else if (v != null && v.isString() && UUID_TEXT.matcher(v.asString()).find()) o.put(name, replaceIds(ids, v.asString()));
        else stableCareer(v, ids);
      }
    } else if (node instanceof tools.jackson.databind.node.ArrayNode a) {
      for (int i = 0; i < a.size(); i++) {
        JsonNode v = a.get(i);
        if (v.isString() && UUID_TEXT.matcher(v.asString()).find()) a.set(i, tools.jackson.databind.node.StringNode.valueOf(replaceIds(ids, v.asString())));
        else stableCareer(v, ids);
      }
    }
    return node;
  }

  /** Troca cada UUID do texto (valor inteiro ou dentro dele, como o {@code instance} do erro) pelo sequencial. */
  private static String replaceIds(Map<String, String> ids, String text) {
    return UUID_TEXT.matcher(text).replaceAll(m -> seq(ids, m.group()));
  }

  private static String seq(Map<String, String> ids, String uuid) {
    return ids.computeIfAbsent(uuid, k -> String.format("00000000-0000-4000-8000-%012d", ids.size() + 1));
  }

  /** Carreira dourada: semente 2026, franquia PAV do mundo padrão, head coach com nome fixo. Devolve o id. */
  private String goldenCareer(String auth) throws Exception {
    Map<String, Object> body = new HashMap<>(Map.of("name", "Dinastia Dourada", "clubId", defaultClub(auth).get("id").asString(), "coachName", "Treinadora Dourada",
        "coachNationality", "BRA", "coachStyle", "OFFENSIVE", "difficulty", "NORMAL", "seed", 2026L));
    MvcResult r = mvc.perform(post("/api/v1/careers").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body))).andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    return json.readTree(r.getResponse().getContentAsString()).get("id").asString();
  }

  @Test
  void carreiras_central_e_calendario_batem_com_as_amostras_douradas() throws Exception {
    String auth = bearer();
    String career = goldenCareer(auth);
    assertCareerGolden("careers-list.json", mvc.perform(get("/api/v1/careers").header("Authorization", auth)).andReturn());
    assertCareerGolden("career-dashboard.json", mvc.perform(get("/api/v1/careers/" + career + "/dashboard").header("Authorization", auth)).andReturn());
    assertCareerGolden("career-calendar.json", mvc.perform(get("/api/v1/careers/" + career + "/calendar").header("Authorization", auth)).andReturn());
  }

  @Test
  void elenco_jogador_e_depth_chart_batem_com_as_amostras_douradas() throws Exception {
    String auth = bearer();
    String career = goldenCareer(auth);
    MvcResult roster = mvc.perform(get("/api/v1/careers/" + career + "/roster").header("Authorization", auth)).andReturn();
    assertCareerGolden("roster.json", roster);
    String qb = null;
    for (JsonNode c : json.readTree(roster.getResponse().getContentAsString()).get("units").get("OFFENSE")) {
      if ("QB".equals(c.get("position").asString())) { qb = c.get("id").asString(); break; }
    }
    assertThat(qb).isNotNull();
    assertCareerGolden("roster-player.json", mvc.perform(get("/api/v1/careers/" + career + "/players/" + qb).header("Authorization", auth)).andReturn());
    MvcResult chart = mvc.perform(get("/api/v1/careers/" + career + "/depth-chart").header("Authorization", auth)).andReturn();
    assertCareerGolden("depth-chart.json", chart);
    // 409: versão desatualizada no PUT
    MvcResult conflict = mvc.perform(put("/api/v1/careers/" + career + "/depth-chart").header("Authorization", auth).header("X-Correlation-Id", "golden-409")
        .header("Accept-Language", "pt-BR").contentType(MediaType.APPLICATION_JSON).content("{\"version\":7,\"slots\":[]}")).andReturn();
    assertThat(conflict.getResponse().getStatus()).as(conflict.getResponse().getContentAsString()).isEqualTo(409);
    assertCareerGolden("problem-409-depth-chart.json", conflict);
  }

  /**
   * Q-24: o 422 {@code CAP_EXCEEDED} da dispensa ({@code details}: teto, comprometido depois da dispensa e excedente, em centavos;
   * mensagem em pt-BR), lido pelo front. Carreira dourada com a data do jogo em março (antes de 1º de junho: todo o dead money na
   * temporada) e o teto da liga ajustado para a dispensa do jogador com o maior dead money além do cap hit passar dele.
   */
  @Test
  void dispensa_que_estoura_o_teto_bate_com_a_amostra_dourada() throws Exception {
    String auth = bearer();
    UUID career = UUID.fromString(goldenCareer(auth));
    long used = json.readTree(mvc.perform(get("/api/v1/careers/" + career + "/roster").header("Authorization", auth)).andReturn().getResponse()
        .getContentAsString()).get("cap").get("capUsed").asLong();
    UUID target = new TransactionTemplate(txManager).execute(st -> {
      CareerSave save = saves.findById(career).orElseThrow();
      weeks.findBySeasonIdAndNumber(save.getSeason().getId(), 1).orElseThrow().setStartsOn(LocalDate.of(2026, 3, 12));
      Comparator<Player> byExtra = Comparator.comparingLong(p -> extraDeadMoney(p.getContract()));
      Player p = players.findByClubIdOrderByOverallDesc(save.getClub().getId()).stream().filter(x -> x.getRosterStatus() == RosterStatus.ACTIVE)
          .max(byExtra.thenComparing(Player::getName).thenComparingInt(Player::getJerseyNumber)).orElseThrow();
      long extra = extraDeadMoney(p.getContract());
      save.getLeague().setSalaryCap(used + extra - extra / 2);
      return p.getId();
    });
    MvcResult refused = mvc.perform(post("/api/v1/careers/" + career + "/players/" + target + "/release").header("Authorization", auth)
        .header("X-Correlation-Id", "golden-422-cap").header("Accept-Language", "pt-BR")).andReturn();
    assertThat(refused.getResponse().getStatus()).as(refused.getResponse().getContentAsString()).isEqualTo(422);
    assertCareerGolden("problem-422-cap-exceeded.json", refused);
  }

  /** Dead money do corte antes de 1º de junho de 2026 menos o cap hit liberado. */
  private static long extraDeadMoney(PlayerContract c) {
    int y = c.yearIndexOf(2026);
    return c.deadMoneyIfCut(y, 5) - c.capHit(y, 5);
  }

  /**
   * Bloco 6b: plano de jogo, playbook, o 409 do plano, a preparação, o painel da semana depois de jogar, o estado ao vivo parado na
   * primeira decisão do manager (o mesmo {@code LiveMatchDto} do {@code live-match.json} em {@code live}), os 12 primeiros lances e o
   * relatório pós-jogo (decisões respondidas com a opção da IA, para a partida ser a mesma a cada execução).
   */
  @Test
  void plano_preparacao_e_partida_batem_com_as_amostras_douradas() throws Exception {
    String auth = bearer();
    String career = goldenCareer(auth);
    String base = "/api/v1/careers/" + career;
    assertCareerGolden("game-plan.json", mvc.perform(get(base + "/game-plan").header("Authorization", auth)).andReturn());
    assertCareerGolden("playbook.json", mvc.perform(get(base + "/playbook").header("Authorization", auth)).andReturn());
    MvcResult conflict = mvc.perform(put(base + "/game-plan").header("Authorization", auth).header("X-Correlation-Id", "golden-409-game-plan")
        .header("Accept-Language", "pt-BR").contentType(MediaType.APPLICATION_JSON).content("{\"version\":7,\"passRatio\":60,\"fourthDownAggression\":\"NORMAL\","
            + "\"tempo\":\"NORMAL\",\"defensiveFocus\":\"BALANCED\",\"blitzFrequency\":\"NORMAL\",\"baseDefense\":\"BASE_4_3\"}")).andReturn();
    assertThat(conflict.getResponse().getStatus()).as(conflict.getResponse().getContentAsString()).isEqualTo(409);
    assertCareerGolden("problem-409-game-plan.json", conflict);
    assertCareerGolden("preparation.json", mvc.perform(get(base + "/next-match/preparation").header("Authorization", auth)).andReturn());

    MvcResult week = mvc.perform(post(base + "/weeks/current/play").header("Authorization", auth)).andReturn();
    assertThat(week.getResponse().getStatus()).as(week.getResponse().getContentAsString()).isEqualTo(200);
    assertCareerGolden("week.json", week);
    String mid = json.readTree(week.getResponse().getContentAsString()).get("matchId").asString();
    MvcResult adv = mvc.perform(post("/api/v1/matches/" + mid + "/advance").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
        .content("{\"mode\":\"TO_DECISION\"}")).andReturn();
    assertThat(json.readTree(adv.getResponse().getContentAsString()).get("status").asString()).isEqualTo("AWAITING_DECISION");
    assertCareerGolden("match-state.json", mvc.perform(get("/api/v1/matches/" + mid).header("Authorization", auth)).andReturn());
    assertCareerGolden("match-events.json", mvc.perform(get("/api/v1/matches/" + mid + "/events?after=0&limit=12").header("Authorization", auth)).andReturn());

    JsonNode state = json.readTree(adv.getResponse().getContentAsString());
    int guard = 0;
    while (!"FINISHED".equals(state.get("status").asString())) {
      assertThat(++guard).isLessThan(500);
      MvcResult next;
      if ("AWAITING_DECISION".equals(state.get("status").asString())) {
        JsonNode d = state.get("live").get("decision");
        next = mvc.perform(post("/api/v1/matches/" + mid + "/play-calls/" + d.get("id").asString()).header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("optionCode", d.get("autoOptionCode").asString(),
                "version", state.get("live").get("version").asLong())))).andReturn();
      } else {
        next = mvc.perform(post("/api/v1/matches/" + mid + "/advance").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"mode\":\"FULL\"}")).andReturn();
      }
      assertThat(next.getResponse().getStatus()).as(next.getResponse().getContentAsString()).isEqualTo(200);
      state = json.readTree(next.getResponse().getContentAsString());
    }
    assertCareerGolden("match-report.json", mvc.perform(get("/api/v1/matches/" + mid + "/report").header("Authorization", auth)).andReturn());
  }

  /**
   * Bloco 6c, numa temporada curta (2 jogos por franquia): depois da semana 1, a classificação (projeção), a moral e o analista; depois
   * da última semana regular, as notícias dela, os playoffs começando e o 422 do avanço; no fim, a chave completa, o resumo, os troféus,
   * o avanço e o 409 do segundo avanço da mesma temporada.
   */
  @Test
  void temporada_bate_com_as_amostras_douradas() throws Exception {
    String auth = bearer();
    MatchFixtures.level(mvc, auth, "NONE");
    UUID world = seeder.seedIfMissing().getId();
    JsonNode clone = json.readTree(mvc.perform(post("/api/v1/worlds/" + world + "/clone").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Temporada Dourada\"}")).andReturn().getResponse().getContentAsString());
    String cloned = clone.get("id").asString();
    JsonNode leagues = json.readTree(mvc.perform(get("/api/v1/worlds/" + cloned + "/leagues").header("Authorization", auth)).andReturn().getResponse()
        .getContentAsString());
    MvcResult rules = mvc.perform(put("/api/v1/leagues/" + leagues.get(0).get("id").asString()).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
        .content("{\"rules\":{\"regularSeasonWeeks\":2,\"gamesPerTeam\":2,\"byeWeeksPerTeam\":0}}")).andReturn();
    assertThat(rules.getResponse().getStatus()).as(rules.getResponse().getContentAsString()).isEqualTo(200);
    String club = null;
    JsonNode clubs = json.readTree(mvc.perform(get("/api/v1/leagues/" + leagues.get(0).get("id").asString() + "/clubs").header("Authorization", auth))
        .andReturn().getResponse().getContentAsString());
    for (JsonNode c : clubs) if ("PAV".equals(c.get("shortName").asString())) club = c.get("id").asString();
    Map<String, Object> body = new HashMap<>(Map.of("name", "Temporada Dourada", "worldId", cloned, "clubId", club, "coachName", "Treinadora Dourada",
        "coachNationality", "BRA", "coachStyle", "OFFENSIVE", "difficulty", "NORMAL", "seed", 2026L));
    MvcResult created = mvc.perform(post("/api/v1/careers").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body))).andReturn();
    assertThat(created.getResponse().getStatus()).as(created.getResponse().getContentAsString()).isEqualTo(201);
    String base = "/api/v1/careers/" + json.readTree(created.getResponse().getContentAsString()).get("id").asString();

    playSeasonWeek(auth, base);
    assertCareerGolden("standings.json", mvc.perform(get(base + "/standings").header("Authorization", auth)).andReturn());
    assertCareerGolden("morale.json", mvc.perform(get(base + "/morale").header("Authorization", auth)).andReturn());
    MvcResult analysis = mvc.perform(post(base + "/analysis/refresh").header("Authorization", auth).header("Accept-Language", "pt-BR")
        .with(r -> { r.setRemoteAddr("198.51.100.66"); return r; })).andReturn();
    assertThat(analysis.getResponse().getStatus()).as(analysis.getResponse().getContentAsString()).isEqualTo(200);
    assertCareerGolden("analysis.json", analysis);

    playSeasonWeek(auth, base);
    assertCareerGolden("week-news.json", mvc.perform(get(base + "/weeks/2/news").header("Authorization", auth).header("Accept-Language", "pt-BR")).andReturn());
    assertCareerGolden("playoffs.json", mvc.perform(get(base + "/playoffs").header("Authorization", auth)).andReturn());
    MvcResult early = mvc.perform(post(base + "/season/advance").header("Authorization", auth).header("X-Correlation-Id", "golden-422-season")
        .header("Accept-Language", "pt-BR")).andReturn();
    assertThat(early.getResponse().getStatus()).as(early.getResponse().getContentAsString()).isEqualTo(422);
    assertCareerGolden("problem-422-season-not-finished.json", early);

    for (int guard = 0; playSeasonWeek(auth, base); guard++) assertThat(guard).isLessThan(10);
    assertCareerGolden("playoffs-bracket.json", mvc.perform(get(base + "/playoffs/bracket").header("Authorization", auth)).andReturn());
    MvcResult summary = mvc.perform(get(base + "/season/summary").header("Authorization", auth)).andReturn();
    assertCareerGolden("season-summary.json", summary);
    assertCareerGolden("trophies.json", mvc.perform(get(base + "/trophies").header("Authorization", auth)).andReturn());
    String seasonId = json.readTree(summary.getResponse().getContentAsString()).get("seasonId").asString();
    String advanceBody = "{\"seasonId\":\"" + seasonId + "\"}";
    MvcResult advanced = mvc.perform(post(base + "/season/advance").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(advanceBody))
        .andReturn();
    assertThat(advanced.getResponse().getStatus()).as(advanced.getResponse().getContentAsString()).isEqualTo(200);
    assertCareerGolden("season-advance.json", advanced);
    MvcResult again = mvc.perform(post(base + "/season/advance").header("Authorization", auth).header("X-Correlation-Id", "golden-409-season")
        .header("Accept-Language", "pt-BR").contentType(MediaType.APPLICATION_JSON).content(advanceBody)).andReturn();
    assertThat(again.getResponse().getStatus()).as(again.getResponse().getContentAsString()).isEqualTo(409);
    assertCareerGolden("problem-409-season-advance.json", again);
  }

  /** Joga a semana atual da temporada dourada (a partida do usuário até o fim); {@code false} quando a temporada acabou. */
  private boolean playSeasonWeek(String auth, String base) throws Exception {
    MvcResult r = mvc.perform(post(base + "/weeks/current/play").header("Authorization", auth)).andReturn();
    if (r.getResponse().getStatus() == 409) return false;
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    JsonNode board = json.readTree(r.getResponse().getContentAsString());
    if (board.hasNonNull("matchId")) MatchFixtures.finish(mvc, auth, board.get("matchId").asString(), "FULL");
    return true;
  }

  /** Franquia "PAV" do mundo padrão, pela lista da liga (mesmas rotas que o front do editor usa). */
  private JsonNode defaultClub(String auth) throws Exception {
    UUID world = seeder.seedIfMissing().getId();
    JsonNode leagues = json.readTree(mvc.perform(get("/api/v1/worlds/" + world + "/leagues").header("Authorization", auth)).andReturn().getResponse().getContentAsString());
    JsonNode clubs = json.readTree(mvc.perform(get("/api/v1/leagues/" + leagues.get(0).get("id").asString() + "/clubs").header("Authorization", auth))
        .andReturn().getResponse().getContentAsString());
    for (JsonNode c : clubs) if ("PAV".equals(c.get("shortName").asString())) return c;
    throw new AssertionError("franquia PAV ausente do mundo padrão");
  }

  @Test
  void franquia_do_editor_bate_com_a_amostra_dourada() throws Exception {
    String auth = bearer();
    assertGolden("world-editor-club.json", get("/api/v1/clubs/" + defaultClub(auth).get("id").asString()).header("Authorization", auth));
  }

  @Test
  void jogador_do_editor_bate_com_a_amostra_dourada() throws Exception {
    String auth = bearer();
    String club = defaultClub(auth).get("id").asString();
    JsonNode roster = json.readTree(mvc.perform(get("/api/v1/clubs/" + club + "/players").header("Authorization", auth)).andReturn().getResponse().getContentAsString());
    String qb = null;
    for (JsonNode p : roster) if ("QB".equals(p.get("position").asString())) { qb = p.get("id").asString(); break; }
    assertThat(qb).as("QB titular da franquia").isNotNull();
    assertGolden("world-editor-player.json", get("/api/v1/players/" + qb).header("Authorization", auth));
  }
}
