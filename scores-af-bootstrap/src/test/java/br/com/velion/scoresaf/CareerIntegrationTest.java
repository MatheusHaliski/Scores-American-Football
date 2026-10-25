package br.com.velion.scoresaf;

import static br.com.velion.scoresaf.CareerFixtures.assertProblem;
import static br.com.velion.scoresaf.CareerFixtures.body;
import static br.com.velion.scoresaf.CareerFixtures.call;
import static br.com.velion.scoresaf.CareerFixtures.club;
import static br.com.velion.scoresaf.CareerFixtures.create;
import static br.com.velion.scoresaf.CareerFixtures.id;
import static br.com.velion.scoresaf.CareerFixtures.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.port.out.persistence.AuditLogRepository;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WorldRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.domain.account.AuditLog;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import br.com.velion.scoresaf.web.security.TokenService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RF11 / RF34 / RF40 / RF21 de ponta a ponta (HTTP → caso de uso → H2): nova carreira com mundo próprio (RNF1), calendário de 17 jogos
 * + bye gerado pela semente, central da franquia, slots (5 ACTIVE), arquivar/reativar, renomear com versão otimista, excluir com
 * confirmação e o controle de acesso do dono (403 auditado para outro usuário, 404 para id inexistente, 404 para jogador de outra
 * carreira).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareerIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;
  @Autowired TokenService tokens;
  @Autowired AuditLogRepository auditLog;
  @Autowired WorldRepository worlds;
  @Autowired CareerSaveRepository saves;
  @Autowired ClubRepository clubs;
  @Autowired PlayerRepository players;
  @Autowired PlatformTransactionManager txManager;

  UUID defaultWorld;

  @BeforeEach
  void seed() { defaultWorld = seeder.seedIfMissing().getId(); }

  private String bearer() { return TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER); }

  private TransactionTemplate tx() { return new TransactionTemplate(txManager); }

  private Map<String, Object> career(String auth, String name, String shortName, Long seed) throws Exception {
    return create(mvc, auth, body(name, null, club(mvc, auth, defaultWorld, shortName), seed));
  }

  @Test
  void nova_carreira_copia_o_mundo_e_gera_calendario_de_17_jogos_e_1_bye() throws Exception {
    String auth = bearer();
    Map<String, Object> c = career(auth, "Dinastia PAV", "PAV", 42L);
    assertThat(c).containsEntry("status", "ACTIVE").containsEntry("slot", 1).containsEntry("clubShortName", "PAV").containsEntry("coachStyle", "OFFENSIVE")
        .containsEntry("seasonYear", 2026).containsEntry("seasonPhase", "REGULAR").containsEntry("currentWeek", 1).containsEntry("seed", 42)
        .containsEntry("employmentStatus", "EMPLOYED").containsEntry("regularSeasonWeeks", 18);
    UUID careerWorld = UUID.fromString((String) c.get("worldId"));
    assertThat(careerWorld).isNotEqualTo(defaultWorld);
    assertThat(c.get("sourceWorldId")).isEqualTo(defaultWorld.toString());
    World w = tx().execute(st -> worlds.findById(careerWorld).orElseThrow());
    assertThat(w.getKind()).isEqualTo(WorldKind.SAVE);
    Club userClub = tx().execute(st -> clubs.findById(UUID.fromString((String) c.get("clubId"))).orElseThrow());
    assertThat(userClub.isUserControlled()).isTrue();
    UUID templatePav = club(mvc, auth, defaultWorld, "PAV");
    Boolean templateControlled = tx().execute(st -> clubs.findById(templatePav).orElseThrow().isUserControlled());
    assertThat(templateControlled).as("o mundo padrão não muda").isFalse();

    Map<String, Object> cal = ok(call(mvc, get("/api/v1/careers/" + id(c) + "/calendar"), auth, null));
    assertThat(cal).containsEntry("regularSeasonWeeks", 18).containsEntry("gamesPerTeam", 17).containsEntry("currentWeek", 1);
    @SuppressWarnings("unchecked") List<Map<String, Object>> weeks = (List<Map<String, Object>>) cal.get("weeks");
    assertThat(weeks).hasSize(18);
    String me = (String) c.get("clubId");
    int games = 0, byes = 0, divisional = 0, divisionalHome = 0, total = 0;
    Map<String, Integer> playedByClub = new HashMap<>();
    for (Map<String, Object> wk : weeks) {
      @SuppressWarnings("unchecked") List<Map<String, Object>> fixtures = (List<Map<String, Object>>) wk.get("fixtures");
      @SuppressWarnings("unchecked") List<String> bye = (List<String>) wk.get("byeClubIds");
      Set<String> seen = new HashSet<>();
      for (Map<String, Object> f : fixtures) {
        total++;
        for (String k : List.of((String) f.get("homeClubId"), (String) f.get("awayClubId"))) {
          assertThat(seen.add(k)).as("franquia joga duas vezes na semana " + wk.get("number")).isTrue();
          playedByClub.merge(k, 1, Integer::sum);
        }
        if (Boolean.TRUE.equals(f.get("userGame"))) {
          games++;
          if (Boolean.TRUE.equals(f.get("divisional"))) { divisional++; if (me.equals(f.get("homeClubId"))) divisionalHome++; }
        }
      }
      assertThat(seen.size() + bye.size()).isEqualTo(32);
      if (Boolean.TRUE.equals(wk.get("userBye"))) byes++;
      assertThat(fixtures.stream().filter(f -> "THU_NIGHT".equals(f.get("kickoffSlot"))).count()).isEqualTo(1);
      assertThat(fixtures.stream().filter(f -> "MON_NIGHT".equals(f.get("kickoffSlot"))).count()).isEqualTo(1);
    }
    assertThat(total).isEqualTo(272);
    assertThat(games).isEqualTo(17);
    assertThat(byes).isEqualTo(1);
    assertThat(divisional).isEqualTo(6);
    assertThat(divisionalHome).isEqualTo(3);
    assertThat(playedByClub.values()).allMatch(v -> v == 17);
    assertThat(weeks.get(0)).containsEntry("startsOn", "2026-09-10").containsEntry("status", "CURRENT");
    Map<String, Object> week1 = ok(call(mvc, get("/api/v1/careers/" + id(c) + "/calendar?week=1"), auth, null));
    assertThat((List<?>) week1.get("weeks")).hasSize(1);
    assertProblem(call(mvc, get("/api/v1/careers/" + id(c) + "/calendar?week=19"), auth, null), 400, "INVALID_WEEK");
  }

  @Test
  void mesma_semente_gera_o_mesmo_calendario_em_mundos_clonados_diferentes() throws Exception {
    String a = bearer(), b = bearer();
    List<String> calA = calendarByShortName(a, career(a, "Semente A", "SAL", 77L));
    List<String> calB = calendarByShortName(b, career(b, "Semente B", "SAL", 77L));
    assertThat(calB).isEqualTo(calA);
    List<String> calC = calendarByShortName(b, career(b, "Semente C", "SAL", 78L));
    assertThat(calC).isNotEqualTo(calA);
  }

  private List<String> calendarByShortName(String auth, Map<String, Object> c) throws Exception {
    Map<String, Object> cal = ok(call(mvc, get("/api/v1/careers/" + id(c) + "/calendar"), auth, null));
    Map<String, String> shortName = new HashMap<>();
    @SuppressWarnings("unchecked") List<Map<String, Object>> clubList = (List<Map<String, Object>>) cal.get("clubs");
    clubList.forEach(x -> shortName.put((String) x.get("id"), (String) x.get("shortName")));
    List<String> out = new ArrayList<>();
    @SuppressWarnings("unchecked") List<Map<String, Object>> weeks = (List<Map<String, Object>>) cal.get("weeks");
    for (Map<String, Object> wk : weeks) {
      @SuppressWarnings("unchecked") List<Map<String, Object>> fixtures = (List<Map<String, Object>>) wk.get("fixtures");
      for (Map<String, Object> f : fixtures) {
        out.add(wk.get("number") + " " + f.get("date") + " " + f.get("kickoffSlot") + " " + shortName.get(f.get("homeClubId")) + "x" + shortName.get(f.get("awayClubId")));
      }
    }
    return out;
  }

  @Test
  void outro_usuario_recebe_403_auditado_e_id_inexistente_404() throws Exception {
    String owner = bearer(), intruder = bearer();
    Map<String, Object> c = career(owner, "Minha Carreira", "BSE", 5L);
    String base = "/api/v1/careers/" + id(c);
    for (String path : List.of("", "/dashboard", "/calendar", "/roster", "/depth-chart", "/game-plan")) {
      assertProblem(call(mvc, get(base + path), intruder, null), 403, "FORBIDDEN");
    }
    assertProblem(call(mvc, patch(base), intruder, Map.of("name", "Roubada")), 403, "FORBIDDEN");
    assertProblem(call(mvc, delete(base + "?confirm=Minha Carreira"), intruder, null), 403, "FORBIDDEN");
    assertProblem(call(mvc, put(base + "/depth-chart"), intruder, Map.of("version", 0, "slots", List.of())), 403, "FORBIDDEN");
    String anyPlayer = firstPlayer(owner, c);
    assertProblem(call(mvc, post(base + "/players/" + anyPlayer + "/release"), intruder, null), 403, "FORBIDDEN");
    List<AuditLog> denied = auditLog.findByResourceAndResourceIdOrderByTimestampDesc("CareerSave", id(c), PageRequest.of(0, 50)).getContent();
    assertThat(denied).anySatisfy(a -> {
      assertThat(a.getAction()).isEqualTo(AuditActions.ACCESS_DENIED);
      assertThat(a.getResult()).isEqualTo(AuditActions.RESULT_DENIED);
    });
    assertProblem(call(mvc, get("/api/v1/careers/" + UUID.randomUUID()), owner, null), 404, "NOT_FOUND");
    // jogador de outra carreira (outro mundo) não é visível pela minha carreira: 404, sem revelar
    Map<String, Object> other = career(intruder, "Outra Carreira", "BSE", 5L);
    assertProblem(call(mvc, get(base + "/players/" + firstPlayer(intruder, other)), owner, null), 404, "NOT_FOUND");
    // a lista só traz as carreiras do próprio usuário
    Map<String, Object> list = ok(call(mvc, get("/api/v1/careers"), intruder, null));
    @SuppressWarnings("unchecked") List<Map<String, Object>> careers = (List<Map<String, Object>>) list.get("careers");
    assertThat(careers).extracting(x -> x.get("id")).containsExactly(id(other));
  }

  private String firstPlayer(String auth, Map<String, Object> c) throws Exception {
    Map<String, Object> roster = ok(call(mvc, get("/api/v1/careers/" + id(c) + "/roster"), auth, null));
    @SuppressWarnings("unchecked") Map<String, List<Map<String, Object>>> units = (Map<String, List<Map<String, Object>>>) roster.get("units");
    return (String) units.get("OFFENSE").get(0).get("id");
  }

  @Test
  void cinco_slots_ativos_arquivar_libera_e_reativar_respeita_o_limite() throws Exception {
    String auth = bearer();
    UUID club = club(mvc, auth, defaultWorld, "VAU");
    List<String> ids = new ArrayList<>();
    for (int i = 1; i <= 5; i++) ids.add(id(create(mvc, auth, body("Slot " + i, null, club, (long) i))));
    assertProblem(call(mvc, post("/api/v1/careers"), auth, body("Slot 6", null, club, 6L)), 422, "CAREER_SLOTS_FULL");
    Map<String, Object> archived = ok(call(mvc, patch("/api/v1/careers/" + ids.get(2)), auth, Map.of("status", "ARCHIVED")));
    assertThat(archived).containsEntry("status", "ARCHIVED").doesNotContainKey("slot");
    Map<String, Object> list = ok(call(mvc, get("/api/v1/careers"), auth, null));
    assertThat(list).containsEntry("activeCount", 4).containsEntry("freeSlots", List.of(3));
    assertProblem(call(mvc, patch("/api/v1/careers/" + ids.get(2)), auth, Map.of("name", "Mudou")), 422, "CAREER_ARCHIVED");
    Map<String, Object> sixth = create(mvc, auth, body("Slot 6", null, club, 6L));
    assertThat(sixth).containsEntry("slot", 3);
    assertProblem(call(mvc, patch("/api/v1/careers/" + ids.get(2)), auth, Map.of("status", "ACTIVE")), 422, "CAREER_SLOTS_FULL");
    assertProblem(call(mvc, post("/api/v1/careers"), auth, body("Slot 1", null, club, 1L)), 409, "CAREER_NAME_TAKEN");
  }

  @Test
  void renomear_com_versao_otimista_e_excluir_com_confirmacao() throws Exception {
    String auth = bearer();
    Map<String, Object> c = career(auth, "Antes", "PLU", 9L);
    String base = "/api/v1/careers/" + id(c);
    long v = ((Number) c.get("version")).longValue();
    Map<String, Object> renamed = ok(call(mvc, patch(base), auth, Map.of("name", "Depois", "difficulty", "HARD", "version", v)));
    assertThat(renamed).containsEntry("name", "Depois").containsEntry("difficulty", "HARD");
    assertThat(((Number) renamed.get("version")).longValue()).isGreaterThan(v);
    MvcResult stale = call(mvc, patch(base), auth, Map.of("name", "Outra", "version", v));
    assertProblem(stale, 409, "CAREER_VERSION_CONFLICT");
    assertProblem(call(mvc, patch(base), auth, Map.of("difficulty", "IMPOSSIVEL")), 422, "INVALID_DIFFICULTY");
    assertProblem(call(mvc, patch(base), auth, Map.of("name", "x")), 422, "CAREER_NAME_INVALID");

    UUID careerWorld = UUID.fromString((String) c.get("worldId"));
    assertProblem(call(mvc, delete(base + "?confirm=Antes"), auth, null), 422, "CAREER_CONFIRMATION_REQUIRED");
    assertThat(call(mvc, delete(base + "?confirm=Depois"), auth, null).getResponse().getStatus()).isEqualTo(204);
    assertProblem(call(mvc, get(base), auth, null), 404, "NOT_FOUND");
    assertThat(worlds.findById(careerWorld)).as("o mundo da carreira vai junto").isEmpty();
    assertThat(worlds.findById(defaultWorld)).isPresent();
  }

  @Test
  void validacoes_da_criacao() throws Exception {
    String auth = bearer(), other = bearer();
    UUID pav = club(mvc, auth, defaultWorld, "PAV");
    Map<String, Object> bad = body("Estilo Ruim", null, pav, 1L);
    bad.put("coachStyle", "TACTICIAN");
    assertProblem(call(mvc, post("/api/v1/careers"), auth, bad), 422, "INVALID_COACH_STYLE");
    Map<String, Object> nat = body("Nacao Ruim", null, pav, 1L);
    nat.put("coachNationality", "B1");
    assertProblem(call(mvc, post("/api/v1/careers"), auth, nat), 422, "COACH_NATIONALITY_INVALID");
    assertProblem(call(mvc, post("/api/v1/careers"), auth, body("ab", null, pav, 1L)), 422, "CAREER_NAME_INVALID");
    // mundo modelo de outro usuário: 403 auditado
    Map<String, Object> cloned = ok(call(mvc, post("/api/v1/worlds/" + defaultWorld + "/clone"), other, Map.of("name", "Mundo dele")));
    UUID otherWorld = UUID.fromString((String) cloned.get("id"));
    assertProblem(call(mvc, post("/api/v1/careers"), auth, body("Mundo Alheio", otherWorld, club(mvc, other, otherWorld, "PAV"), 1L)), 403, "FORBIDDEN");
    // franquia de outro mundo (não pertence à liga escolhida)
    Map<String, Object> wrong = body("Liga Errada", otherWorld, pav, 1L);
    assertProblem(call(mvc, post("/api/v1/careers"), other, wrong), 422, "CLUB_NOT_IN_LEAGUE");
    assertProblem(call(mvc, post("/api/v1/careers"), auth, Map.of("name", "Sem Franquia")), 400, "VALIDATION");
  }

  /**
   * RNF1 CA4/CA5 (como no basquete): a carreira nasce do mundo editado do usuário, com a edição; editar o mundo depois não chega à
   * carreira; o que muda na carreira não volta ao mundo de origem.
   */
  @Test
  void carreira_nasce_do_mundo_editado_e_fica_isolada_dele() throws Exception {
    String auth = bearer();
    Map<String, Object> cloned = ok(call(mvc, post("/api/v1/worlds/" + defaultWorld + "/clone"), auth, Map.of("name", "Meu database")));
    UUID myWorld = UUID.fromString((String) cloned.get("id"));
    UUID cco = club(mvc, auth, myWorld, "CCE");
    List<Map<String, Object>> squad = CareerFixtures.list(call(mvc, get("/api/v1/clubs/" + cco + "/players"), auth, null));
    String edited = (String) squad.get(0).get("id");
    ok(call(mvc, put("/api/v1/players/" + edited), auth, Map.of("name", "Editado Antes")));
    Map<String, Object> c = create(mvc, auth, body("Do Meu Mundo", myWorld, cco, 3L));
    assertThat(c.get("sourceWorldId")).isEqualTo(myWorld.toString());
    assertThat(c).containsEntry("sourceWorldName", "Meu database");
    UUID careerWorld = UUID.fromString((String) c.get("worldId"));
    assertThat(names(careerWorld)).contains("Editado Antes");
    ok(call(mvc, put("/api/v1/players/" + squad.get(1).get("id")), auth, Map.of("name", "Editado Depois")));
    assertThat(names(careerWorld)).doesNotContain("Editado Depois");
    // dispensa na carreira não mexe no mundo de origem
    String careerPlayer = firstPlayer(auth, c);
    ok(call(mvc, post("/api/v1/careers/" + id(c) + "/players/" + careerPlayer + "/release"), auth, null));
    Integer sourceSquad = tx().execute(st -> players.findByClubIdOrderByOverallDesc(cco).size());
    assertThat(sourceSquad).isEqualTo(69);
    // o editor não edita o mundo da carreira
    assertThat(call(mvc, put("/api/v1/players/" + careerPlayer), auth, Map.of("name", "x")).getResponse().getStatus()).isEqualTo(422);
  }

  private List<String> names(UUID worldId) {
    return tx().execute(st -> players.findByWorldId(worldId).stream().map(Player::getName).toList());
  }

  /** RNF1 CA1: falha no meio da criação (depois de copiar o mundo) não deixa carreira nem mundo de carreira. */
  @Test
  void falha_no_meio_da_criacao_nao_deixa_carreira_parcial() throws Exception {
    TestSessions.Session session = TestSessions.open(users, sessions, tokens, UserRole.PLAYER);
    String auth = session.bearer();
    Map<String, Object> cloned = ok(call(mvc, post("/api/v1/worlds/" + defaultWorld + "/clone"), auth, Map.of("name", "Quebrado")));
    UUID myWorld = UUID.fromString((String) cloned.get("id"));
    UUID target = club(mvc, auth, myWorld, "SAL");
    UUID loose = club(mvc, auth, myWorld, "VAU");
    // franquia fora de divisão: a liga perde a estrutura e o calendário (gerado depois da cópia do mundo) recusa
    tx().executeWithoutResult(st -> { Club c = clubs.findById(loose).orElseThrow(); c.setDivision(null); c.setConference(null); clubs.save(c); });
    long saveWorldsBefore = tx().execute(st -> worlds.findAll(PageRequest.of(0, 1000)).getContent().stream().filter(w -> w.getKind() == WorldKind.SAVE).count());
    UUID userId = session.user().getId();
    assertProblem(call(mvc, post("/api/v1/careers"), auth, body("Quebra", myWorld, target, 1L)), 422, "LEAGUE_STRUCTURE_INVALID");
    long saveWorldsAfter = tx().execute(st -> worlds.findAll(PageRequest.of(0, 1000)).getContent().stream().filter(w -> w.getKind() == WorldKind.SAVE).count());
    assertThat(saveWorldsAfter).isEqualTo(saveWorldsBefore);
    assertThat(saves.findByUserIdOrderByCreatedAtAsc(userId)).isEmpty();
  }

  @Test
  void central_da_franquia_mostra_proximo_jogo_divisao_destaques_e_teto() throws Exception {
    String auth = bearer();
    Map<String, Object> c = career(auth, "Central", "MAZ", 11L);
    Map<String, Object> d = ok(call(mvc, get("/api/v1/careers/" + id(c) + "/dashboard"), auth, null));
    assertThat(d).containsKeys("career", "club", "coach", "nextGame", "byeWeek", "record", "division", "upcomingDivisionGames", "rosterHighlights",
        "injuries", "cap", "depthChart", "live", "morale", "analysis", "news", "playoffs", "inbox", "unreadInbox", "scoresPoints");
    assertThat(d.get("live")).isNull();
    // bloco 6c (D-106): moral do elenco e o usuário nos playoffs (projeção); sem relatório do analista nem semana encerrada ainda
    assertThat(d.get("morale")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP).containsKeys("average", "label", "trend", "low", "tradeRequests");
    assertThat(d.get("analysis")).isNull();
    assertThat(d.get("news")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
    assertThat(d.get("playoffs")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP).containsKey("status");
    assertThat(d.get("inbox")).isNull();
    assertThat(d.get("scoresPoints")).isNull();
    @SuppressWarnings("unchecked") Map<String, Object> next = (Map<String, Object>) d.get("nextGame");
    @SuppressWarnings("unchecked") Map<String, Object> fixture = (Map<String, Object>) next.get("fixture");
    assertThat(fixture).containsEntry("userGame", true);
    @SuppressWarnings("unchecked") List<Map<String, Object>> division = (List<Map<String, Object>>) d.get("division");
    assertThat(division).hasSize(4).anySatisfy(row -> assertThat(row).containsEntry("userClub", true));
    assertThat(d.get("divisionRank")).isNotNull();
    @SuppressWarnings("unchecked") List<Map<String, Object>> highlights = (List<Map<String, Object>>) d.get("rosterHighlights");
    assertThat(highlights).hasSize(5);
    @SuppressWarnings("unchecked") Map<String, Object> cap = (Map<String, Object>) d.get("cap");
    assertThat(((Number) cap.get("salaryCap")).longValue()).isPositive();
    assertThat(((Number) cap.get("capSpace")).longValue()).isEqualTo(((Number) cap.get("salaryCap")).longValue() - ((Number) cap.get("capUsed")).longValue());
    @SuppressWarnings("unchecked") Map<String, Object> chart = (Map<String, Object>) d.get("depthChart");
    assertThat(chart).containsEntry("valid", true);
  }

  /**
   * Q-25 (confirmado pelo PO em 25/10/2026): o DELETE da carreira é definitivo e exige a confirmação pelo nome exato; sem ela ou com
   * outro nome, 422 e nada muda; com ela, o save some do banco (não vira arquivada) junto com o mundo SAVE, e não há como voltar.
   */
  @Test
  void delete_da_carreira_e_definitivo_com_confirmacao_pelo_nome() throws Exception {
    String auth = bearer();
    Map<String, Object> c = career(auth, "Definitiva", "PAV", 31L);
    String base = "/api/v1/careers/" + id(c);
    UUID careerId = UUID.fromString(id(c));
    UUID careerWorld = UUID.fromString((String) c.get("worldId"));
    assertProblem(call(mvc, delete(base), auth, null), 422, "CAREER_CONFIRMATION_REQUIRED");
    assertProblem(call(mvc, delete(base + "?confirm=definitiva"), auth, null), 422, "CAREER_CONFIRMATION_REQUIRED");
    assertThat(saves.findById(careerId)).isPresent();
    assertThat(call(mvc, delete(base + "?confirm=Definitiva"), auth, null).getResponse().getStatus()).isEqualTo(204);
    assertThat(saves.findById(careerId)).as("apagada, não arquivada").isEmpty();
    assertThat(worlds.findById(careerWorld)).isEmpty();
    assertProblem(call(mvc, patch(base), auth, Map.of("status", "ACTIVE")), 404, "NOT_FOUND");
    assertThat(call(mvc, delete(base + "?confirm=Definitiva"), auth, null).getResponse().getStatus()).isEqualTo(404);
  }

  /**
   * Q-26 (D-82): o PO não quer bloquear — o dono clona o mundo SAVE da carreira como mundo modelo (imersão: leva a temporada para o
   * editor). O clone traz o estado da carreira (dispensado sem franquia; quem estava em waivers entra no practice squad), editar o
   * clone não chega à carreira (RNF1), dá para iniciar carreira nova nele, e outro usuário não clona o mundo da carreira alheia.
   */
  @Test
  @SuppressWarnings("unchecked")
  void dono_clona_o_mundo_da_carreira_como_mundo_modelo() throws Exception {
    String auth = bearer(), other = bearer();
    Map<String, Object> c = career(auth, "Imersao", "PAV", 12L);
    String base = "/api/v1/careers/" + id(c);
    UUID careerWorld = UUID.fromString((String) c.get("worldId"));
    Map<String, Object> roster = ok(call(mvc, get(base + "/roster"), auth, null));
    Map<String, Object> released = ((List<Map<String, Object>>) roster.get("practiceSquad")).get(0);
    ok(call(mvc, post(base + "/players/" + released.get("id") + "/release"), auth, null));
    Map<String, Object> young = ((Map<String, List<Map<String, Object>>>) roster.get("units")).get("DEFENSE").stream()
        .filter(p -> ((Number) p.get("experienceYears")).intValue() <= 3).findFirst().orElseThrow();
    ok(call(mvc, patch(base + "/players/" + young.get("id") + "/roster-status"), auth, Map.of("status", "PRACTICE_SQUAD")));

    MvcResult r = call(mvc, post("/api/v1/worlds/" + careerWorld + "/clone"), auth, Map.of("name", "Minha temporada"));
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    Map<String, Object> clone = CareerFixtures.map(r);
    assertThat(clone).containsEntry("kind", "TEMPLATE").containsEntry("editable", true).containsEntry("sourceWorldId", careerWorld.toString());
    UUID tpl = UUID.fromString(id(clone));
    Player releasedCopy = copyIn(tpl, (String) released.get("id"));
    assertThat(releasedCopy.getRosterStatus().name()).isEqualTo("WAIVED");
    assertThat(releasedCopy.getClub()).isNull();
    assertThat(copyIn(tpl, (String) young.get("id")).getRosterStatus().name()).as("em waivers na carreira, practice squad no modelo").isEqualTo("PRACTICE_SQUAD");

    // editar o clone não chega à carreira; a carreira continua com o mundo dela
    UUID tplClub = club(mvc, auth, tpl, "PAV");
    String clonedPlayer = (String) CareerFixtures.list(call(mvc, get("/api/v1/clubs/" + tplClub + "/players"), auth, null)).get(0).get("id");
    ok(call(mvc, put("/api/v1/players/" + clonedPlayer), auth, Map.of("name", "Editado no Clone")));
    assertThat(names(careerWorld)).doesNotContain("Editado no Clone");
    assertThat(ok(call(mvc, get(base), auth, null))).containsEntry("worldId", careerWorld.toString());
    // carreira nova no mundo clonado
    create(mvc, auth, body("Do Clone", tpl, tplClub, 5L));
    // o mundo da carreira de outro usuário não é clonado
    assertProblem(call(mvc, post("/api/v1/worlds/" + careerWorld + "/clone"), other, null), 403, "FORBIDDEN");
  }

  /** A cópia, no mundo {@code worldId}, do jogador {@code playerId} (mesmo nome, nascimento, posição e número). */
  private Player copyIn(UUID worldId, String playerId) {
    return tx().execute(st -> {
      Player o = players.findById(UUID.fromString(playerId)).orElseThrow();
      return players.findByWorldId(worldId).stream().filter(p -> p.getName().equals(o.getName()) && p.getBirthDate().equals(o.getBirthDate())
          && p.getPosition() == o.getPosition() && p.getJerseyNumber() == o.getJerseyNumber()).findFirst().orElseThrow();
    });
  }
}
