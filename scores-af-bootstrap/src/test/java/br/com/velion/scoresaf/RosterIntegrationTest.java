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
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.web.security.TokenService;
import java.util.Locale;
import java.time.LocalDate;
import java.text.DecimalFormatSymbols;
import java.text.DecimalFormat;
import br.com.velion.scoresaf.domain.season.Week;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
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
 * money calculado pelo contrato do bloco 4; carreira arquivada é só leitura. Respostas do PO (25/10/2026): dos 53 para o practice
 * squad passa por waivers até a semana avançar (Q-23); regra do 1º de junho e dispensa recusada com {@code CAP_EXCEEDED} (Q-24); a IR
 * continua exigindo lesão (Q-22).
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

  @Autowired WeekRepository weeks;
  @Autowired RosterMoveRepository moveLog;

  UUID defaultWorld;
  String auth;
  String base;
  UUID careerId;
  UUID seasonId;

  @BeforeEach
  void career() throws Exception {
    defaultWorld = seeder.seedIfMissing().getId();
    auth = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    Map<String, Object> c = create(mvc, auth, body("Elenco " + UUID.randomUUID().toString().substring(0, 8), null, club(mvc, auth, defaultWorld, "PBR"), 21L));
    base = "/api/v1/careers/" + id(c);
    careerId = UUID.fromString(id(c));
    seasonId = UUID.fromString((String) c.get("seasonId"));
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
    // dos 53 para o practice squad passa por waivers (Q-23): fora dos 53 e do practice squad, com a vaga reservada
    Map<String, Object> moved = ok(move(pid(young), "PRACTICE_SQUAD"));
    assertThat((Map<String, Object>) moved.get("counts")).containsEntry("roster", 52).containsEntry("practiceSquad", 15).containsEntry("waivers", 1);
    assertThat((Map<String, Object>) moved.get("player")).containsEntry("rosterStatus", "PRACTICE_SQUAD_WAIVERS").containsEntry("depthChart", List.of());
    assertThat(chartPlayers()).doesNotContain(pid(young));
    // agora cabe nos 53
    ok(move(pid(ps), "ACTIVE"));
    assertThat(counts(roster())).containsEntry("roster", 53).containsEntry("practiceSquad", 14).containsEntry("waivers", 1);
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
    // a semana 1 começa em setembro: dispensa depois de 1º de junho, só a parcela do ano entra na temporada (Q-24)
    PlayerContract.DeadMoney split = target.getContract().deadMoneySplit(target.getContract().yearIndexOf(2026), 5, true);
    long expectedDead = split.currentSeason();
    long hit = target.getContract().capHit(target.getContract().yearIndexOf(2026), 5);
    assertThat(expectedDead).as("jogador com bônus ou garantia: o corte gera dead money").isPositive();
    Map<String, Object> capBefore = (Map<String, Object>) before.get("cap");

    Map<String, Object> res = ok(call(mvc, post(base + "/players/" + target.getId() + "/release"), auth, null));
    assertThat(res).containsEntry("rosterStatus", "WAIVED");
    assertThat(((Number) res.get("deadMoney")).longValue()).isEqualTo(expectedDead);
    assertThat(((Number) res.get("deadMoneyNextSeason")).longValue()).isEqualTo(split.nextSeason());
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

  // ------------------------------------------------------------------ respostas do PO (Q-23, Q-24)

  /** Data do jogo da carreira = quinta-feira da semana atual (a regra do 1º de junho olha para ela). */
  private void setGameDate(LocalDate date) {
    tx().executeWithoutResult(st -> {
      Week w = weeks.findBySeasonIdAndNumber(seasonId, 1).orElseThrow();
      w.setStartsOn(date);
      weeks.save(w);
    });
  }

  private void setSalaryCap(long cap) {
    tx().executeWithoutResult(st -> saves.findById(careerId).orElseThrow().getLeague().setSalaryCap(cap));
  }

  private UUID clubId() { return tx().execute(st -> saves.findById(careerId).orElseThrow().getClub().getId()); }

  private Player reload(UUID playerId) { return tx().execute(st -> players.findById(playerId).orElseThrow()); }

  /**
   * Q-23: o jogador movido dos 53 para o practice squad fica em waivers (estado próprio, fora dos 53 e do practice squad, com a vaga
   * reservada e o cap hit na folha) até a semana avançar; sem reivindicação (a IA só reivindica no bloco 7), entra no practice squad
   * da franquia com o mesmo contrato e o movimento WAIVER_CLEARED vai ao histórico.
   */
  @Test
  @SuppressWarnings("unchecked")
  void mover_para_o_practice_squad_passa_por_waivers_ate_a_semana_avancar() throws Exception {
    MatchFixtures.level(mvc, auth, "NONE");
    Map<String, Object> r = roster();
    List<Map<String, Object>> young = active(r).stream().filter(c -> ((Number) c.get("experienceYears")).intValue() <= 3)
        .filter(c -> !List.of("QB", "K", "P", "LS").contains(c.get("position"))).limit(2).toList();
    String exposed = pid(young.get(0));
    PlayerContract contractBefore = reload(UUID.fromString(exposed)).getContract();
    ok(call(mvc, post(base + "/players/" + pid(practiceSquad(r).get(0)) + "/release"), auth, null));
    long capUsedBefore = ((Number) ((Map<String, Object>) roster().get("cap")).get("capUsed")).longValue();

    Map<String, Object> moved = ok(move(exposed, "PRACTICE_SQUAD"));
    assertThat((Map<String, Object>) moved.get("player")).containsEntry("rosterStatus", "PRACTICE_SQUAD_WAIVERS");
    Map<String, Object> during = roster();
    assertThat(counts(during)).containsEntry("roster", 52).containsEntry("practiceSquad", 15).containsEntry("waivers", 1);
    assertThat((List<Map<String, Object>>) during.get("waivers")).extracting(c -> c.get("id")).containsExactly(exposed);
    assertThat(practiceSquad(during)).extracting(c -> c.get("id")).doesNotContain(exposed);
    assertThat(((Number) ((Map<String, Object>) during.get("cap")).get("capUsed")).longValue()).as("o cap hit continua na folha").isEqualTo(capUsedBefore);
    // a vaga do practice squad está reservada; em waivers ninguém volta aos 53 por movimento
    assertProblem(move(pid(young.get(1)), "PRACTICE_SQUAD"), 422, "PRACTICE_SQUAD_FULL");
    assertProblem(move(exposed, "ACTIVE"), 422, "ROSTER_MOVE_NOT_ALLOWED");
    assertProblem(move(exposed, "PRACTICE_SQUAD_WAIVERS"), 422, "ROSTER_MOVE_NOT_ALLOWED");

    // avanço da semana: a semana fecha no fim da partida do usuário
    String mid = (String) MatchFixtures.play(mvc, auth, careerId.toString()).get("matchId");
    MatchFixtures.finish(mvc, auth, mid, "FULL");
    Map<String, Object> after = roster();
    assertThat(after).containsEntry("currentWeek", 2);
    assertThat(counts(after)).containsEntry("practiceSquad", 16).containsEntry("waivers", 0);
    assertThat(practiceSquad(after)).extracting(c -> c.get("id")).contains(exposed);
    assertThat((List<Map<String, Object>>) after.get("waivers")).isEmpty();
    Player cleared = reload(UUID.fromString(exposed));
    assertThat(cleared.getRosterStatus()).isEqualTo(RosterStatus.PRACTICE_SQUAD);
    assertThat(cleared.getClub().getId()).isEqualTo(clubId());
    assertThat(cleared.getContract().getBaseSalaryByYear()).as("contrato mantido").isEqualTo(contractBefore.getBaseSalaryByYear());
    assertThat(cleared.getContract().getSigningBonus()).isEqualTo(contractBefore.getSigningBonus());
    Map<String, Object> detail = ok(call(mvc, get(base + "/players/" + exposed), auth, null));
    assertThat((List<Map<String, Object>>) detail.get("history")).extracting(h -> h.get("kind") + ":" + h.get("toStatus"))
        .containsExactly("STATUS_CHANGE:PRACTICE_SQUAD_WAIVERS", "WAIVER_CLEARED:PRACTICE_SQUAD");
  }

  /** Jogador da franquia com o maior dead money que a regra do 1º de junho joga para a temporada seguinte. */
  private Player withFutureDeadMoney() {
    UUID club = clubId();
    return tx().execute(st -> players.findByClubIdOrderByOverallDesc(club).stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE)
        .max(Comparator.comparingLong(p -> p.getContract().deadMoneySplit(p.getContract().yearIndexOf(2026), 5, true).nextSeason())).orElseThrow());
  }

  /**
   * Q-24, regra do 1º de junho pela data do jogo da carreira: a partir de 1º de junho, só a parcela do ano (bônus rateado do ano +
   * garantia do ano) entra na temporada e o resto vai para a seguinte; antes, todo o dead money restante entra na temporada atual.
   */
  @Test
  @SuppressWarnings("unchecked")
  void regra_do_1o_de_junho_antes_e_depois_da_data() throws Exception {
    // depois: a semana 1 começa em setembro
    Player late = withFutureDeadMoney();
    PlayerContract.DeadMoney lateSplit = late.getContract().deadMoneySplit(late.getContract().yearIndexOf(2026), 5, true);
    long lateTotal = late.getContract().deadMoneyIfCut(late.getContract().yearIndexOf(2026), 5);
    assertThat(lateSplit.nextSeason()).as("contrato com bônus a ratear nos anos seguintes").isPositive();
    Map<String, Object> lateDetail = ok(call(mvc, get(base + "/players/" + late.getId()), auth, null));
    assertThat(((Number) lateDetail.get("deadMoneyIfReleased")).longValue()).isEqualTo(lateSplit.currentSeason());
    assertThat(((Number) lateDetail.get("deadMoneyNextSeasonIfReleased")).longValue()).isEqualTo(lateSplit.nextSeason());
    Map<String, Object> lateRes = ok(call(mvc, post(base + "/players/" + late.getId() + "/release"), auth, null));
    assertThat(((Number) lateRes.get("deadMoney")).longValue()).isEqualTo(lateSplit.currentSeason());
    assertThat(((Number) lateRes.get("deadMoneyNextSeason")).longValue()).isEqualTo(lateSplit.nextSeason());
    assertThat(lateSplit.currentSeason() + lateSplit.nextSeason()).isEqualTo(lateTotal);
    Map<String, Object> capLate = (Map<String, Object>) lateRes.get("cap");
    assertThat(((Number) capLate.get("deadMoney")).longValue()).isEqualTo(lateSplit.currentSeason());
    assertThat(((Number) capLate.get("deadMoneyNextSeason")).longValue()).isEqualTo(lateSplit.nextSeason());

    // antes: a mesma carreira com a data do jogo em maio (offseason); teto folgado para a dispensa caber
    setGameDate(LocalDate.of(2026, 5, 31));
    setSalaryCap(Long.MAX_VALUE / 4);
    Player early = withFutureDeadMoney();
    long earlyTotal = early.getContract().deadMoneyIfCut(early.getContract().yearIndexOf(2026), 5);
    assertThat(earlyTotal).isGreaterThan(early.getContract().deadMoneySplit(early.getContract().yearIndexOf(2026), 5, true).currentSeason());
    Map<String, Object> earlyDetail = ok(call(mvc, get(base + "/players/" + early.getId()), auth, null));
    assertThat(((Number) earlyDetail.get("deadMoneyIfReleased")).longValue()).isEqualTo(earlyTotal);
    assertThat(((Number) earlyDetail.get("deadMoneyNextSeasonIfReleased")).longValue()).isZero();
    Map<String, Object> earlyRes = ok(call(mvc, post(base + "/players/" + early.getId() + "/release"), auth, null));
    assertThat(((Number) earlyRes.get("deadMoney")).longValue()).isEqualTo(earlyTotal);
    assertThat(((Number) earlyRes.get("deadMoneyNextSeason")).longValue()).isZero();
    Map<String, Object> capEarly = (Map<String, Object>) earlyRes.get("cap");
    assertThat(((Number) capEarly.get("deadMoney")).longValue()).isEqualTo(lateSplit.currentSeason() + earlyTotal);
    assertThat(((Number) capEarly.get("deadMoneyNextSeason")).longValue()).isEqualTo(lateSplit.nextSeason());
    assertThat(moveLog.findByCareerIdAndPlayerIdOrderByCreatedAtAsc(careerId, early.getId())).singleElement()
        .satisfies(m -> assertThat(m.getDeadMoneyNextSeason()).isZero());
  }

  /**
   * Q-24: a dispensa cujo dead money estoura o teto da temporada é recusada com 422 CAP_EXCEEDED, com teto, comprometido e excedente
   * em {@code details} e a mensagem no idioma do usuário; nada muda. No limite exato do teto, passa.
   */
  @Test
  @SuppressWarnings("unchecked")
  void dispensa_que_estoura_o_teto_e_recusada_com_cap_exceeded() throws Exception {
    setGameDate(LocalDate.of(2026, 3, 15));
    UUID club = clubId();
    Player target = tx().execute(st -> players.findByClubIdOrderByOverallDesc(club).stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE)
        .max(Comparator.comparingLong(p -> p.getContract().deadMoneyIfCut(p.getContract().yearIndexOf(2026), 5) - p.getContract().capHit(p.getContract().yearIndexOf(2026), 5)))
        .orElseThrow());
    long dead = target.getContract().deadMoneyIfCut(target.getContract().yearIndexOf(2026), 5);
    long hit = target.getContract().capHit(target.getContract().yearIndexOf(2026), 5);
    assertThat(dead).as("antes de 1º de junho o dead money passa do cap hit liberado").isGreaterThan(hit);
    long used = ((Number) ((Map<String, Object>) roster().get("cap")).get("capUsed")).longValue();
    long after = used - hit + dead;
    long excess = Math.max(1, (dead - hit) / 2);
    setSalaryCap(after - excess);

    MvcResult refused = call(mvc, post(base + "/players/" + target.getId() + "/release"), auth, null);
    assertProblem(refused, 422, "CAP_EXCEEDED");
    Map<String, Object> details = (Map<String, Object>) map(refused).get("details");
    assertThat(((Number) details.get("salaryCap")).longValue()).isEqualTo(after - excess);
    assertThat(((Number) details.get("capUsed")).longValue()).isEqualTo(after);
    assertThat(((Number) details.get("excess")).longValue()).isEqualTo(excess);
    String brl = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"))).format(excess / 100.0);
    assertThat((String) map(refused).get("message")).contains("S$ " + brl);
    assertThat(reload(target.getId()).getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(moveLog.findByCareerIdAndPlayerIdOrderByCreatedAtAsc(careerId, target.getId())).isEmpty();

    // no limite exato do teto, a dispensa passa
    setSalaryCap(after);
    Map<String, Object> res = ok(call(mvc, post(base + "/players/" + target.getId() + "/release"), auth, null));
    assertThat(((Number) ((Map<String, Object>) res.get("cap")).get("capSpace")).longValue()).isZero();
  }

  /** Q-22 (mantido): a IR continua exigindo lesão de pelo menos {@code irMinWeeks}, mesmo sem o motor gerar lesões até o RF64. */
  @Test
  void ir_continua_exigindo_lesao() throws Exception {
    Map<String, Object> e = active(roster()).stream().filter(c -> ((Number) c.get("injuryWeeksOut")).intValue() == 0).findFirst().orElseThrow();
    assertProblem(move(pid(e), "IR"), 422, "IR_NOT_ALLOWED");
    setInjury(pid(e), 3);
    assertProblem(move(pid(e), "IR"), 422, "IR_NOT_ALLOWED");
    setInjury(pid(e), 4);
    ok(move(pid(e), "IR"));
  }
}
