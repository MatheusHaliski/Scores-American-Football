package br.com.velion.scoresaf;

import static br.com.velion.scoresaf.CareerFixtures.body;
import static br.com.velion.scoresaf.CareerFixtures.call;
import static br.com.velion.scoresaf.CareerFixtures.club;
import static br.com.velion.scoresaf.CareerFixtures.create;
import static br.com.velion.scoresaf.CareerFixtures.id;
import static br.com.velion.scoresaf.CareerFixtures.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.GamePlanSetupRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchReportRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlaybookSetupRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerMatchStatsRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WorldRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.application.usecase.rf46.AccountDeletionUseCases;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.web.security.TokenService;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import br.com.velion.scoresaf.domain.catalog.World;
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
import tools.jackson.core.type.TypeReference;

/**
 * RF46 CA07 / LGPD com a carreira plugada no {@code AccountDataContributor} e no {@code AccountErasureHandler} (D-12): a exportação
 * (JSON imediato e o arquivo do pedido) leva as carreiras e os movimentos do elenco; a eliminação da conta apaga as carreiras com o
 * mundo de cada uma e não toca no mundo padrão. Bloco 6b: as partidas, o plano de jogo e o playbook entram na exportação e saem na
 * eliminação. Bloco 6c: as temporadas (campeã, semente do usuário, prêmios) e os relatórios do analista entram na exportação; sementes,
 * prêmios e relatórios saem na eliminação.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareerAccountDataIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;
  @Autowired TokenService tokens;
  @Autowired CareerSaveRepository saves;
  @Autowired WorldRepository worlds;
  @Autowired PlayerRepository players;
  @Autowired FixtureRepository fixtures;
  @Autowired AccountDeletionUseCases deletion;
  @Autowired MatchSessionRepository matchSessions;
  @Autowired MatchReportRepository reports;
  @Autowired PlayerMatchStatsRepository stats;
  @Autowired GamePlanSetupRepository plans;
  @Autowired PlaybookSetupRepository playbooks;
  @Autowired PlatformTransactionManager txManager;
  @Autowired br.com.velion.scoresaf.application.port.out.persistence.PlayoffSeedRepository seeds;
  @Autowired br.com.velion.scoresaf.application.port.out.persistence.SeasonAwardRepository awards;
  @Autowired br.com.velion.scoresaf.application.port.out.persistence.AnalysisReportRepository analyses;

  /** Bloco 6c: a exportação leva a temporada encerrada e o relatório do analista; a eliminação apaga sementes, prêmios e relatórios. */
  @Test
  @SuppressWarnings("unchecked")
  void exportacao_leva_temporada_e_analista_e_eliminacao_apaga_sementes_premios_e_relatorios() throws Exception {
    UUID defaultWorld = seeder.seedIfMissing().getId();
    TestSessions.Session s = TestSessions.open(users, sessions, tokens, UserRole.PLAYER);
    String auth = s.bearer();
    SeasonFixtures.Career c = SeasonFixtures.shortCareer(mvc, auth, defaultWorld, "Temporada Exportada", "CRU", 6L, 1);
    ok(call(mvc, post(c.base() + "/analysis/refresh").with(r -> { r.setRemoteAddr("203.0.113.81"); return r; }), auth, null));
    SeasonFixtures.playToEnd(mvc, c);
    UUID careerId = UUID.fromString(c.id());
    UUID seasonId = UUID.fromString((String) ok(call(mvc, get(c.base()), auth, null)).get("seasonId"));
    assertThat(seeds.findBySeasonIdOrderBySeedAsc(seasonId)).hasSize(14);
    assertThat(awards.findBySeasonId(seasonId)).isNotEmpty();

    Map<String, Object> export = ok(call(mvc, get("/api/v1/me/export"), auth, null));
    assertThat((List<Map<String, Object>>) export.get("careerSeasons")).singleElement().satisfies(row -> {
      assertThat(row).containsEntry("careerId", c.id()).containsEntry("number", 1).containsEntry("phase", "OFFSEASON");
      assertThat(row.get("champion")).isNotNull();
      assertThat((List<String>) row.get("awards")).anySatisfy(a -> assertThat(a).startsWith("MVP: "));
    });
    assertThat((List<Map<String, Object>>) export.get("careerAnalyses")).singleElement().satisfies(row -> assertThat(row).containsEntry("week", 1)
        .containsEntry("source", "RULES"));

    deletion.erase(users.findById(s.user().getId()).orElseThrow());
    assertThat(saves.findById(careerId)).isEmpty();
    assertThat(seeds.findBySeasonIdOrderBySeedAsc(seasonId)).isEmpty();
    assertThat(awards.findBySeasonId(seasonId)).isEmpty();
    assertThat(analyses.findByCareerIdOrderByCreatedAtAsc(careerId)).isEmpty();
    assertThat(worlds.findById(c.world())).as("o mundo modelo clonado no editor também sai (D-81)").isEmpty();
    assertThat(players.countByWorldId(defaultWorld)).isEqualTo(32 * 69);
  }

  @Test
  @SuppressWarnings("unchecked")
  void exportacao_leva_as_carreiras_e_eliminacao_apaga_carreira_e_mundo() throws Exception {
    UUID defaultWorld = seeder.seedIfMissing().getId();
    TestSessions.Session s = TestSessions.open(users, sessions, tokens, UserRole.PLAYER);
    String auth = s.bearer();
    Map<String, Object> c = create(mvc, auth, body("Exportada", null, club(mvc, auth, defaultWorld, "CRU"), 4L));
    Map<String, Object> roster = ok(call(mvc, get("/api/v1/careers/" + id(c) + "/roster"), auth, null));
    String someone = (String) ((List<Map<String, Object>>) roster.get("practiceSquad")).get(0).get("id");
    ok(call(mvc, post("/api/v1/careers/" + id(c) + "/players/" + someone + "/release"), auth, null));

    // JSON imediato (rota do RF3)
    Map<String, Object> export = ok(call(mvc, get("/api/v1/me/export"), auth, null));
    assertThat((List<Map<String, Object>>) export.get("careers")).singleElement().satisfies(row -> assertThat(row)
        .containsEntry("id", id(c)).containsEntry("name", "Exportada").containsEntry("coachName", "Treinadora Teste").containsEntry("status", "ACTIVE"));
    assertThat((List<Map<String, Object>>) export.get("careerRosterMoves")).singleElement().satisfies(row -> assertThat(row).containsEntry("kind", "RELEASE"));

    // arquivo do pedido de exportação (RF46 CA07)
    Map<String, Object> job = ok(call(mvc, post("/api/v1/me/export"), auth, null));
    assertThat(job).containsEntry("status", "READY");
    MvcResult file = call(mvc, get("/api/v1/me/export/" + job.get("id") + "/download?format=json"), auth, null);
    Map<String, Object> data = Jsons.MAPPER.readValue(file.getResponse().getContentAsByteArray(), new TypeReference<>() {});
    assertThat((List<Map<String, Object>>) data.get("careers")).extracting(r -> r.get("name")).containsExactly("Exportada");
    String csv = call(mvc, get("/api/v1/me/export/" + job.get("id") + "/download?format=csv"), auth, null).getResponse().getContentAsString();
    assertThat(csv).contains("careers;0;name;Exportada");

    // eliminação definitiva da conta: a carreira e o mundo dela somem; o mundo padrão fica
    UUID careerId = UUID.fromString(id(c));
    UUID careerWorld = UUID.fromString((String) c.get("worldId"));
    UUID seasonId = UUID.fromString((String) c.get("seasonId"));
    deletion.erase(users.findById(s.user().getId()).orElseThrow());
    assertThat(saves.findById(careerId)).isEmpty();
    assertThat(saves.findByUserIdOrderByCreatedAtAsc(s.user().getId())).isEmpty();
    assertThat(worlds.findById(careerWorld)).isEmpty();
    assertThat(players.countByWorldId(careerWorld)).isZero();
    assertThat(fixtures.countBySeasonId(seasonId)).isZero();
    assertThat(worlds.findById(defaultWorld)).isPresent();
    assertThat(players.countByWorldId(defaultWorld)).isEqualTo(32 * 69);
  }

  /** Bloco 6b: a exportação leva as partidas e o plano de jogo; a eliminação apaga partida, relatório, box score, plano e playbook. */
  @Test
  @SuppressWarnings("unchecked")
  void exportacao_leva_partidas_e_plano_e_eliminacao_apaga_tudo() throws Exception {
    UUID defaultWorld = seeder.seedIfMissing().getId();
    TestSessions.Session s = TestSessions.open(users, sessions, tokens, UserRole.PLAYER);
    String auth = s.bearer();
    MatchFixtures.level(mvc, auth, "NONE");
    Map<String, Object> c = create(mvc, auth, body("Partidas LGPD", null, club(mvc, auth, defaultWorld, "PAV"), 6L));
    String base = "/api/v1/careers/" + id(c);
    Map<String, Object> g = ok(call(mvc, get(base + "/game-plan"), auth, null));
    ok(call(mvc, put(base + "/game-plan"), auth, Map.of("version", g.get("version"), "passRatio", 70, "fourthDownAggression", "AGGRESSIVE",
        "tempo", "HURRY_UP", "defensiveFocus", "STOP_PASS", "blitzFrequency", "LOW", "baseDefense", "BASE_3_4")));
    ok(call(mvc, put(base + "/playbook"), auth, Map.of("version", 0, "situations",
        List.of(Map.of("situation", "RED_ZONE", "concepts", List.of(Map.of("item", "DEEP_PASS", "weight", 0)))))));
    String mid = (String) MatchFixtures.play(mvc, auth, id(c)).get("matchId");
    MatchFixtures.finish(mvc, auth, mid, "FULL");

    Map<String, Object> export = ok(call(mvc, get("/api/v1/me/export"), auth, null));
    assertThat((List<Map<String, Object>>) export.get("careerMatches")).singleElement().satisfies(row -> assertThat(row)
        .containsEntry("careerId", id(c)).containsEntry("week", 1).containsEntry("status", "FINISHED").containsEntry("playCallingLevel", "NONE")
        .containsKeys("home", "away", "homeScore", "awayScore", "seed", "playCalls", "adjustments"));
    assertThat((List<Map<String, Object>>) export.get("careerGamePlans")).extracting(r -> r.get("kind")).containsExactly("GAME_PLAN", "PLAYBOOK");
    assertThat((List<Map<String, Object>>) export.get("careerGamePlans")).first().satisfies(r -> assertThat(r).containsEntry("passRatio", 70));

    UUID careerId = UUID.fromString(id(c));
    assertThat(reports.countByCareerId(careerId)).isEqualTo(16);
    assertThat(stats.countByCareerId(careerId)).isPositive();
    deletion.erase(users.findById(s.user().getId()).orElseThrow());
    assertThat(saves.findById(careerId)).isEmpty();
    assertThat(matchSessions.findById(UUID.fromString(mid))).isEmpty();
    assertThat(reports.countByCareerId(careerId)).isZero();
    assertThat(stats.countByCareerId(careerId)).isZero();
    assertThat(plans.findByCareerId(careerId)).isEmpty();
    assertThat(playbooks.findByCareerId(careerId)).isEmpty();
  }

  /**
   * Q-27 (D-81): eliminar a conta apaga também os mundos modelo que o titular criou e clonou no editor (RF5), com o catálogo; o mundo
   * padrão fica. A carreira de outro usuário cujo mundo nasceu de um desses modelos sobrevive inteira: o mundo dela é uma cópia SAVE
   * própria (RNF1) e só perde a referência de origem. Pela API ninguém inicia carreira em mundo alheio (403), então o vínculo de
   * origem da carreira de B é gravado direto, como a cópia da criação o grava.
   */
  @Test
  @SuppressWarnings("unchecked")
  void eliminacao_apaga_os_mundos_modelo_do_titular_e_a_carreira_de_outro_usuario_sobrevive() throws Exception {
    UUID defaultWorld = seeder.seedIfMissing().getId();
    TestSessions.Session a = TestSessions.open(users, sessions, tokens, UserRole.PLAYER);
    TestSessions.Session b = TestSessions.open(users, sessions, tokens, UserRole.PLAYER);
    UUID created = UUID.fromString(id(ok(call(mvc, post("/api/v1/worlds"), a.bearer(), Map.of("name", "Criado por A")))));
    UUID cloned = UUID.fromString(id(ok(call(mvc, post("/api/v1/worlds/" + created + "/clone"), a.bearer(), Map.of("name", "Clone de A")))));
    Map<String, Object> careerA = create(mvc, a.bearer(), body("Carreira de A", created, club(mvc, a.bearer(), created, "PAV"), 2L));
    UUID careerAWorld = UUID.fromString((String) careerA.get("worldId"));

    Map<String, Object> careerB = create(mvc, b.bearer(), body("Carreira de B", null, club(mvc, b.bearer(), defaultWorld, "PAV"), 3L));
    UUID careerBId = UUID.fromString(id(careerB));
    UUID careerBWorld = UUID.fromString((String) careerB.get("worldId"));
    tx().executeWithoutResult(st -> {
      World w = worlds.findById(careerBWorld).orElseThrow();
      w.setSourceWorld(worlds.findById(created).orElseThrow());
      saves.findById(careerBId).orElseThrow().setSourceWorldName("Criado por A");
    });
    assertThat(ok(call(mvc, get("/api/v1/careers/" + careerBId), b.bearer(), null))).containsEntry("sourceWorldId", created.toString());
    long careerBPlayers = players.countByWorldId(careerBWorld);

    deletion.erase(users.findById(a.user().getId()).orElseThrow());

    assertThat(worlds.findById(created)).as("mundo criado no editor").isEmpty();
    assertThat(worlds.findById(cloned)).as("mundo clonado no editor").isEmpty();
    assertThat(players.countByWorldId(created) + players.countByWorldId(cloned)).isZero();
    assertThat(worlds.findById(careerAWorld)).as("o mundo SAVE da carreira do titular sai com ela").isEmpty();
    assertThat(worlds.findByOwnerUserIdAndKindOrderByCreatedAtAsc(a.user().getId(), WorldKind.TEMPLATE)).isEmpty();
    assertThat(worlds.findById(defaultWorld)).as("o mundo padrão nunca é apagado").isPresent();
    assertThat(players.countByWorldId(defaultWorld)).isEqualTo(32 * 69);

    // a carreira de B continua jogável, com o próprio mundo SAVE
    Map<String, Object> after = ok(call(mvc, get("/api/v1/careers/" + careerBId), b.bearer(), null));
    assertThat(after).doesNotContainKey("sourceWorldId").containsEntry("sourceWorldName", "Criado por A").containsEntry("worldId", careerBWorld.toString());
    assertThat(players.countByWorldId(careerBWorld)).isEqualTo(careerBPlayers);
    Map<String, Object> roster = ok(call(mvc, get("/api/v1/careers/" + careerBId + "/roster"), b.bearer(), null));
    assertThat((Map<String, Object>) roster.get("counts")).containsEntry("roster", 53);
    ok(call(mvc, get("/api/v1/careers/" + careerBId + "/dashboard"), b.bearer(), null));
  }

  private TransactionTemplate tx() { return new TransactionTemplate(txManager); }
}
