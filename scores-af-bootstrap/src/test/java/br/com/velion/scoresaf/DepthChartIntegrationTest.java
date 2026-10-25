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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.application.usecase.rf42.SimulateExhibitionMatchUseCase;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore;
import br.com.velion.scoresaf.web.security.TokenService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * RF60 de ponta a ponta: depth chart padrão (do automático do motor) válido, validações (jogador indisponível, repetido na vaga,
 * vaga incompleta, vaga desconhecida, mais que os 46 ativos), aviso de fora de posição, versão otimista (409 com a versão atual),
 * papel de retornador e o motor usando o depth chart salvo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DepthChartIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;
  @Autowired TokenService tokens;
  @Autowired SimulateExhibitionMatchUseCase exhibition;

  String auth;
  String base;
  Map<String, Object> career;

  @BeforeEach
  void career() throws Exception {
    UUID defaultWorld = seeder.seedIfMissing().getId();
    auth = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    career = create(mvc, auth, body("Depth " + UUID.randomUUID().toString().substring(0, 8), null, club(mvc, auth, defaultWorld, "NES"), 31L));
    base = "/api/v1/careers/" + id(career);
  }

  private Map<String, Object> chart() throws Exception { return ok(call(mvc, get(base + "/depth-chart"), auth, null)); }

  /** Vaga → ids, na ordem do depth chart. */
  @SuppressWarnings("unchecked")
  private static Map<String, List<String>> slots(Map<String, Object> chart) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    for (Map<String, Object> u : (List<Map<String, Object>>) chart.get("units")) {
      for (Map<String, Object> s : (List<Map<String, Object>>) u.get("slots")) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> e : (List<Map<String, Object>>) s.get("players")) ids.add((String) e.get("playerId"));
        out.put((String) s.get("slot"), ids);
      }
    }
    return out;
  }

  private static List<Map<String, Object>> request(Map<String, List<String>> slots) {
    List<Map<String, Object>> out = new ArrayList<>();
    slots.forEach((k, v) -> out.add(Map.of("slot", k, "playerIds", v)));
    return out;
  }

  private MvcResult putChart(long version, Map<String, List<String>> slots) throws Exception {
    return call(mvc, put(base + "/depth-chart"), auth, Map.of("version", version, "slots", request(slots)));
  }

  private static long version(Map<String, Object> chart) { return ((Number) chart.get("version")).longValue(); }

  @SuppressWarnings("unchecked")
  private Map<String, Object> rosterCard(String position) throws Exception {
    Map<String, Object> r = ok(call(mvc, get(base + "/roster"), auth, null));
    for (List<Map<String, Object>> unit : ((Map<String, List<Map<String, Object>>>) r.get("units")).values()) {
      for (Map<String, Object> c : unit) if (position.equals(c.get("position"))) return c;
    }
    throw new AssertionError(position);
  }

  @Test
  void depth_chart_padrao_e_valido_e_cabe_nos_46_ativos() throws Exception {
    Map<String, Object> c = chart();
    assertThat(c).containsEntry("version", 0).containsEntry("valid", true).containsEntry("gameDayActives", 46).containsEntry("errors", List.of());
    assertThat(((Number) c.get("uniquePlayers")).intValue()).isBetween(25, 46);
    Map<String, List<String>> s = slots(c);
    assertThat(s.keySet()).contains("QB", "WR", "LS", "KR", "PR", "H", "THIRD_DOWN_BACK");
    assertThat(s.get("QB")).hasSizeGreaterThanOrEqualTo(1);
    assertThat(s.get("WR")).hasSizeGreaterThanOrEqualTo(3);
    assertThat(s.get("KR")).isNotEmpty();
    assertThat(s.get("H")).isNotEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void validacoes_versao_otimista_e_aviso_de_fora_de_posicao() throws Exception {
    Map<String, Object> c = chart();
    Map<String, List<String>> s = slots(c);

    MvcResult stale = putChart(5, s);
    assertProblem(stale, 409, "DEPTH_CHART_VERSION_CONFLICT");
    assertThat((Map<String, Object>) map(stale).get("details")).containsEntry("currentVersion", 0).containsEntry("expectedVersion", 5);

    Map<String, Object> roster = ok(call(mvc, get(base + "/roster"), auth, null));
    String psPlayer = (String) ((List<Map<String, Object>>) roster.get("practiceSquad")).get(0).get("id");
    Map<String, List<String>> bad = new LinkedHashMap<>(s);
    List<String> qb = new ArrayList<>(s.get("QB"));
    qb.add(0, psPlayer);
    bad.put("QB", qb);
    MvcResult unavailable = putChart(0, bad);
    assertProblem(unavailable, 422, "DEPTH_CHART_PLAYER_UNAVAILABLE");
    assertThat((List<Map<String, Object>>) map(unavailable).get("errors")).first().satisfies(e -> assertThat(e)
        .containsEntry("slot", "QB").containsEntry("depth", 1).containsEntry("playerId", psPlayer));

    Map<String, List<String>> dup = new LinkedHashMap<>(s);
    List<String> wr = new ArrayList<>(s.get("WR"));
    wr.add(wr.get(0));
    dup.put("WR", wr);
    assertProblem(putChart(0, dup), 422, "DEPTH_CHART_DUPLICATE_PLAYER");

    Map<String, List<String>> empty = new LinkedHashMap<>(s);
    empty.put("QB", List.of());
    assertProblem(putChart(0, empty), 422, "DEPTH_CHART_INCOMPLETE");

    Map<String, List<String>> unknown = new LinkedHashMap<>(s);
    unknown.put("SLOTX", List.of(s.get("QB").get(0)));
    assertProblem(putChart(0, unknown), 400, "DEPTH_CHART_UNKNOWN_SLOT");

    // mais jogadores distintos que os 46 ativos do dia de jogo
    Set<String> inChart = new LinkedHashSet<>();
    s.values().forEach(inChart::addAll);
    List<String> outside = new ArrayList<>();
    for (List<Map<String, Object>> unit : ((Map<String, List<Map<String, Object>>>) roster.get("units")).values()) {
      for (Map<String, Object> card : unit) if (!inChart.contains(card.get("id"))) outside.add((String) card.get("id"));
    }
    Map<String, List<String>> many = new LinkedHashMap<>(s);
    List<String> fb = new ArrayList<>(s.get("FB"));
    for (String id : outside) if (fb.size() < 10) fb.add(id);
    many.put("FB", fb);
    List<String> tdb = new ArrayList<>(s.get("THIRD_DOWN_BACK"));
    for (String id : outside) if (!fb.contains(id) && tdb.size() < 10) tdb.add(id);
    many.put("THIRD_DOWN_BACK", tdb);
    Set<String> uniq = new LinkedHashSet<>();
    many.values().forEach(uniq::addAll);
    assertThat(uniq.size()).as("pré-condição: passa de 46").isGreaterThan(46);
    assertProblem(putChart(0, many), 422, "DEPTH_CHART_TOO_MANY_ACTIVES");

    // fora de posição é aviso, não erro: um WR como 3º QB grava e volta com OUT_OF_POSITION
    Map<String, List<String>> oop = new LinkedHashMap<>(s);
    List<String> qbs = new ArrayList<>(s.get("QB").subList(0, Math.min(2, s.get("QB").size())));
    qbs.add(s.get("WR").get(0));
    oop.put("QB", qbs);
    Map<String, Object> saved = ok(putChart(0, oop));
    assertThat(version(saved)).isEqualTo(1);
    assertThat((List<Map<String, Object>>) saved.get("warnings")).anySatisfy(w -> assertThat(w).containsEntry("code", "OUT_OF_POSITION")
        .containsEntry("slot", "QB").containsEntry("playerId", s.get("WR").get(0)));
    assertThat(slots(chart()).get("QB")).isEqualTo(qbs);
    assertProblem(putChart(0, s), 409, "DEPTH_CHART_VERSION_CONFLICT");
  }

  @Test
  void plano_de_jogo_padrao_do_estilo_do_head_coach_so_leitura() throws Exception {
    Map<String, Object> plan = ok(call(mvc, get(base + "/game-plan"), auth, null));
    assertThat(plan).containsEntry("coachStyle", "OFFENSIVE").containsEntry("passRatio", 58).containsEntry("fourthDownAggression", "AGGRESSIVE")
        .containsEntry("source", "COACH_STYLE").containsEntry("editable", false);
  }

  @Test
  void papel_de_retornador_pelo_elenco() throws Exception {
    Map<String, Object> wr = rosterCard("WR");
    long v = version(chart());
    Map<String, Object> after = ok(call(mvc, patch(base + "/players/" + wr.get("id") + "/role"), auth, Map.of("role", "KR", "depth", 1, "version", v)));
    assertThat(slots(after).get("KR").get(0)).isEqualTo(wr.get("id"));
    assertThat(version(after)).isEqualTo(v + 1);
    assertProblem(call(mvc, patch(base + "/players/" + wr.get("id") + "/role"), auth, Map.of("role", "QB")), 400, "DEPTH_CHART_UNKNOWN_SLOT");
    assertProblem(call(mvc, patch(base + "/players/" + wr.get("id") + "/role"), auth, Map.of("role", "PR", "version", v)), 409, "DEPTH_CHART_VERSION_CONFLICT");
  }

  /** O motor usa o depth chart salvo: o QB2 promovido a titular faz os passes; antes, o titular era o QB1. */
  @Test
  @SuppressWarnings("unchecked")
  void motor_usa_o_depth_chart_salvo() throws Exception {
    Map<String, Object> c = chart();
    Map<String, List<String>> s = slots(c);
    String qb1 = s.get("QB").get(0), qb2 = s.get("QB").get(1);
    UUID me = UUID.fromString((String) career.get("clubId"));
    Map<String, Object> cal = ok(call(mvc, get(base + "/calendar"), auth, null));
    UUID opponent = ((List<Map<String, Object>>) cal.get("clubs")).stream().map(x -> UUID.fromString((String) x.get("id"))).filter(id -> !id.equals(me))
        .findFirst().orElseThrow();

    MatchResult before = exhibition.execute(me, opponent, 99L);
    assertThat(passes(before, qb1)).as("antes: QB1 titular passa").isPositive();
    assertThat(passes(before, qb2)).isZero();

    Map<String, List<String>> swapped = new LinkedHashMap<>(s);
    List<String> qbs = new ArrayList<>(s.get("QB"));
    qbs.set(0, qb2); qbs.set(1, qb1);
    swapped.put("QB", qbs);
    ok(putChart(version(c), swapped));

    MatchResult after = exhibition.execute(me, opponent, 99L);
    assertThat(passes(after, qb2)).as("depois: o QB2 promovido no depth chart salvo passa").isPositive();
    assertThat(passes(after, qb1)).isZero();
  }

  private static int passes(MatchResult r, String playerId) {
    UUID id = UUID.fromString(playerId);
    return r.homePlayers().stream().filter(p -> p.playerId().equals(id)).map(PlayerBoxScore::passing).filter(p -> p != null)
        .mapToInt(PlayerBoxScore.PassingLine::attempts).sum();
  }
}
