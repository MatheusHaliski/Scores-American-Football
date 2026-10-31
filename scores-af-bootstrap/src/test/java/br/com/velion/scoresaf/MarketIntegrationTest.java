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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ContractEventRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FreeAgencyWindowRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WaiverClaimRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.application.service.CapSheet;
import br.com.velion.scoresaf.application.usecase.rf46.AccountDeletionUseCases;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.web.security.TokenService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bloco 7a de ponta a ponta (RF49 teto e contratos, RF65 free agency e waivers): a folha, a renovação no último ano com aceite e recusa
 * determinísticos, a reestruturação, a franchise tag (uma por temporada, só na entressafra), o trade block, o teto rígido em cada
 * operação, a fase de free agency abrindo no avanço e fechando nos dias, a IA contratando dentro do teto, a fila de waivers (ordem inversa
 * da classificação, IA por necessidade, reivindicação do usuário), o acesso de outro usuário (403 auditado), a concorrência e a LGPD; e a
 * plausibilidade de duas temporadas avançadas (toda franquia com 53 e dentro do teto).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;
  @Autowired TokenService tokens;
  @Autowired CareerSaveRepository saves;
  @Autowired PlayerRepository players;
  @Autowired ClubRepository clubs;
  @Autowired RosterMoveRepository moves;
  @Autowired ContractEventRepository events;
  @Autowired FreeAgencyWindowRepository windows;
  @Autowired WaiverClaimRepository claims;
  @Autowired CapSheet capSheet;
  @Autowired AccountDeletionUseCases deletion;
  @Autowired TransactionTemplate tx;

  private record Career(String auth, String id, String base) {}

  private Career career(String club, long seed) throws Exception {
    String auth = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    MatchFixtures.level(mvc, auth, "NONE");
    UUID world = seeder.seedIfMissing().getId();
    Map<String, Object> c = create(mvc, auth, body("Mercado " + UUID.randomUUID().toString().substring(0, 6), null, club(mvc, auth, world, club), seed));
    return new Career(auth, id(c), "/api/v1/careers/" + id(c));
  }

  private SeasonFixtures.Career shortCareer(String club, long seed) throws Exception {
    String auth = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    return SeasonFixtures.shortCareer(mvc, auth, seeder.seedIfMissing().getId(), "Mercado curto " + UUID.randomUUID().toString().substring(0, 6), club, seed, 1);
  }

  private Map<String, Object> read(String auth, String path) throws Exception { return ok(call(mvc, get(path), auth, null)); }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> l(Object o) { return (List<Map<String, Object>>) o; }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> m(Object o) { return (Map<String, Object>) o; }

  private static long n(Object o) { return ((Number) o).longValue(); }

  /** Proposta igual à contraproposta (um mapa pronto para o corpo), com luvas a mais se pedido. */
  private static Map<String, Object> offer(Map<String, Object> asking, long extraBonus, long salaryDelta) {
    Map<String, Object> b = new HashMap<>();
    b.put("years", asking.get("years"));
    List<Long> salaries = new ArrayList<>();
    for (Object s : (List<?>) asking.get("salaryByYear")) salaries.add(n(s) + salaryDelta);
    b.put("salaryByYear", salaries);
    b.put("signingBonus", n(asking.get("signingBonus")) + extraBonus);
    b.put("guaranteed", Math.min(n(asking.get("guaranteed")), salaries.stream().mapToLong(Long::longValue).sum()));
    return b;
  }

  private UUID pick(Career c, java.util.function.Predicate<Player> filter) {
    return tx.execute(st -> {
      CareerSave save = saves.findById(UUID.fromString(c.id())).orElseThrow();
      return players.findByClubIdOrderByOverallDesc(save.getClub().getId()).stream().filter(filter)
          .min(Comparator.comparing(Player::getName)).orElseThrow().getId();
    });
  }

  private void setCap(String careerId, long cap) {
    tx.executeWithoutResult(st -> saves.findById(UUID.fromString(careerId)).orElseThrow().getLeague().setSalaryCap(cap));
  }

  // ================================================================ RF49

  @Test
  void folha_renovacao_com_aceite_e_recusa_e_janela_do_ultimo_ano() throws Exception {
    Career c = career("NES", 71L);
    Map<String, Object> cap = read(c.auth(), c.base() + "/cap");
    assertThat(cap).containsEntry("seasonYear", 2026).containsEntry("hardCap", true).containsEntry("rosterCount", 53);
    assertThat(l(cap.get("projections"))).extracting(p -> p.get("seasonYear")).containsExactly(2027, 2028, 2029);
    assertThat(n(cap.get("capUsed"))).isEqualTo(n(m(read(c.auth(), c.base() + "/roster").get("cap")).get("capUsed")));
    Map<String, Object> first = l(cap.get("contracts")).get(0);
    assertThat(l(first.get("years")).get(0)).containsKeys("season", "baseSalary", "prorationBonus", "guaranteed", "capHit", "deadMoneyIfCut");

    UUID expiring = pick(c, p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getContract().endSeason() == 2026 && p.getOverall() >= 60);
    Map<String, Object> detail = read(c.auth(), c.base() + "/players/" + expiring);
    Map<String, Object> market = m(detail.get("market"));
    assertThat(m(market.get("actions"))).containsEntry("canRenew", true).containsEntry("canFranchiseTag", false)
        .containsEntry("franchiseTagBlockedReason", "NOT_OFFSEASON");
    Map<String, Object> asking = m(market.get("asking"));

    // recusa determinística: S$ 1.000 a menos por ano já é recusado, com a contraproposta (o pedido) em details
    MvcResult refused = call(mvc, post(c.base() + "/players/" + expiring + "/renew"), c.auth(), offer(asking, 0, -1_000_00L));
    assertProblem(refused, 422, "OFFER_REFUSED");
    Map<String, Object> counter = m(m(map(refused).get("details")).get("counterOffer"));
    assertThat(counter).containsEntry("years", asking.get("years")).containsEntry("salaryByYear", asking.get("salaryByYear"));
    assertThat(n(m(map(refused).get("details")).get("offerValue"))).isLessThan(n(m(map(refused).get("details")).get("askingValue")));
    assertThat(map(refused).get("message")).asString().contains("recusou");
    // a mesma proposta é recusada de novo (sem sorteio)
    assertProblem(call(mvc, post(c.base() + "/players/" + expiring + "/renew"), c.auth(), offer(asking, 0, -1_000_00L)), 422, "OFFER_REFUSED");
    // proposta inválida
    assertProblem(call(mvc, post(c.base() + "/players/" + expiring + "/renew"), c.auth(),
        Map.of("years", 2, "salaryByYear", List.of(1_000L), "signingBonus", 0, "guaranteed", 0)), 422, "OFFER_INVALID");

    // aceite: a contraproposta
    Map<String, Object> renewed = ok(call(mvc, post(c.base() + "/players/" + expiring + "/renew"), c.auth(), offer(counter, 0, 0)));
    assertThat(renewed).containsEntry("kind", "RENEWAL").containsEntry("playerId", expiring.toString());
    int years = ((Number) counter.get("years")).intValue();
    assertThat(m(renewed.get("contract"))).containsEntry("startSeason", 2026).containsEntry("endSeason", 2026 + years);
    // fora da janela: o contrato já não está no último ano
    assertProblem(call(mvc, post(c.base() + "/players/" + expiring + "/renew"), c.auth(), offer(counter, 0, 0)), 422, "RENEWAL_NOT_ALLOWED");
    UUID lasting = pick(c, p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getContract().endSeason() >= 2028);
    MvcResult window = call(mvc, post(c.base() + "/players/" + lasting + "/renew"), c.auth(), offer(counter, 0, 0));
    assertProblem(window, 422, "RENEWAL_NOT_ALLOWED");
    assertThat(m(map(window).get("details"))).containsEntry("reason", "NOT_LAST_YEAR").containsEntry("seasonYear", 2026);
    assertThat(events.findByCareerIdOrderByCreatedAtAsc(UUID.fromString(c.id()))).singleElement()
        .satisfies(e -> assertThat(e.getKind().name()).isEqualTo("RENEWAL"));
  }

  @Test
  void teto_rigido_em_renovar_reestruturar_e_contratar() throws Exception {
    Career c = career("CRU", 72L);
    long used = n(read(c.auth(), c.base() + "/cap").get("capUsed"));
    UUID expiring = pick(c, p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getContract().endSeason() == 2026);
    Map<String, Object> asking = m(m(read(c.auth(), c.base() + "/players/" + expiring).get("market")).get("asking"));
    // teto no comprometido de agora: as luvas novas rateadas a partir de 2026 aumentam o comprometido da temporada
    setCap(c.id(), used);
    MvcResult over = call(mvc, post(c.base() + "/players/" + expiring + "/renew"), c.auth(), offer(asking, 1_000_000_00L, 0));
    assertProblem(over, 422, "CAP_EXCEEDED");
    assertThat(m(map(over).get("details"))).containsEntry("salaryCap", used).containsEntry("seasonYear", 2026).containsKeys("capUsed", "excess");
    assertThat(n(m(map(over).get("details")).get("excess"))).isPositive();
    // agente livre (em plena temporada) também esbarra no teto
    Map<String, Object> fa = read(c.auth(), c.base() + "/free-agency");
    assertThat(fa).containsEntry("phase", "REGULAR");
    assertThat(m(fa.get("window"))).containsEntry("status", "NONE").containsEntry("discountPct", 40);
    // reestruturação: libera espaço agora; com o teto da temporada seguinte no comprometido projetado, 422 (as parcelas sobem lá)
    UUID longDeal = pick(c, p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getContract().endSeason() >= 2028
        && p.getContract().baseSalary(p.getContract().yearIndexOf(2026)) > 3_000_000_00L);
    setCap(c.id(), 1_000_000_000_00L);
    Map<String, Object> cap = read(c.auth(), c.base() + "/cap");
    long next = n(l(cap.get("projections")).get(0).get("committed"));
    setCap(c.id(), next);
    MvcResult restructureOver = call(mvc, post(c.base() + "/players/" + longDeal + "/restructure"), c.auth(), Map.of("amount", 2_000_000_00L));
    assertProblem(restructureOver, 422, "CAP_EXCEEDED");
    assertThat(m(map(restructureOver).get("details"))).containsEntry("seasonYear", 2027);
    setCap(c.id(), 1_000_000_000_00L);
    long before = n(read(c.auth(), c.base() + "/cap").get("capUsed"));
    Map<String, Object> restructured = ok(call(mvc, post(c.base() + "/players/" + longDeal + "/restructure"), c.auth(), Map.of("amount", 2_000_000_00L)));
    assertThat(restructured).containsEntry("kind", "RESTRUCTURE");
    long after = n(m(restructured.get("cap")).get("capUsed"));
    assertThat(after).as("libera espaço agora").isLessThan(before);
    Map<String, Object> capAfter = read(c.auth(), c.base() + "/cap");
    assertThat(n(l(capAfter.get("projections")).get(0).get("committed"))).as("e sobe a temporada seguinte").isGreaterThan(next);
    assertProblem(call(mvc, post(c.base() + "/players/" + longDeal + "/restructure"), c.auth(), Map.of("amount", 999_000_000_00L)), 422,
        "RESTRUCTURE_INVALID");
    UUID lastYear = pick(c, p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getContract().endSeason() == 2026);
    MvcResult notAllowed = call(mvc, post(c.base() + "/players/" + lastYear + "/restructure"), c.auth(), null);
    assertProblem(notAllowed, 422, "RESTRUCTURE_NOT_ALLOWED");
    assertThat(m(map(notAllowed).get("details"))).containsEntry("reason", "TOO_FEW_YEARS");
    // franchise tag fora da entressafra
    MvcResult tag = call(mvc, post(c.base() + "/players/" + lastYear + "/franchise-tag"), c.auth(), null);
    assertProblem(tag, 422, "FRANCHISE_TAG_NOT_ALLOWED");
    assertThat(m(map(tag).get("details"))).containsEntry("reason", "NOT_OFFSEASON");
  }

  @Test
  void trade_block_grava_e_mostra() throws Exception {
    Career c = career("PAV", 73L);
    UUID p = pick(c, x -> x.getRosterStatus() == RosterStatus.ACTIVE);
    assertThat(ok(call(mvc, post(c.base() + "/players/" + p + "/trade-block"), c.auth(), null))).containsEntry("tradeBlock", true);
    assertThat(m(read(c.auth(), c.base() + "/players/" + p).get("card"))).containsEntry("tradeBlock", true);
    assertThat(m(m(read(c.auth(), c.base() + "/players/" + p).get("market")).get("actions"))).containsEntry("tradeBlock", true);
    assertThat(ok(call(mvc, post(c.base() + "/players/" + p + "/trade-block/remove"), c.auth(), null))).containsEntry("tradeBlock", false);
    // jogador de outra franquia: 422 PLAYER_NOT_IN_ROSTER
    UUID other = tx.execute(st -> players.findByWorldId(saves.findById(UUID.fromString(c.id())).orElseThrow().getWorld().getId()).stream()
        .filter(x -> x.getClub() != null && !x.getClub().getId().equals(saves.findById(UUID.fromString(c.id())).orElseThrow().getClub().getId()))
        .findFirst().orElseThrow().getId());
    assertProblem(call(mvc, post(c.base() + "/players/" + other + "/trade-block"), c.auth(), null), 422, "PLAYER_NOT_IN_ROSTER");
  }

  @Test
  void outro_usuario_recebe_403_nas_rotas_do_mercado() throws Exception {
    Career c = career("NES", 74L);
    String intruder = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    UUID p = pick(c, x -> x.getRosterStatus() == RosterStatus.ACTIVE);
    assertProblem(call(mvc, get(c.base() + "/cap"), intruder, null), 403, "FORBIDDEN");
    assertProblem(call(mvc, get(c.base() + "/free-agency"), intruder, null), 403, "FORBIDDEN");
    assertProblem(call(mvc, get(c.base() + "/waivers"), intruder, null), 403, "FORBIDDEN");
    assertProblem(call(mvc, post(c.base() + "/free-agency/advance"), intruder, null), 403, "FORBIDDEN");
    assertProblem(call(mvc, post(c.base() + "/players/" + p + "/renew"), intruder,
        Map.of("years", 1, "salaryByYear", List.of(100_000_000L), "signingBonus", 0, "guaranteed", 0)), 403, "FORBIDDEN");
    assertProblem(call(mvc, post(c.base() + "/players/" + p + "/restructure"), intruder, null), 403, "FORBIDDEN");
    assertProblem(call(mvc, post(c.base() + "/players/" + p + "/franchise-tag"), intruder, null), 403, "FORBIDDEN");
    assertProblem(call(mvc, post(c.base() + "/players/" + p + "/trade-block"), intruder, null), 403, "FORBIDDEN");
    assertProblem(call(mvc, post(c.base() + "/waivers/" + p + "/claim"), intruder, null), 403, "FORBIDDEN");
  }

  // ================================================================ entressafra: tag, free agency

  @Test
  void franchise_tag_uma_por_temporada_e_free_agency_abre_anda_e_fecha() throws Exception {
    SeasonFixtures.Career c = shortCareer("NES", 75L);
    SeasonFixtures.playToEnd(mvc, c);
    Career cc = new Career(c.auth(), c.id(), c.base());
    List<UUID> tagged = new ArrayList<>();
    tx.executeWithoutResult(st -> {
      CareerSave save = saves.findById(UUID.fromString(c.id())).orElseThrow();
      players.findByClubIdOrderByOverallDesc(save.getClub().getId()).stream()
          .filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getContract().endSeason() == 2026).sorted(Comparator.comparing(Player::getName))
          .limit(2).forEach(p -> tagged.add(p.getId()));
    });
    Map<String, Object> cap = read(c.auth(), c.base() + "/cap");
    assertThat(m(cap.get("franchiseTag"))).containsEntry("windowOpen", true).containsEntry("used", false);
    // restruturar na entressafra não (a temporada acabou)
    UUID lasting = pick(cc, p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getContract().endSeason() >= 2028);
    MvcResult r = call(mvc, post(c.base() + "/players/" + lasting + "/restructure"), c.auth(), null);
    assertProblem(r, 422, "RESTRUCTURE_NOT_ALLOWED");
    assertThat(m(map(r).get("details"))).containsEntry("reason", "SEASON_COMPLETED");
    // a tag: um ano a mais, todo garantido, e o teto da temporada seguinte confere
    Map<String, Object> t = ok(call(mvc, post(c.base() + "/players/" + tagged.get(0) + "/franchise-tag"), c.auth(), null));
    assertThat(m(t.get("contract"))).containsEntry("franchiseTag", true).containsEntry("type", "TAG").containsEntry("endSeason", 2027);
    MvcResult second = call(mvc, post(c.base() + "/players/" + tagged.get(1) + "/franchise-tag"), c.auth(), null);
    assertProblem(second, 409, "FRANCHISE_TAG_USED");
    assertThat(m(map(second).get("details"))).containsEntry("playerId", tagged.get(0).toString());
    assertThat(m(read(c.auth(), c.base() + "/cap").get("franchiseTag"))).containsEntry("used", true).containsEntry("playerId", tagged.get(0).toString());

    // avanço: a free agency abre na temporada nova (fase FREE_AGENCY, dia 0) e a semana 1 não joga
    String seasonId = (String) read(c.auth(), c.base() + "/season/summary").get("seasonId");
    Map<String, Object> adv = ok(call(mvc, post(c.base() + "/season/advance"), c.auth(), Map.of("seasonId", seasonId)));
    assertThat(adv).containsEntry("phase", "FREE_AGENCY");
    assertThat(l(adv.get("steps"))).extracting(s -> s.get("phase") + ":" + s.get("status") + ":" + s.get("handledBy"))
        .containsExactly("RENEWALS:DONE:SEASON_ADVANCE", "FREE_AGENCY:OPEN:FREE_AGENCY", "DRAFT:SKIPPED:BLOCK_7", "PRESEASON:DONE:SEASON_ADVANCE");
    assertProblem(call(mvc, post(c.base() + "/weeks/current/play"), c.auth(), null), 409, "FREE_AGENCY_OPEN");
    assertThat(players.findById(tagged.get(0)).orElseThrow().getClub()).as("o jogador com a tag fica").isNotNull();
    Map<String, Object> fa = read(c.auth(), c.base() + "/free-agency?sort=OVERALL&limit=500");
    assertThat(m(fa.get("window"))).containsEntry("status", "OPEN").containsEntry("day", 0).containsEntry("discountPct", 0)
        .containsEntry("marketDate", "2027-03-12");
    assertThat(l(fa.get("players"))).isNotEmpty().allSatisfy(p -> assertThat(p).containsKeys("asking", "source", "marketValue"));
    assertThat(l(fa.get("players"))).extracting(p -> p.get("source")).contains("CONTRACT_EXPIRED");
    List<Map<String, Object>> qbs = l(read(c.auth(), c.base() + "/free-agency?position=QB").get("players"));
    assertThat(qbs).allSatisfy(p -> assertThat(p.get("position")).isEqualTo("QB"));

    // o usuário contrata pelo pedido; abaixo dele, recusa
    Map<String, Object> target = l(fa.get("players")).get(l(fa.get("players")).size() - 1);
    long count = n(m(fa.get("counts")).get("roster"));
    Map<String, Object> asking = m(target.get("asking"));
    Map<String, Object> low = offer(asking, 0, -1_000_00L);
    low.put("playerId", target.get("id"));
    if (n(((List<?>) low.get("salaryByYear")).get(0)) >= 90_000_000L) assertProblem(call(mvc, post(c.base() + "/free-agency"), c.auth(), low), 422, "OFFER_REFUSED");
    Map<String, Object> good = offer(asking, 0, 0);
    good.put("playerId", target.get("id"));
    Map<String, Object> signed = ok(call(mvc, post(c.base() + "/free-agency"), c.auth(), good));
    assertThat(signed).containsEntry("kind", "SIGNING").containsEntry("rosterStatus", "ACTIVE");
    assertThat(n(m(signed.get("counts")).get("roster"))).isEqualTo(count + 1);
    assertProblem(call(mvc, post(c.base() + "/free-agency"), c.auth(), good), 422, "NOT_A_FREE_AGENT");

    // um dia: os pedidos caem 10 % e a IA contrata
    Map<String, Object> day1 = ok(call(mvc, post(c.base() + "/free-agency/advance"), c.auth(), Map.of("mode", "DAY")));
    assertThat(m(day1.get("window"))).containsEntry("day", 1).containsEntry("discountPct", 10).containsEntry("status", "OPEN");
    assertThat(l(day1.get("recentSignings"))).anySatisfy(s -> assertThat(s).containsEntry("source", "AI"));
    // fecha: a temporada passa à temporada regular e a semana 1 joga
    Map<String, Object> closed = ok(call(mvc, post(c.base() + "/free-agency/advance"), c.auth(), Map.of("mode", "CLOSE")));
    assertThat(m(closed.get("window"))).containsEntry("status", "CLOSED").containsEntry("day", 4);
    assertThat(closed).containsEntry("phase", "REGULAR");
    assertProblem(call(mvc, post(c.base() + "/free-agency/advance"), c.auth(), null), 422, "FREE_AGENCY_CLOSED");
    assertAiWithinCapAnd53(UUID.fromString(c.id()));
  }

  /** Toda franquia da IA com 53 e dentro do teto (comprometido ≤ teto). */
  private void assertAiWithinCapAnd53(UUID careerId) {
    tx.executeWithoutResult(st -> {
      CareerSave save = saves.findById(careerId).orElseThrow();
      long salaryCap = save.getLeague().getSalaryCap();
      for (Club club : clubs.findByWorldId(save.getWorld().getId())) {
        List<Player> roster = players.findByClubIdOrderByOverallDesc(club.getId()).stream().filter(p -> p.getRosterStatus().requiresClub()).toList();
        long counted = roster.stream().filter(p -> p.getRosterStatus().countsTowardRoster()).count();
        long committed = capSheet.committed(save, club.getId(), roster, save.getSeason().getYear());
        assertThat(committed).as("comprometido de %s", club.getShortName()).isLessThanOrEqualTo(salaryCap);
        if (!club.getId().equals(save.getClub().getId())) assertThat(counted).as("53 de %s", club.getShortName()).isEqualTo(53);
      }
    });
  }

  // ================================================================ waivers

  @Test
  void fila_de_waivers_pela_ordem_inversa_ia_por_necessidade_e_reivindicacao_do_usuario() throws Exception {
    SeasonFixtures.Career c = SeasonFixtures.shortCareer(mvc, TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER), seeder.seedIfMissing().getId(),
        "Waivers " + UUID.randomUUID().toString().substring(0, 6), "NES", 76L, 2);
    SeasonFixtures.playWeek(mvc, c);
    Map<String, Object> w = read(c.auth(), c.base() + "/waivers");
    int priority = ((Number) w.get("priority")).intValue();
    assertThat(w).containsEntry("clubs", 32).containsEntry("resolvesAt", "WEEK_CLOSE");
    UUID careerId = UUID.fromString(c.id());
    List<Map<String, Object>> exposedBefore = l(w.get("players"));
    assertThat(exposedBefore).isEmpty();
    // o usuário dispensa um jogador barato: ele aparece em waivers e o próprio usuário não pode reivindicá-lo
    UUID cheap = tx.execute(st -> players.findByClubIdOrderByOverallDesc(saves.findById(careerId).orElseThrow().getClub().getId()).stream()
        .filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getContract().getSigningBonus() == 0 && p.getContract().getGuaranteed() == 0)
        .min(Comparator.comparingLong((Player p) -> p.getContract().capHit(0, 5)).thenComparing(Player::getName)).orElseThrow().getId());
    ok(call(mvc, post(c.base() + "/players/" + cheap + "/release"), c.auth(), null));
    Map<String, Object> after = read(c.auth(), c.base() + "/waivers");
    assertThat(l(after.get("players"))).singleElement().satisfies(p -> assertThat(p).containsEntry("id", cheap.toString()).containsEntry("kind", "RELEASED")
        .containsEntry("canClaim", false).containsEntry("blockedReason", "OWN_PLAYER"));
    assertProblem(call(mvc, post(c.base() + "/waivers/" + cheap + "/claim"), c.auth(), null), 422, "WAIVER_OWN_PLAYER");
    assertThat(priority).isBetween(1, 32);

    // dois jogadores de outra franquia em waivers, da mesma posição; A (à frente do usuário na fila) e B (atrás) com vaga nos 53 e
    // precisando da posição; o usuário (com a vaga da dispensa) reivindica os dois
    String userClub = tx.execute(st -> saves.findById(careerId).orElseThrow().getClub().getId().toString());
    List<String> orderIds = priorityIds(c);
    int userAt = orderIds.indexOf(userClub);
    assertThat(userAt).isEqualTo(priority - 1);
    assertThat(userAt).as("o usuário precisa de alguém à frente e atrás na fila").isBetween(1, 30);
    UUID clubA = UUID.fromString(orderIds.get(userAt - 1)), clubB = UUID.fromString(orderIds.get(userAt + 1));
    UUID clubC = UUID.fromString(orderIds.stream().filter(x -> !x.equals(clubA.toString()) && !x.equals(clubB.toString())
        && !x.equals(userClub)).findFirst().orElseThrow());
    List<UUID> exposed = tx.execute(st -> {
      CareerSave save = saves.findById(careerId).orElseThrow();
      // A e B ficam com um CB a menos (vaga e necessidade); C expõe os dois CBs mais baratos dele
      for (UUID club : List.of(clubA, clubB)) {
        Player gone = players.findByClubIdOrderByOverallDesc(club).stream().filter(p -> p.getPosition() == Position.CB && p.getRosterStatus() == RosterStatus.ACTIVE)
            .min(Comparator.comparing(Player::getName)).orElseThrow();
        gone.setClub(null); gone.setRosterStatus(RosterStatus.FREE_AGENT);
        players.save(gone);
      }
      List<Player> cbs = players.findByClubIdOrderByOverallDesc(clubC).stream().filter(p -> p.getPosition() == Position.CB && p.getRosterStatus() == RosterStatus.ACTIVE)
          .sorted(Comparator.comparingLong((Player p) -> p.getContract().capHit(0, 5)).thenComparing(Player::getName)).limit(2).toList();
      List<UUID> out = new ArrayList<>();
      for (Player p : cbs) {
        RosterMove m = new RosterMove();
        m.setCareer(save); m.setSeason(save.getSeason()); m.setPlayer(p); m.setClub(p.getClub()); m.setWeek(save.getCurrentWeek()); m.setKind(RosterMoveKind.RELEASE);
        m.setFromStatus(RosterStatus.ACTIVE); m.setToStatus(RosterStatus.WAIVED);
        moves.save(m);
        p.setClub(null); p.setRosterStatus(RosterStatus.WAIVED);
        players.save(p);
        out.add(p.getId());
      }
      // o primeiro processado é o melhor (overall maior, depois mais novo e nome)
      out.sort(Comparator.comparing(id -> players.findById(id).orElseThrow(), br.com.velion.scoresaf.application.service.market.MarketService.BEST));
      return out;
    });
    setCap(c.id(), 1_000_000_000_00L);
    for (UUID x : exposed) ok(call(mvc, post(c.base() + "/waivers/" + x + "/claim"), c.auth(), null));
    assertProblem(call(mvc, post(c.base() + "/waivers/" + exposed.get(0) + "/claim"), c.auth(), null), 409, "WAIVER_CLAIM_EXISTS");
    assertThat(l(read(c.auth(), c.base() + "/waivers").get("players"))).filteredOn(p -> exposed.contains(UUID.fromString((String) p.get("id"))))
        .allSatisfy(p -> assertThat(p).containsEntry("claimedByUser", true));
    // cancelar e pedir de novo
    ok(call(mvc, delete(c.base() + "/waivers/" + exposed.get(1) + "/claim"), c.auth(), null));
    ok(call(mvc, post(c.base() + "/waivers/" + exposed.get(1) + "/claim"), c.auth(), null));

    // a semana fecha: o melhor vai para A (à frente do usuário, precisa de CB); o segundo para o usuário (à frente de B)
    SeasonFixtures.playWeek(mvc, c);
    tx.executeWithoutResult(st -> {
      CareerSave save = saves.findById(careerId).orElseThrow();
      Player first = players.findById(exposed.get(0)).orElseThrow(), second = players.findById(exposed.get(1)).orElseThrow();
      assertThat(first.getClub().getId()).as("a IA à frente na fila leva o primeiro").isEqualTo(clubA);
      assertThat(second.getClub().getId()).as("o usuário, à frente de B, leva o segundo").isEqualTo(save.getClub().getId());
      assertThat(second.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
      assertThat(second.getContract().getSigningBonus()).as("as luvas ficam com quem dispensou").isZero();
      assertThat(moves.findByCareerIdAndPlayerIdOrderByCreatedAtAsc(careerId, second.getId())).extracting(RosterMove::getKind)
          .containsExactly(RosterMoveKind.RELEASE, RosterMoveKind.WAIVER_CLAIM);
      assertThat(claims.findByCareerIdOrderByCreatedAtAsc(careerId)).as("reivindicações resolvidas saem").isEmpty();
      Player released = players.findById(cheap).orElseThrow();
      assertThat(released.getRosterStatus()).as("o dispensado do usuário: reivindicado pela IA ou agente livre")
          .isIn(RosterStatus.FREE_AGENT, RosterStatus.ACTIVE);
    });
  }

  /** A fila de prioridade (a mesma que a API usa no {@code priority} do {@code GET /waivers}). */
  @Autowired br.com.velion.scoresaf.application.service.market.MarketService market;

  private List<String> priorityIds(SeasonFixtures.Career c) {
    return tx.execute(st -> market.priorityOrder(saves.findById(UUID.fromString(c.id())).orElseThrow()).stream().map(x -> x.getId().toString()).toList());
  }

  // ================================================================ concorrência e LGPD

  @Test
  void duas_tags_juntas_dao_200_e_409_e_a_lgpd_leva_e_apaga_o_mercado() throws Exception {
    TestSessions.Session session = TestSessions.open(users, sessions, tokens, UserRole.PLAYER);
    SeasonFixtures.Career c = SeasonFixtures.shortCareer(mvc, session.bearer(), seeder.seedIfMissing().getId(),
        "Tags " + UUID.randomUUID().toString().substring(0, 6), "CRU", 77L, 1);
    SeasonFixtures.playToEnd(mvc, c);
    List<UUID> two = tx.execute(st -> players.findByClubIdOrderByOverallDesc(saves.findById(UUID.fromString(c.id())).orElseThrow().getClub().getId())
        .stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getContract().endSeason() == 2026).sorted(Comparator.comparing(Player::getName))
        .limit(2).map(Player::getId).toList());
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<Integer>> replies = new ArrayList<>();
    try {
      for (UUID p : two) replies.add(pool.submit(() -> {
        go.await();
        return call(mvc, post(c.base() + "/players/" + p + "/franchise-tag"), c.auth(), null).getResponse().getStatus();
      }));
      go.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> f : replies) statuses.add(f.get());
      assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    } finally {
      pool.shutdownNow();
    }
    UUID careerId = UUID.fromString(c.id());
    assertThat(events.findByCareerIdOrderByCreatedAtAsc(careerId)).singleElement().satisfies(e -> assertThat(e.getKind().name()).isEqualTo("FRANCHISE_TAG"));
    String seasonId = (String) read(c.auth(), c.base() + "/season/summary").get("seasonId");
    ok(call(mvc, post(c.base() + "/season/advance"), c.auth(), Map.of("seasonId", seasonId)));
    UUID someone = tx.execute(st -> players.findByWorldIdAndRosterStatus(saves.findById(careerId).orElseThrow().getWorld().getId(), RosterStatus.FREE_AGENT)
        .stream().min(Comparator.comparing(Player::getName)).orElseThrow().getId());
    ok(call(mvc, post(c.base() + "/players/" + two.get(0) + "/trade-block"), c.auth(), null));

    Map<String, Object> export = ok(call(mvc, get("/api/v1/me/export"), c.auth(), null));
    assertThat(l(export.get("careerContracts"))).singleElement().satisfies(row -> assertThat(row).containsEntry("kind", "FRANCHISE_TAG")
        .containsEntry("careerId", c.id()).containsKeys("totalValue", "player"));
    assertThat(l(export.get("careerMarket"))).extracting(row -> row.get("kind")).contains("FREE_AGENCY", "TRADE_BLOCK");
    assertThat(someone).isNotNull();

    deletion.erase(users.findById(session.user().getId()).orElseThrow());
    assertThat(saves.findById(careerId)).isEmpty();
    assertThat(events.findByCareerIdOrderByCreatedAtAsc(careerId)).isEmpty();
    assertThat(windows.findByCareerIdOrderByCreatedAtAsc(careerId)).isEmpty();
    assertThat(claims.findByCareerIdOrderByCreatedAtAsc(careerId)).isEmpty();
  }

  // ================================================================ plausibilidade

  /**
   * Duas temporadas avançadas (curtas, 1 jogo por franquia), com a free agency de cada uma: o usuário completa os 53 contratando pelo
   * pedido os agentes livres mais baratos, fecha a janela e joga; no fim, toda franquia tem 53 e está dentro do teto.
   */
  @Test
  void duas_temporadas_avancadas_toda_franquia_com_53_e_dentro_do_teto() throws Exception {
    SeasonFixtures.Career c = shortCareer("PAV", 78L);
    UUID careerId = UUID.fromString(c.id());
    for (int season = 1; season <= 2; season++) {
      SeasonFixtures.playToEnd(mvc, c);
      String seasonId = (String) read(c.auth(), c.base() + "/season/summary").get("seasonId");
      ok(call(mvc, post(c.base() + "/season/advance"), c.auth(), Map.of("seasonId", seasonId)));
      fillRoster(c);
      Map<String, Object> closed = ok(call(mvc, post(c.base() + "/free-agency/advance"), c.auth(), Map.of("mode", "CLOSE")));
      assertThat(closed).containsEntry("phase", "REGULAR");
      fillRoster(c);
      assertAiWithinCapAnd53(careerId);
      Map<String, Object> roster = read(c.auth(), c.base() + "/roster");
      assertThat(m(roster.get("counts"))).containsEntry("roster", 53);
      assertThat(n(m(roster.get("cap")).get("capSpace"))).isNotNegative();
    }
    SeasonFixtures.playWeek(mvc, c);
  }

  /** O usuário contrata, pelo pedido, os agentes livres mais baratos da posição que falta até fechar os 53. */
  private void fillRoster(SeasonFixtures.Career c) throws Exception {
    for (int guard = 0; guard < 60; guard++) {
      Map<String, Object> fa = read(c.auth(), c.base() + "/free-agency?sort=OVERALL&limit=200");
      if (n(m(fa.get("counts")).get("roster")) >= 53) return;
      List<Map<String, Object>> list = new ArrayList<>(l(fa.get("players")));
      list.sort(Comparator.comparingLong((Map<String, Object> p) -> n(p.get("askingCapHit"))).thenComparing(p -> (String) p.get("name")));
      Map<String, Object> cheapest = list.get(0);
      Map<String, Object> body = offer(m(cheapest.get("asking")), 0, 0);
      body.put("playerId", cheapest.get("id"));
      ok(call(mvc, post(c.base() + "/free-agency"), c.auth(), body));
    }
    throw new AssertionError("o elenco não fechou 53");
  }
}
