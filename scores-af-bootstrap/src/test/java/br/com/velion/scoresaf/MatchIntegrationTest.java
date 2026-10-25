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
import static br.com.velion.scoresaf.MatchFixtures.finish;
import static br.com.velion.scoresaf.MatchFixtures.level;
import static br.com.velion.scoresaf.MatchFixtures.live;
import static br.com.velion.scoresaf.MatchFixtures.play;
import static br.com.velion.scoresaf.MatchFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchReportRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerMatchStatsRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.application.usecase.rf42.MatchSupport;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.sim.DefaultMatchSimulator;
import br.com.velion.scoresaf.domain.shared.FixtureStatus;
import br.com.velion.scoresaf.web.security.TokenService;
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
 * RF27 de ponta a ponta: jogar a semana (partida ao vivo do usuário, idempotente), situação da sessão (BREAK, HALFTIME), avanço,
 * fim da partida (placar no calendário, relatório, box score, tabela da divisão, os outros jogos simulados e a semana avançada),
 * semana de folga, play-by-play com cursor, determinismo (mesma semente ⇒ mesmo jogo, retomando do snapshot gravado), depth
 * chart inválido (422), controle de acesso (partida de outro usuário: 404) e a exclusão da carreira levando a partida junto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;
  @Autowired TokenService tokens;
  @Autowired MatchSessionRepository matchSessions;
  @Autowired MatchReportRepository reports;
  @Autowired PlayerMatchStatsRepository stats;
  @Autowired FixtureRepository fixtures;
  @Autowired TransactionTemplate tx;

  private record Career(String auth, String id, String base) {}

  private Career career(String name, String club, long seed, String level) throws Exception {
    UUID defaultWorld = seeder.seedIfMissing().getId();
    String auth = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    if (level != null) level(mvc, auth, level);
    Map<String, Object> c = create(mvc, auth, body(name, null, club(mvc, auth, defaultWorld, club), seed));
    return new Career(auth, id(c), "/api/v1/careers/" + id(c));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> games(Map<String, Object> board) { return (List<Map<String, Object>>) board.get("games"); }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> fixture(Map<String, Object> game) { return (Map<String, Object>) game.get("fixture"); }

  @Test
  @SuppressWarnings("unchecked")
  void jogar_a_semana_cria_a_partida_avanca_por_quarto_e_fecha_a_semana() throws Exception {
    Career c = career("Semana " + UUID.randomUUID().toString().substring(0, 6), "NES", 27L, "NONE");
    Map<String, Object> board = play(mvc, c.auth(), c.id());
    String mid = (String) board.get("matchId");
    assertThat(mid).isNotNull();
    assertThat(board).containsEntry("week", 1).containsEntry("currentWeek", 1).containsEntry("completed", false).containsEntry("userBye", false);
    assertThat(games(board)).hasSize(16).filteredOn(g -> (Boolean) fixture(g).get("userGame")).singleElement()
        .satisfies(g -> assertThat((Map<String, Object>) g.get("live")).containsEntry("matchId", mid).containsEntry("status", "NOT_STARTED"));
    // os outros jogos só são simulados quando a semana fecha
    assertThat(games(board)).filteredOn(g -> !(Boolean) fixture(g).get("userGame")).allSatisfy(g -> assertThat(fixture(g)).containsEntry("status", "SCHEDULED"));
    // idempotente
    assertThat(play(mvc, c.auth(), c.id())).containsEntry("matchId", mid);
    assertThat((Map<String, Object>) ok(call(mvc, get(c.base() + "/dashboard"), c.auth(), null)).get("live")).containsEntry("matchId", mid);

    Map<String, Object> s = state(mvc, c.auth(), mid);
    assertThat(s).containsEntry("status", "NOT_STARTED").containsEntry("adjustable", true).containsEntry("playCallingLevel", "NONE");
    assertProblem(call(mvc, get("/api/v1/matches/" + mid + "/report"), c.auth(), null), 422, "MATCH_NOT_FINISHED");
    assertProblem(call(mvc, post("/api/v1/matches/" + mid + "/advance"), c.auth(), Map.of("mode", "SPRINT")), 400, "INVALID_ADVANCE_MODE");
    assertProblem(call(mvc, post("/api/v1/matches/" + mid + "/advance"), c.auth(), Map.of("mode", "PLAY", "version", 99)), 409, "MATCH_VERSION_CONFLICT");

    s = advance(mvc, c.auth(), mid, "QUARTER");
    assertThat(s).containsEntry("status", "BREAK").containsEntry("adjustable", true);
    assertThat(live(s)).containsEntry("quarter", 2);
    s = advance(mvc, c.auth(), mid, "QUARTER");
    assertThat(s).containsEntry("status", "HALFTIME");
    s = advance(mvc, c.auth(), mid, "PLAY");
    assertThat(s).containsEntry("status", "LIVE").containsEntry("adjustable", false);

    s = finish(mvc, c.auth(), mid, "FULL");
    assertThat(s).containsEntry("status", "FINISHED").containsEntry("advanceModes", List.of());
    assertProblem(call(mvc, post("/api/v1/matches/" + mid + "/advance"), c.auth(), Map.of("mode", "PLAY")), 409, "MATCH_FINISHED");
    int home = ((Number) ((Map<String, Object>) live(s).get("home")).get("score")).intValue();
    int away = ((Number) ((Map<String, Object>) live(s).get("away")).get("score")).intValue();

    // a semana fechou: todos os jogos encerrados, a semana atual avançou e a tabela da divisão conta o jogo
    Map<String, Object> after = ok(call(mvc, get(c.base() + "/weeks/1"), c.auth(), null));
    assertThat(after).containsEntry("completed", true).containsEntry("status", "COMPLETED").containsEntry("currentWeek", 2);
    assertThat(games(after)).allSatisfy(g -> assertThat(fixture(g)).containsEntry("status", "FINISHED").containsKeys("homeScore", "awayScore"));
    Map<String, Object> userGame = games(after).stream().filter(g -> (Boolean) fixture(g).get("userGame")).findFirst().orElseThrow();
    assertThat(fixture(userGame)).containsEntry("homeScore", home).containsEntry("awayScore", away);
    assertThat(ok(call(mvc, get(c.base() + "/weeks/2"), c.auth(), null))).containsEntry("status", "CURRENT").containsEntry("completed", false);
    Map<String, Object> dash = ok(call(mvc, get(c.base() + "/dashboard"), c.auth(), null));
    assertThat(dash).containsEntry("currentWeek", 2).containsEntry("live", null);
    Map<String, Object> record = (Map<String, Object>) dash.get("record");
    assertThat(((Number) record.get("wins")).intValue() + ((Number) record.get("losses")).intValue() + ((Number) record.get("ties")).intValue()).isEqualTo(1);
    assertThat((List<Map<String, Object>>) dash.get("division")).allSatisfy(row -> {
      Map<String, Object> r = (Map<String, Object>) row.get("record");
      assertThat(((Number) r.get("wins")).intValue() + ((Number) r.get("losses")).intValue() + ((Number) r.get("ties")).intValue()).isEqualTo(1);
    });

    // relatório do jogo do usuário (id da partida) e de um jogo da IA (id do jogo do calendário)
    Map<String, Object> report = ok(call(mvc, get("/api/v1/matches/" + mid + "/report"), c.auth(), null));
    assertThat(report).containsEntry("matchId", mid).containsEntry("userGame", true).containsKeys("home", "away", "leaders", "drives", "scoringPlays", "decisions");
    assertThat((Map<String, Object>) report.get("home")).containsEntry("score", home);
    String aiFixture = (String) fixture(games(after).stream().filter(g -> !(Boolean) fixture(g).get("userGame")).findFirst().orElseThrow()).get("id");
    assertThat(ok(call(mvc, get("/api/v1/matches/" + aiFixture + "/report"), c.auth(), null))).containsEntry("userGame", false).doesNotContainKey("matchId");
    assertThat(stats.countByCareerId(UUID.fromString(c.id()))).isGreaterThan(16 * 40L);
    assertThat(reports.countByCareerId(UUID.fromString(c.id()))).isEqualTo(16);
  }

  @Test
  void play_by_play_com_cursor() throws Exception {
    Career c = career("Cursor " + UUID.randomUUID().toString().substring(0, 6), "CRU", 5L, "NONE");
    String mid = (String) play(mvc, c.auth(), c.id()).get("matchId");
    advance(mvc, c.auth(), mid, "QUARTER");
    List<Map<String, Object>> page = MatchFixtures.events(mvc, c.auth(), mid, 10, 5);
    assertThat(page).extracting(e -> ((Number) e.get("seq")).intValue()).containsExactly(11, 12, 13, 14, 15);
    assertThat(page).allSatisfy(e -> assertThat((String) e.get("textKey")).startsWith("match:"));
    Map<String, Object> all = ok(call(mvc, get("/api/v1/matches/" + mid + "/events"), c.auth(), null));
    assertThat(all).containsEntry("finished", false).containsEntry("after", 0);
    assertThat(((List<?>) all.get("events")).size()).isEqualTo(((Number) all.get("lastSeq")).intValue());
  }

  /** Mesma semente ⇒ mesmo jogo: retomando do snapshot gravado a cada quarto, igual ao motor direto e igual a outra carreira com a mesma semente. */
  @Test
  void mesma_semente_mesmo_jogo_inclusive_retomando_do_snapshot() throws Exception {
    Career a = career("Det A " + UUID.randomUUID().toString().substring(0, 6), "PAV", 4242L, "NONE");
    Career b = career("Det B " + UUID.randomUUID().toString().substring(0, 6), "PAV", 4242L, "NONE");
    String ma = (String) play(mvc, a.auth(), a.id()).get("matchId");
    String mb = (String) play(mvc, b.auth(), b.id()).get("matchId");
    int stops = 0;
    Map<String, Object> s = state(mvc, a.auth(), ma);
    while (!"FINISHED".equals(s.get("status"))) { s = advance(mvc, a.auth(), ma, "QUARTER"); stops++; }
    assertThat(stops).isGreaterThanOrEqualTo(4);
    finish(mvc, b.auth(), mb, "FULL");

    MatchSnapshot stored = tx.execute(t -> MatchSupport.read(matchSessions.findById(UUID.fromString(ma)).orElseThrow()));
    MatchResult direct = new DefaultMatchSimulator().simulate(stored.setup());
    assertThat(stored.events()).isEqualTo(direct.events());
    assertThat(stored.homeScore()).isEqualTo(direct.homeScore());
    assertThat(stored.awayScore()).isEqualTo(direct.awayScore());

    // outra carreira, mesma semente: mesmos lances (os ids são de outro mundo copiado, então compara o que não é id)
    List<String> la = MatchFixtures.events(mvc, a.auth(), ma, 0, 1000).stream().map(MatchIntegrationTest::sketch).toList();
    List<String> lb = MatchFixtures.events(mvc, b.auth(), mb, 0, 1000).stream().map(MatchIntegrationTest::sketch).toList();
    assertThat(la).hasSizeGreaterThan(100).isEqualTo(lb);
    assertThat(direct.events().stream().map(PlayEvent::textKey).toList()).isEqualTo(
        MatchFixtures.events(mvc, a.auth(), ma, 0, 1000).stream().map(e -> e.get("textKey")).toList());
  }

  private static String sketch(Map<String, Object> e) {
    return e.get("seq") + "|" + e.get("quarter") + "|" + e.get("clockSeconds") + "|" + e.get("down") + "|" + e.get("distance") + "|" + e.get("ballOnAbsYd")
        + "|" + e.get("type") + "|" + e.get("concept") + "|" + e.get("yards") + "|" + e.get("result") + "|" + e.get("homeScore") + "-" + e.get("awayScore");
  }

  @Test
  @SuppressWarnings("unchecked")
  void semana_de_folga_fecha_na_hora_e_avanca() throws Exception {
    Career c = career("Folga " + UUID.randomUUID().toString().substring(0, 6), "SLU", 9L, "NONE");
    Map<String, Object> cal = ok(call(mvc, get(c.base() + "/calendar"), c.auth(), null));
    int bye = ((List<Map<String, Object>>) cal.get("weeks")).stream().filter(w -> (Boolean) w.get("userBye")).mapToInt(w -> ((Number) w.get("number")).intValue())
        .findFirst().orElseThrow();
    for (int w = 1; w < bye; w++) {
      Map<String, Object> board = play(mvc, c.auth(), c.id());
      finish(mvc, c.auth(), (String) board.get("matchId"), "FULL");
    }
    Map<String, Object> board = play(mvc, c.auth(), c.id());
    assertThat(board).containsEntry("week", bye).containsEntry("userBye", true).containsEntry("completed", true).containsEntry("currentWeek", bye + 1)
        .doesNotContainKey("matchId");
    assertThat(games(board)).allSatisfy(g -> assertThat(fixture(g)).containsEntry("status", "FINISHED"));
    assertThat((List<?>) board.get("byeClubIds")).isNotEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void depth_chart_invalido_impede_o_kickoff() throws Exception {
    Career c = career("Sem QB " + UUID.randomUUID().toString().substring(0, 6), "NES", 3L, null);
    Map<String, Object> roster = ok(call(mvc, get(c.base() + "/roster"), c.auth(), null));
    for (Map<String, Object> p : ((Map<String, List<Map<String, Object>>>) roster.get("units")).get("OFFENSE")) {
      if ("QB".equals(p.get("position"))) ok(call(mvc, post(c.base() + "/players/" + p.get("id") + "/release"), c.auth(), null));
    }
    MvcResult r = call(mvc, post(c.base() + "/weeks/current/play"), c.auth(), null);
    assertProblem(r, 422, "DEPTH_CHART_INVALID");
    assertThat((List<Map<String, Object>>) ((Map<String, Object>) map(r).get("details")).get("errors"))
        .anySatisfy(e -> assertThat(e).containsEntry("code", "DEPTH_CHART_INCOMPLETE").containsEntry("slot", "QB"));
    assertThat(fixtures.findBySeasonIdAndWeekNumber(UUID.fromString((String) ok(call(mvc, get(c.base()), c.auth(), null)).get("seasonId")), 1))
        .allSatisfy(f -> assertThat(f.getStatus()).isEqualTo(FixtureStatus.SCHEDULED));
  }

  @Test
  @SuppressWarnings("unchecked")
  void partida_de_outro_usuario_responde_404() throws Exception {
    Career c = career("Bola " + UUID.randomUUID().toString().substring(0, 6), "CRU", 12L, "NONE");
    Map<String, Object> board = play(mvc, c.auth(), c.id());
    String mid = (String) board.get("matchId");
    String fixtureId = (String) fixture(games(board).get(0)).get("id");
    String intruder = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    long before = ((Number) live(state(mvc, c.auth(), mid)).get("version")).longValue();
    assertProblem(call(mvc, get("/api/v1/matches/" + mid), intruder, null), 404, "NOT_FOUND");
    assertProblem(call(mvc, get("/api/v1/matches/" + mid + "/events"), intruder, null), 404, "NOT_FOUND");
    assertProblem(call(mvc, get("/api/v1/matches/" + mid + "/report"), intruder, null), 404, "NOT_FOUND");
    assertProblem(call(mvc, get("/api/v1/matches/" + fixtureId + "/report"), intruder, null), 404, "NOT_FOUND");
    assertProblem(call(mvc, post("/api/v1/matches/" + mid + "/advance"), intruder, Map.of("mode", "FULL")), 404, "NOT_FOUND");
    assertProblem(call(mvc, post("/api/v1/matches/" + mid + "/decisions"), intruder,
        Map.of("version", before, "adjustments", List.of(Map.of("type", "TIMEOUT")))), 404, "NOT_FOUND");
    assertProblem(call(mvc, post("/api/v1/matches/" + mid + "/play-calls/dec-1"), intruder, Map.of("optionCode", "INSIDE_RUN", "version", before)), 404,
        "NOT_FOUND");
    // id inexistente: o mesmo 404
    assertProblem(call(mvc, get("/api/v1/matches/" + UUID.randomUUID()), intruder, null), 404, "NOT_FOUND");
    assertThat(state(mvc, c.auth(), mid)).containsEntry("status", "NOT_STARTED");
    assertThat(((Number) live(state(mvc, c.auth(), mid)).get("version")).longValue()).isEqualTo(before);
  }

  @Test
  void excluir_a_carreira_leva_a_partida_o_relatorio_e_o_plano() throws Exception {
    String name = "Apagar " + UUID.randomUUID().toString().substring(0, 6);
    Career c = career(name, "NES", 44L, "NONE");
    ok(call(mvc, get(c.base() + "/game-plan"), c.auth(), null));
    ok(call(mvc, get(c.base() + "/playbook"), c.auth(), null));
    String mid = (String) play(mvc, c.auth(), c.id()).get("matchId");
    finish(mvc, c.auth(), mid, "FULL");
    String mid2 = (String) play(mvc, c.auth(), c.id()).get("matchId");
    advance(mvc, c.auth(), mid2, "QUARTER");
    UUID careerId = UUID.fromString(c.id());
    assertThat(reports.countByCareerId(careerId)).isEqualTo(16);
    assertThat(call(mvc, delete(c.base() + "?confirm=" + name), c.auth(), null).getResponse().getStatus()).isEqualTo(204);
    assertThat(matchSessions.findById(UUID.fromString(mid))).isEmpty();
    assertThat(matchSessions.findById(UUID.fromString(mid2))).isEmpty();
    assertThat(reports.countByCareerId(careerId)).isZero();
    assertThat(stats.countByCareerId(careerId)).isZero();
    assertProblem(call(mvc, get("/api/v1/matches/" + mid), c.auth(), null), 404, "NOT_FOUND");
  }
}
