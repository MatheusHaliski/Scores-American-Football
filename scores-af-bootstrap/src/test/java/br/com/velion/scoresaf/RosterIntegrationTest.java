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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.web.security.TokenService;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RF4 / RF56 de ponta a ponta: elenco por unidade com overall, macros e nível; limites de {@code LeagueRules} (53 no elenco, 16 no
 * practice squad); lista de lesionados (lesão mínima, semanas mínimas, recuperado, retorno contado); dispensa para waivers com o dead
 * money calculado pelo contrato do bloco 4; carreira arquivada é só leitura.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RosterIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;
  @Autowired TokenService tokens;
  @Autowired PlayerRepository players;
  @Autowired CareerSaveRepository saves;
  @Autowired PlatformTransactionManager txManager;

  UUID defaultWorld;
  String auth;
  String base;

  @BeforeEach
  void career() throws Exception {
    defaultWorld = seeder.seedIfMissing().getId();
    auth = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    Map<String, Object> c = create(mvc, auth, body("Elenco " + UUID.randomUUID().toString().substring(0, 8), null, club(mvc, auth, defaultWorld, "PBR"), 21L));
    base = "/api/v1/careers/" + id(c);
  }

  private TransactionTemplate tx() { return new TransactionTemplate(txManager); }

  private Map<String, Object> roster() throws Exception { return ok(call(mvc, get(base + "/roster"), auth, null)); }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> active(Map<String, Object> roster) {
    List<Map<String, Object>> out = new ArrayList<>();
    ((Map<String, List<Map<String, Object>>>) roster.get("units")).values().forEach(out::addAll);
    return out;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> practiceSquad(Map<String, Object> roster) { return (List<Map<String, Object>>) roster.get("practiceSquad"); }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> counts(Map<String, Object> roster) { return (Map<String, Object>) roster.get("counts"); }

  private MvcResult move(String playerId, String status) throws Exception {
    return call(mvc, patch(base + "/players/" + playerId + "/roster-status"), auth, Map.of("status", status));
  }

  private static String pid(Map<String, Object> card) { return (String) card.get("id"); }

  @Test
  @SuppressWarnings("unchecked")
  void elenco_por_unidade_com_overall_macros_e_contadores() throws Exception {
    Map<String, Object> r = roster();
    assertThat(counts(r)).containsEntry("roster", 53).containsEntry("active", 53).containsEntry("inactive", 0).containsEntry("practiceSquad", 16)
        .containsEntry("injuredReserve", 0);
    assertThat((Map<String, Object>) r.get("rules")).containsEntry("rosterMax", 53).containsEntry("gameDayActives", 46).containsEntry("practiceSquadMax", 16)
        .containsEntry("irMinWeeks", 4);
    Map<String, List<Map<String, Object>>> units = (Map<String, List<Map<String, Object>>>) r.get("units");
    assertThat(units).containsOnlyKeys("OFFENSE", "DEFENSE", "SPECIAL_TEAMS");
    assertThat(units.get("SPECIAL_TEAMS")).extracting(c -> c.get("position")).containsExactlyInAnyOrder("K", "P", "LS");
    assertThat(active(r)).hasSize(53).allSatisfy(c -> {
      assertThat(c).containsKeys("overall", "tier", "macros", "capHit", "depthChart");
      assertThat(((Map<String, Object>) c.get("macros"))).isNotEmpty();
    });
    Map<String, Object> qb = units.get("OFFENSE").stream().filter(c -> "QB".equals(c.get("position"))).findFirst().orElseThrow();
    assertThat(((Map<String, Object>) qb.get("macros")).keySet()).containsExactly("PRE", "BRA", "LEI", "MOB", "CAL", "LID");
    assertThat(practiceSquad(r)).hasSize(16);
    Map<String, Object> cap = (Map<String, Object>) r.get("cap");
    assertThat(((Number) cap.get("capSpace")).longValue()).isGreaterThanOrEqualTo(0);
    Map<String, Object> detail = ok(call(mvc, get(base + "/players/" + pid(qb)), auth, null));
    assertThat(detail).containsEntry("ownPlayer", true).containsKeys("attributes", "overallByPosition", "contract", "deadMoneyIfReleased", "history");
  }

  @Test
  @SuppressWarnings("unchecked")
  void limites_de_53_e_16_e_inativo_sai_do_depth_chart() throws Exception {
    Map<String, Object> r = roster();
    Map<String, Object> young = active(r).stream().filter(c -> ((Number) c.get("experienceYears")).intValue() <= 3)
        .filter(c -> !List.of("QB", "K", "P", "LS").contains(c.get("position"))).findFirst().orElseThrow();
    MvcResult full = move(pid(young), "PRACTICE_SQUAD");
    assertProblem(full, 422, "PRACTICE_SQUAD_FULL");
    assertThat((String) map(full).get("message")).contains("16");
    Map<String, Object> ps = practiceSquad(r).get(0);
    assertProblem(move(pid(ps), "ACTIVE"), 422, "ROSTER_FULL");
    // abre vaga no practice squad dispensando um jogador dele
    ok(call(mvc, post(base + "/players/" + pid(practiceSquad(r).get(1)) + "/release"), auth, null));
    Map<String, Object> moved = ok(move(pid(young), "PRACTICE_SQUAD"));
    assertThat((Map<String, Object>) moved.get("counts")).containsEntry("roster", 52).containsEntry("practiceSquad", 16);
    assertThat((Map<String, Object>) moved.get("player")).containsEntry("rosterStatus", "PRACTICE_SQUAD").containsEntry("depthChart", List.of());
    assertThat(chartPlayers()).doesNotContain(pid(young));
    // agora cabe nos 53
    ok(move(pid(ps), "ACTIVE"));
    assertThat(counts(roster())).containsEntry("roster", 53).containsEntry("practiceSquad", 15);
    // inativo continua nos 53, mas sai do depth chart
    String starter = chartPlayers().get(0);
    Map<String, Object> inactive = ok(move(starter, "INACTIVE"));
    assertThat((Map<String, Object>) inactive.get("counts")).containsEntry("roster", 53).containsEntry("inactive", 1);
    assertThat(chartPlayers()).doesNotContain(starter);
    assertProblem(move(starter, "WAIVED"), 422, "ROSTER_MOVE_NOT_ALLOWED");
    assertProblem(move(starter, "BANCO"), 400, "INVALID_ROSTER_STATUS");
  }

  @SuppressWarnings("unchecked")
  private List<String> chartPlayers() throws Exception {
    Map<String, Object> chart = ok(call(mvc, get(base + "/depth-chart"), auth, null));
    List<String> out = new ArrayList<>();
    for (Map<String, Object> u : (List<Map<String, Object>>) chart.get("units")) {
      for (Map<String, Object> s : (List<Map<String, Object>>) u.get("slots")) {
        for (Map<String, Object> e : (List<Map<String, Object>>) s.get("players")) out.add((String) e.get("playerId"));
      }
    }
    return out;
  }

  private void setInjury(String playerId, int weeks) {
    tx().executeWithoutResult(st -> { Player p = players.findById(UUID.fromString(playerId)).orElseThrow(); p.setInjuryWeeksOut(weeks); players.save(p); });
  }

  private void setWeek(int week) {
    UUID careerId = UUID.fromString(base.substring(base.lastIndexOf('/') + 1));
    tx().executeWithoutResult(st -> { CareerSave s = saves.findById(careerId).orElseThrow(); s.setCurrentWeek(week); saves.save(s); });
  }

  @Test
  @SuppressWarnings("unchecked")
  void lista_de_lesionados_exige_lesao_semanas_minimas_e_recuperacao() throws Exception {
    Map<String, Object> r = roster();
    Map<String, Object> e = active(r).stream().filter(c -> "WR".equals(c.get("position"))).findFirst().orElseThrow();
    assertProblem(move(pid(e), "IR"), 422, "IR_NOT_ALLOWED");
    setInjury(pid(e), 6);
    Map<String, Object> onIr = ok(move(pid(e), "IR"));
    assertThat((Map<String, Object>) onIr.get("counts")).containsEntry("roster", 52).containsEntry("injuredReserve", 1);
    Map<String, Object> detail = ok(call(mvc, get(base + "/players/" + pid(e)), auth, null));
    assertThat(detail).containsEntry("irPlacedWeek", 1).containsEntry("irEarliestReturnWeek", 5);
    Map<String, Object> dash = ok(call(mvc, get(base + "/dashboard"), auth, null));
    assertThat((List<Map<String, Object>>) dash.get("injuries")).anySatisfy(i -> assertThat(i).containsEntry("playerId", pid(e)).containsEntry("weeksOut", 6));
    assertProblem(move(pid(e), "ACTIVE"), 422, "IR_RETURN_TOO_EARLY");
    assertProblem(move(pid(e), "PRACTICE_SQUAD"), 422, "ROSTER_MOVE_NOT_ALLOWED");
    setWeek(5);
    setInjury(pid(e), 2);
    assertProblem(move(pid(e), "ACTIVE"), 422, "IR_STILL_INJURED");
    setInjury(pid(e), 0);
    Map<String, Object> back = ok(move(pid(e), "ACTIVE"));
    assertThat((Map<String, Object>) back.get("counts")).containsEntry("roster", 53).containsEntry("injuredReserve", 0).containsEntry("irReturnsUsed", 1);
    assertThat((List<Map<String, Object>>) ok(call(mvc, get(base + "/players/" + pid(e)), auth, null)).get("history")).extracting(h -> h.get("toStatus"))
        .containsExactly("IR", "ACTIVE");
  }

  @Test
  @SuppressWarnings("unchecked")
  void dispensa_vai_a_waivers_com_dead_money_do_contrato() throws Exception {
    Map<String, Object> before = roster();
    UUID clubId = UUID.fromString((String) before.get("clubId"));
    Player target = tx().execute(st -> players.findByClubIdOrderByOverallDesc(clubId).stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE)
        .max(Comparator.comparingLong(p -> p.getContract().getSigningBonus() + p.getContract().getGuaranteed())).orElseThrow());
    long expectedDead = target.getContract().deadMoneyIfCut(target.getContract().yearIndexOf(2026), 5);
    long hit = target.getContract().capHit(target.getContract().yearIndexOf(2026), 5);
    assertThat(expectedDead).as("jogador com bônus ou garantia: o corte gera dead money").isPositive();
    Map<String, Object> capBefore = (Map<String, Object>) before.get("cap");

    Map<String, Object> res = ok(call(mvc, post(base + "/players/" + target.getId() + "/release"), auth, null));
    assertThat(res).containsEntry("rosterStatus", "WAIVED");
    assertThat(((Number) res.get("deadMoney")).longValue()).isEqualTo(expectedDead);
    assertThat(((Number) res.get("capHitRemoved")).longValue()).isEqualTo(hit);
    assertThat((Map<String, Object>) res.get("counts")).containsEntry("roster", 52);
    Map<String, Object> capAfter = (Map<String, Object>) res.get("cap");
    assertThat(((Number) capAfter.get("deadMoney")).longValue()).isEqualTo(expectedDead);
    assertThat(((Number) capAfter.get("capUsed")).longValue()).isEqualTo(((Number) capBefore.get("capUsed")).longValue() - hit + expectedDead);

    Player waived = tx().execute(st -> players.findById(target.getId()).orElseThrow());
    assertThat(waived.getRosterStatus()).isEqualTo(RosterStatus.WAIVED);
    assertThat(waived.getClub()).isNull();
    Map<String, Object> detail = ok(call(mvc, get(base + "/players/" + target.getId()), auth, null));
    assertThat(detail).containsEntry("ownPlayer", false);
    assertThat((List<Map<String, Object>>) detail.get("history")).singleElement().satisfies(h -> {
      assertThat(h).containsEntry("kind", "RELEASE").containsEntry("toStatus", "WAIVED");
      assertThat(((Number) h.get("deadMoney")).longValue()).isEqualTo(expectedDead);
    });
    assertThat(chartPlayers()).doesNotContain(target.getId().toString());
    assertProblem(call(mvc, post(base + "/players/" + target.getId() + "/release"), auth, null), 422, "PLAYER_NOT_IN_ROSTER");

    // carreira arquivada é só leitura
    ok(call(mvc, patch(base), auth, Map.of("status", "ARCHIVED")));
    String other = pid(active(roster()).get(0));
    assertProblem(call(mvc, post(base + "/players/" + other + "/release"), auth, null), 422, "CAREER_ARCHIVED");
    assertProblem(move(other, "INACTIVE"), 422, "CAREER_ARCHIVED");
  }
}
