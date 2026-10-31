package br.com.velion.scoresaf;

import static br.com.velion.scoresaf.CareerFixtures.assertProblem;
import static br.com.velion.scoresaf.CareerFixtures.call;
import static br.com.velion.scoresaf.CareerFixtures.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.port.out.persistence.AuditLogRepository;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.domain.account.AuditLog;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.career.RosterRules;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.web.security.TokenService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bloco 6c de ponta a ponta, numa temporada curta (o mundo padrão com 1 jogo por franquia, SeasonFixtures): o fim da temporada regular
 * abre os playoffs (7 sementes por conferência, 4 campeões de divisão nas primeiras, folga da semente 1), re-seed a cada rodada, jogos
 * de playoff sem empate, final em campo neutro, campeã, prêmios, notícias e troféus (RF16, RF21, RF35, RF66); waivers processados nas
 * semanas de playoff (Q-39); o avanço da temporada só depois da final e uma vez só, com envelhecimento, fim de contrato, dead money da
 * temporada seguinte e calendário novo; e o acesso de outro usuário às rotas novas (403 auditado).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeasonIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DefaultWorldSeeder seeder;
  @Autowired UserAccountRepository users;
  @Autowired UserSessionRepository sessions;
  @Autowired TokenService tokens;
  @Autowired FixtureRepository fixtures;
  @Autowired PlayerRepository players;
  @Autowired RosterMoveRepository moves;
  @Autowired CareerSaveRepository saves;
  @Autowired ClubRepository clubs;
  @Autowired AuditLogRepository auditLog;
  @Autowired TransactionTemplate tx;

  private SeasonFixtures.Career career(String name, String club, long seed) throws Exception {
    String auth = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    return SeasonFixtures.shortCareer(mvc, auth, seeder.seedIfMissing().getId(), name + " " + UUID.randomUUID().toString().substring(0, 6), club, seed, 1);
  }

  private Map<String, Object> read(SeasonFixtures.Career c, String path) throws Exception { return ok(call(mvc, get(c.base() + path), c.auth(), null)); }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> l(Object o) { return (List<Map<String, Object>>) o; }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> m(Object o) { return (Map<String, Object>) o; }

  private static int n(Object o) { return ((Number) o).intValue(); }

  @Test
  void fim_da_temporada_regular_abre_os_playoffs_com_7_por_conferencia_re_seed_e_final_neutra() throws Exception {
    SeasonFixtures.Career c = career("Playoffs", "NES", 5L);
    Map<String, Object> projected = read(c, "/playoffs");
    assertThat(projected).containsEntry("status", "NOT_STARTED").containsEntry("projection", true).containsEntry("seedsPerConference", 7)
        .containsEntry("byeSeeds", 1).containsEntry("rounds", List.of("WILD_CARD", "DIVISIONAL", "CONFERENCE", "FINAL"));
    Map<String, Object> projectedBracket = read(c, "/playoffs/bracket");
    assertThat(l(projectedBracket.get("rounds")).get(0)).containsEntry("status", "PROJECTED").satisfies(r -> assertThat(l(r.get("games"))).hasSize(6));

    SeasonFixtures.playWeek(mvc, c);
    Map<String, Object> started = read(c, "/playoffs");
    assertThat(started).containsEntry("status", "IN_PROGRESS").containsEntry("projection", false).containsEntry("currentRound", "WILD_CARD");
    Map<String, Integer> seedOf = new HashMap<>();
    for (Map<String, Object> conf : l(started.get("conferences"))) {
      List<Map<String, Object>> seeds = l(conf.get("seeds"));
      assertThat(seeds).extracting(s -> n(s.get("seed"))).containsExactly(1, 2, 3, 4, 5, 6, 7);
      assertThat(seeds.subList(0, 4)).allSatisfy(s -> assertThat(s).containsEntry("divisionChampion", true));
      assertThat(seeds.subList(4, 7)).allSatisfy(s -> assertThat(s).containsEntry("divisionChampion", false));
      assertThat(seeds).extracting(s -> s.get("bye")).containsExactly(true, false, false, false, false, false, false);
      seeds.forEach(s -> seedOf.put((String) m(s.get("club")).get("id"), n(s.get("seed"))));
    }
    assertThat(seedOf).hasSize(14);
    Map<String, Object> standings = read(c, "/standings");
    assertThat(standings).containsEntry("official", true).containsEntry("throughWeek", 1);
    // o calendário ganhou a semana do Wild Card; a folga é só das sementes 1
    Map<String, Object> week2 = l(read(c, "/calendar?week=2").get("weeks")).get(0);
    assertThat(week2).containsEntry("kind", "WILD_CARD").containsEntry("status", "CURRENT");
    assertThat(l(week2.get("fixtures"))).hasSize(6);
    assertThat((List<?>) week2.get("byeClubIds")).hasSize(2).allSatisfy(id -> assertThat(seedOf.get((String) id)).isEqualTo(1));
    Map<String, Object> wc = l(read(c, "/playoffs/bracket").get("rounds")).get(0);
    assertThat(wc).containsEntry("kind", "WILD_CARD").containsEntry("week", 2).containsEntry("status", "CURRENT");
    assertThat(l(wc.get("games"))).extracting(g -> n(m(g.get("home")).get("seed")) + "x" + n(m(g.get("away")).get("seed")))
        .containsExactlyInAnyOrder("2x7", "3x6", "4x5", "2x7", "3x6", "4x5");

    SeasonFixtures.playToEnd(mvc, c);
    List<Map<String, Object>> rounds = l(read(c, "/playoffs/bracket").get("rounds"));
    assertThat(rounds).extracting(r -> r.get("status")).containsOnly("COMPLETED");
    // re-seed: no Divisional, a semente 1 de cada conferência pega a pior semente que sobrou do Wild Card
    Map<String, Integer> worstAlive = new HashMap<>();
    for (Map<String, Object> g : l(rounds.get(0).get("games"))) {
      String winner = (String) g.get("winnerClubId");
      worstAlive.merge((String) g.get("conference"), seedOf.get(winner), Math::max);
    }
    for (Map<String, Object> g : l(rounds.get(1).get("games"))) {
      if (n(m(g.get("home")).get("seed")) == 1) assertThat(n(m(g.get("away")).get("seed"))).isEqualTo(worstAlive.get((String) g.get("conference")));
    }
    assertThat(l(rounds.get(1).get("games"))).hasSize(4).anySatisfy(g -> assertThat(n(m(g.get("home")).get("seed"))).isEqualTo(1));
    Map<String, Object> fin = l(rounds.get(3).get("games")).get(0);
    assertThat(fin).containsEntry("neutralSite", true).doesNotContainKey("conference");
    // jogo de playoff nunca termina empatado (D-40)
    UUID seasonId = UUID.fromString((String) read(c, "").get("seasonId"));
    List<Fixture> playoff = fixtures.findBySeasonId(seasonId).stream().filter(f -> f.getWeekNumber() > 1).toList();
    assertThat(playoff).hasSize(13).allSatisfy(f -> {
      assertThat(f.isFinished()).isTrue();
      assertThat(f.getHomeScore()).isNotEqualTo(f.getAwayScore());
    });

    Map<String, Object> done = read(c, "/playoffs");
    assertThat(done).containsEntry("status", "FINISHED");
    assertThat(m(done.get("champion")).get("id")).isEqualTo(fin.get("winnerClubId"));
    Map<String, Object> summary = read(c, "/season/summary");
    assertThat(summary).containsEntry("completed", true).containsEntry("phase", "OFFSEASON").containsEntry("provisionalAwards", false);
    assertThat(m(summary.get("champion")).get("id")).isEqualTo(fin.get("winnerClubId"));
    assertThat(l(summary.get("awards"))).extracting(a -> a.get("type")).contains("MVP", "OPOY", "DPOY", "COACH");
    assertThat(l(m(summary.get("leaders")).get("PASS_YARDS"))).isNotEmpty();
    assertThat(m(summary.get("offseason"))).containsEntry("canAdvance", true);
    assertThat(l(read(c, "/weeks/5/news").get("items"))).anySatisfy(i -> assertThat(i).containsEntry("kind", "CHAMPION"));
    assertProblem(call(mvc, post(c.base() + "/weeks/current/play"), c.auth(), null), 409, "NO_NEXT_GAME");
    Map<String, Object> trophies = read(c, "/trophies");
    assertThat(l(trophies.get("trophies"))).extracting(t -> t.get("kind")).filteredOn("CHAMPIONSHIP"::equals).hasSize(1);
    assertThat(l(trophies.get("trophies"))).extracting(t -> t.get("kind")).filteredOn("CONFERENCE"::equals).hasSize(2);
    assertThat(l(trophies.get("trophies"))).extracting(t -> t.get("kind")).filteredOn("DIVISION"::equals).hasSize(8);
    assertThat(l(trophies.get("seasons"))).singleElement().satisfies(s -> assertThat(s).containsEntry("completed", true));
    assertThat(l(read(c, "/calendar").get("playoffWeeks"))).extracting(w -> w.get("status")).containsOnly("COMPLETED");
  }

  @Test
  @SuppressWarnings("unchecked")
  void waivers_sao_processados_nas_semanas_de_playoff() throws Exception {
    SeasonFixtures.Career c = career("Waivers", "CRU", 8L);
    SeasonFixtures.playWeek(mvc, c);
    Map<String, Object> roster = read(c, "/roster");
    assertThat(roster).containsEntry("currentWeek", 2);
    List<Map<String, Object>> active = ((Map<String, List<Map<String, Object>>>) roster.get("units")).values().stream().flatMap(List::stream)
        .filter(p -> "ACTIVE".equals(p.get("rosterStatus"))).toList();
    String exposed = (String) active.stream().filter(p -> n(p.get("experienceYears")) <= 3).filter(p -> !List.of("QB", "K", "P", "LS").contains(p.get("position")))
        .sorted(Comparator.comparingInt(p -> n(p.get("overall")))).findFirst().orElseThrow().get("id");
    ok(call(mvc, post(c.base() + "/players/" + l(roster.get("practiceSquad")).get(0).get("id") + "/release"), c.auth(), null));
    Map<String, Object> moved = ok(call(mvc, patch(c.base() + "/players/" + exposed + "/roster-status"), c.auth(), Map.of("status", "PRACTICE_SQUAD")));
    assertThat(m(moved.get("player"))).containsEntry("rosterStatus", "PRACTICE_SQUAD_WAIVERS");
    // a semana 2 é o Wild Card: fechá-la processa os waivers como na temporada regular (Q-39)
    SeasonFixtures.playWeek(mvc, c);
    Player cleared = tx.execute(st -> players.findById(UUID.fromString(exposed)).orElseThrow());
    assertThat(cleared.getRosterStatus()).isEqualTo(RosterStatus.PRACTICE_SQUAD);
    List<RosterMove> history = tx.execute(st -> moves.findByCareerIdAndPlayerIdOrderByCreatedAtAsc(UUID.fromString(c.id()), UUID.fromString(exposed)));
    assertThat(history).extracting(RosterMove::getKind).containsExactly(RosterMoveKind.STATUS_CHANGE, RosterMoveKind.WAIVER_CLEARED);
    assertThat(history.get(1).getWeek()).isEqualTo(2);
  }

  @Test
  void avancar_a_temporada_so_depois_da_final_e_uma_vez_so_com_envelhecimento_contratos_e_dead_money() throws Exception {
    SeasonFixtures.Career c = career("Avanco", "PAV", 21L);
    assertProblem(call(mvc, post(c.base() + "/season/advance"), c.auth(), null), 422, "SEASON_NOT_FINISHED");
    UUID careerId = UUID.fromString(c.id());
    record Pick(UUID young, int youngAge, int youngSpeed, UUID old, int oldAge, int oldSpeed, UUID expiring, UUID lasting, int lastingExperience, UUID cut) {}
    Pick pick = tx.execute(st -> {
      CareerSave save = saves.findById(careerId).orElseThrow();
      List<Player> roster = players.findByClubIdOrderByOverallDesc(save.getClub().getId());
      Player young = roster.stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getAge() <= 23 && p.getPotential() >= p.getOverall() + 6)
          .min(Comparator.comparing(Player::getName)).orElseThrow();
      Player old = roster.stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getAge() >= 32 && !specialist(p))
          .min(Comparator.comparing(Player::getName)).orElseThrow();
      Player expiring = roster.stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getContract().endSeason() == 2026)
          .min(Comparator.comparing(Player::getName)).orElseThrow();
      Player lasting = roster.stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE && p.getContract().endSeason() >= 2028 && p != young && p != old)
          .min(Comparator.comparing(Player::getName)).orElseThrow();
      Player cut = roster.stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE && p != young && p != old && p != expiring && p != lasting)
          .max(Comparator.comparingLong(p -> p.getContract().deadMoneySplit(p.getContract().yearIndexOf(2026), 5, true).nextSeason())).orElseThrow();
      return new Pick(young.getId(), young.getAge(), young.getAttributes().get(AttributeKey.SPEED), old.getId(), old.getAge(),
          old.getAttributes().get(AttributeKey.SPEED), expiring.getId(), lasting.getId(), lasting.getExperienceYears(), cut.getId());
    });
    Map<String, Object> released = ok(call(mvc, post(c.base() + "/players/" + pick.cut() + "/release"), c.auth(), null));
    long nextSeason = ((Number) released.get("deadMoneyNextSeason")).longValue();
    assertThat(nextSeason).as("dead money jogado para a temporada seguinte (regra do 1º de junho)").isPositive();

    SeasonFixtures.playToEnd(mvc, c);
    Map<String, Object> summary = read(c, "/season/summary");
    assertThat(l(m(summary.get("offseason")).get("expiringContracts"))).anySatisfy(e -> assertThat(e).containsEntry("playerId", pick.expiring().toString()));
    String seasonId = (String) summary.get("seasonId");

    Map<String, Object> adv = ok(call(mvc, post(c.base() + "/season/advance"), c.auth(), Map.of("seasonId", seasonId)));
    assertThat(adv).containsEntry("previousSeasonId", seasonId).containsEntry("seasonNumber", 2).containsEntry("seasonYear", 2027)
        .containsEntry("phase", "FREE_AGENCY").containsEntry("currentWeek", 1);
    assertThat(((Number) adv.get("deadMoneyCarried")).longValue()).isEqualTo(nextSeason);
    assertThat(n(adv.get("expiredContracts"))).isPositive();
    assertThat(n(adv.get("aiRenewals"))).as("as franquias da IA renovam antes da free agency, dentro do teto (D-138)").isPositive();
    assertThat(l(adv.get("userExpiredContracts"))).anySatisfy(e -> assertThat(e).containsEntry("playerId", pick.expiring().toString()));
    assertThat(l(adv.get("steps"))).extracting(s -> s.get("phase") + ":" + s.get("status") + ":" + s.get("handledBy"))
        .containsExactly("RENEWALS:DONE:SEASON_ADVANCE", "FREE_AGENCY:OPEN:FREE_AGENCY", "DRAFT:SKIPPED:BLOCK_7", "PRESEASON:DONE:SEASON_ADVANCE");
    // idempotência: a mesma temporada não avança de novo (409) e a nova ainda não terminou (422)
    assertProblem(call(mvc, post(c.base() + "/season/advance"), c.auth(), Map.of("seasonId", seasonId)), 409, "SEASON_ALREADY_ADVANCED");
    assertProblem(call(mvc, post(c.base() + "/season/advance"), c.auth(), null), 422, "SEASON_NOT_FINISHED");
    // o dead money que a dispensa jogou para a temporada seguinte agora conta na temporada atual (D-79)
    Map<String, Object> cap = m(read(c, "/roster").get("cap"));
    assertThat(((Number) cap.get("deadMoney")).longValue()).isEqualTo(nextSeason);
    assertThat(((Number) cap.get("deadMoneyNextSeason")).longValue()).isZero();

    tx.executeWithoutResult(st -> {
      CareerSave save = saves.findById(careerId).orElseThrow();
      assertThat(save.getSeason().getNumber()).isEqualTo(2);
      assertThat(save.getSeason().getYear()).isEqualTo(2027);
      assertThat(save.getCurrentWeek()).isEqualTo(1);
      assertThat(fixtures.findBySeasonId(save.getSeason().getId())).hasSize(16).allSatisfy(f -> assertThat(f.isFinished()).isFalse());
      Player expiring = players.findById(pick.expiring()).orElseThrow();
      assertThat(expiring.getClub()).isNull();
      assertThat(expiring.getRosterStatus()).isEqualTo(RosterStatus.FREE_AGENT);
      assertThat(moves.findByCareerIdAndPlayerIdOrderByCreatedAtAsc(careerId, pick.expiring())).extracting(RosterMove::getKind)
          .containsExactly(RosterMoveKind.CONTRACT_EXPIRED);
      Player lasting = players.findById(pick.lasting()).orElseThrow();
      assertThat(lasting.getClub().getId()).isEqualTo(save.getClub().getId());
      assertThat(lasting.getExperienceYears()).isEqualTo(pick.lastingExperience() + 1);
      Player young = players.findById(pick.young()).orElseThrow(), old = players.findById(pick.old()).orElseThrow();
      assertThat(young.getAge()).isEqualTo(pick.youngAge() + 1);
      assertThat(young.getAttributes().get(AttributeKey.SPEED)).isGreaterThan(pick.youngSpeed());
      assertThat(old.getAge()).isEqualTo(pick.oldAge() + 1);
      assertThat(old.getAttributes().get(AttributeKey.SPEED)).isLessThan(pick.oldSpeed());
      assertAiRenewals(save, careerId, n(adv.get("aiRenewals")), n(adv.get("expiredContracts")));
    });
    assertThat(read(c, "/calendar")).containsEntry("phase", "FREE_AGENCY");
    // a free agency fecha (bloco 7a): a temporada passa à regular e a IA fecha os 53
    ok(call(mvc, post(c.base() + "/free-agency/advance"), c.auth(), Map.of("mode", "CLOSE")));
    tx.executeWithoutResult(st -> {
      CareerSave save = saves.findById(careerId).orElseThrow();
      for (Club club : clubs.findByWorldId(save.getWorld().getId())) {
        if (club.getId().equals(save.getClub().getId())) continue;
        assertThat(players.findByClubIdOrderByOverallDesc(club.getId()).stream().filter(p -> p.getRosterStatus().countsTowardRoster()).count())
            .as("53 de %s depois da free agency", club.getShortName()).isEqualTo(53);
      }
    });
    Map<String, Object> calendar = read(c, "/calendar");
    assertThat(calendar).containsEntry("seasonYear", 2027).containsEntry("currentWeek", 1).containsEntry("phase", "REGULAR");
    assertThat(l(read(c, "/trophies").get("seasons"))).hasSize(2);
    assertThat(m(read(c, "").get("record"))).containsEntry("summary", "0-0");
  }

  /**
   * Q-42/D-120, bloco 7a (D-138): cada franquia da IA renova, entre os próprios contratos vencidos, o que cabe no teto da temporada nova
   * deixando a reserva das vagas que faltam; quem não renova vai para a free agency. Os contratos renovados começam em 2027 (o pedido do
   * jogador: anos pela idade, luvas pelo overall), os números da resposta batem com o banco e a franquia do usuário não renova sozinha.
   */
  private void assertAiRenewals(CareerSave save, UUID careerId, int aiRenewals, int expiredContracts) {
    UUID userClub = save.getClub().getId();
    List<RosterMove> expiredMoves = moves.findByCareerIdOrderByCreatedAtAsc(careerId).stream()
        .filter(mv -> mv.getKind() == RosterMoveKind.CONTRACT_EXPIRED).toList();
    assertThat(expiredMoves).hasSize(expiredContracts);
    Map<UUID, List<Player>> leftovers = expiredMoves.stream()
        .collect(Collectors.groupingBy(mv -> mv.getClub().getId(), Collectors.mapping(RosterMove::getPlayer, Collectors.toList())));
    List<Club> aiClubs = clubs.findByWorldId(save.getWorld().getId()).stream().filter(cl -> !cl.getId().equals(userClub)).toList();
    assertThat(aiClubs).hasSize(31);
    long salaryCap = save.getLeague().getSalaryCap();
    int renewedInDb = 0;
    for (Club club : aiClubs) {
      List<Player> roster = players.findByClubIdOrderByOverallDesc(club.getId());
      long counted = roster.stream().filter(p -> p.getRosterStatus().countsTowardRoster()).count();
      long ps = roster.stream().filter(p -> p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD).count();
      assertThat(counted).as("53 de %s", club.getShortName()).isLessThanOrEqualTo(53);
      assertThat(ps).as("practice squad de %s", club.getShortName()).isLessThanOrEqualTo(16);
      long veterans = roster.stream().filter(p -> p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD)
          .filter(p -> p.getExperienceYears() > RosterRules.PRACTICE_SQUAD_MAX_EXPERIENCE).count();
      assertThat(veterans).isLessThanOrEqualTo(RosterRules.PRACTICE_SQUAD_VETERAN_SLOTS);
      long committed = roster.stream().filter(p -> p.getRosterStatus().requiresClub()).mapToLong(p -> p.getContract().capHit(p.getContract().yearIndexOf(2027), 5)).sum();
      assertThat(committed).as("comprometido de %s dentro do teto", club.getShortName()).isLessThanOrEqualTo(salaryCap);
      renewedInDb += (int) roster.stream().filter(p -> p.getContract().getStartSeason() == 2027).count();
      assertThat(leftovers.getOrDefault(club.getId(), List.of())).allSatisfy(p -> assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.FREE_AGENT));
    }
    assertThat(renewedInDb).isEqualTo(aiRenewals);
    // a franquia do usuário não renova sozinha: nenhum contrato novo e os vencidos dela viram agentes livres
    assertThat(players.findByClubIdOrderByOverallDesc(userClub)).noneMatch(p -> p.getContract().getStartSeason() == 2027);
    assertThat(leftovers.get(userClub)).isNotEmpty();
  }

  private static boolean specialist(Player p) { return p.getPosition() == Position.K || p.getPosition() == Position.P || p.getPosition() == Position.LS; }

  @Test
  void outro_usuario_recebe_403_auditado_nas_rotas_da_temporada() throws Exception {
    SeasonFixtures.Career c = career("Bola", "SLU", 3L);
    String intruder = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    for (String path : List.of("/standings", "/weeks/1/news", "/playoffs", "/playoffs/bracket", "/trophies", "/season/summary", "/morale", "/analysis")) {
      assertProblem(call(mvc, get(c.base() + path).with(r -> { r.setRemoteAddr("203.0.113.61"); return r; }), intruder, null), 403, "FORBIDDEN");
    }
    assertProblem(call(mvc, post(c.base() + "/season/advance"), intruder, null), 403, "FORBIDDEN");
    assertProblem(call(mvc, post(c.base() + "/analysis/refresh").with(r -> { r.setRemoteAddr("203.0.113.62"); return r; }), intruder, null), 403, "FORBIDDEN");
    List<AuditLog> denied = auditLog.findByResourceAndResourceIdOrderByTimestampDesc("CareerSave", c.id(), PageRequest.of(0, 50)).getContent();
    assertThat(denied.stream().filter(a -> AuditActions.ACCESS_DENIED.equals(a.getAction())).collect(Collectors.toList())).hasSizeGreaterThanOrEqualTo(10);
  }

  @Test
  void moral_do_elenco_reage_ao_resultado_e_ao_papel_no_depth_chart() throws Exception {
    SeasonFixtures.Career c = career("Moral", "MAZ", 13L);
    Map<String, Object> before = read(c, "/morale");
    assertThat(before).containsEntry("throughWeek", 0).containsEntry("trend", "STABLE");
    Map<String, Object> board = SeasonFixtures.playWeek(mvc, c);
    Map<String, Object> mine = l(board.get("games")).stream().map(g -> m(g.get("fixture"))).filter(f -> (Boolean) f.get("userGame")).findFirst().orElseThrow();
    boolean home = mine.get("homeClubId").equals(read(c, "").get("clubId"));
    Map<String, Object> fx = l(l(read(c, "/calendar?week=1").get("weeks")).get(0).get("fixtures")).stream().filter(f -> (Boolean) f.get("userGame"))
        .findFirst().orElseThrow();
    int us = n(home ? fx.get("homeScore") : fx.get("awayScore")), them = n(home ? fx.get("awayScore") : fx.get("homeScore"));
    Map<String, Object> after = read(c, "/morale");
    assertThat(after).containsEntry("throughWeek", 1);
    assertThat(after).isEqualTo(read(c, "/morale"));
    List<Map<String, Object>> rows = l(after.get("players"));
    assertThat(rows).allSatisfy(r -> {
      double result = ((Number) m(r.get("factors")).get("RESULT")).doubleValue();
      if (us > them) assertThat(result).isPositive();
      if (us < them) assertThat(result).isNegative();
    });
    assertThat(rows).anySatisfy(r -> assertThat(r).containsEntry("position", "QB").containsEntry("role", "STARTER").containsEntry("expectedSnapShare", 0.95));
    assertThat(rows).anySatisfy(r -> assertThat(r).containsEntry("rosterStatus", "PRACTICE_SQUAD").containsEntry("role", "RESERVE"));
    assertThat(rows).anySatisfy(r -> assertThat(r).containsEntry("role", "SPECIALIST").doesNotContainKey("expectedSnapShare"));
    assertThat(n(after.get("average"))).isBetween(0, 100);
  }

  @Test
  void analista_gera_relatorio_por_regras_e_respeita_o_limite_de_pedidos() throws Exception {
    SeasonFixtures.Career c = career("Analista", "BSE", 17L);
    java.util.function.UnaryOperator<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> ip =
        b -> b.with(r -> { r.setRemoteAddr("203.0.113.71"); return r; });
    assertProblem(call(mvc, ip.apply(get(c.base() + "/analysis")), c.auth(), null), 404, "ANALYSIS_NOT_FOUND");
    Map<String, Object> report = ok(call(mvc, ip.apply(post(c.base() + "/analysis/refresh")), c.auth(), null));
    assertThat(report).containsEntry("source", "RULES").containsEntry("week", 1).containsEntry("summaryCode",
        "NEXT_GAME_" + l(report.get("upcoming")).get(0).get("verdict"));
    assertThat(l(report.get("units"))).extracting(u -> u.get("unit")).containsExactly("QB", "OFFENSIVE_LINE", "RUNNING_BACK", "RECEIVERS",
        "DEFENSIVE_LINE", "LINEBACKERS", "SECONDARY", "SPECIAL_TEAMS");
    assertThat(l(report.get("upcoming"))).singleElement().satisfies(u -> assertThat(l(u.get("matchups"))).hasSize(5));
    assertThat((String) report.get("summary")).isNotBlank();
    Map<String, Object> latest = ok(call(mvc, ip.apply(get(c.base() + "/analysis")), c.auth(), null));
    assertThat(latest).containsEntry("id", report.get("id"));
    assertThat(m(read(c, "/dashboard").get("analysis"))).containsEntry("id", report.get("id"));
    // o limite do basquete: 6 pedidos por minuto por IP nas rotas /analysis (3 já foram)
    for (int i = 0; i < 3; i++) ok(call(mvc, ip.apply(get(c.base() + "/analysis")), c.auth(), null));
    assertProblem(call(mvc, ip.apply(post(c.base() + "/analysis/refresh")), c.auth(), null), 429, "RATE_LIMITED");
  }

  @Test
  void classificacao_e_noticias_da_semana_na_liga_padrao() throws Exception {
    String auth = TestSessions.bearer(users, sessions, tokens, UserRole.PLAYER);
    MatchFixtures.level(mvc, auth, "NONE");
    UUID world = seeder.seedIfMissing().getId();
    Map<String, Object> created = CareerFixtures.create(mvc, auth, CareerFixtures.body("Tabela " + UUID.randomUUID().toString().substring(0, 6), null,
        CareerFixtures.club(mvc, auth, world, "NES"), 31L));
    SeasonFixtures.Career c = new SeasonFixtures.Career(auth, CareerFixtures.id(created), "/api/v1/careers/" + CareerFixtures.id(created), world);
    SeasonFixtures.playWeek(mvc, c);
    Map<String, Object> st = read(c, "/standings");
    assertThat(st).containsEntry("official", false).containsEntry("throughWeek", 1).containsEntry("regularSeasonWeeks", 18);
    assertThat((List<?>) st.get("divisionTiebreakers")).first().isEqualTo("HEAD_TO_HEAD");
    List<Map<String, Object>> confs = l(st.get("conferences"));
    assertThat(confs).hasSize(2).allSatisfy(conf -> {
      assertThat(l(conf.get("divisions"))).hasSize(4).allSatisfy(d -> assertThat(l(d.get("rows"))).hasSize(4));
      assertThat(l(conf.get("rows"))).hasSize(16).extracting(r -> r.get("rank")).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 16).boxed().toList());
      assertThat(l(conf.get("rows")).subList(0, 7)).extracting(r -> r.get("seed")).containsExactly(1, 2, 3, 4, 5, 6, 7);
    });
    long played = confs.stream().flatMap(conf -> l(conf.get("rows")).stream()).filter(r -> n(m(r.get("record")).get("wins")) + n(m(r.get("record")).get("losses"))
        + n(m(r.get("record")).get("ties")) == 1).count();
    assertThat(played).as("na semana 1 ninguém folga (as folgas vão da semana 5 à 14)").isEqualTo(32);
    // a tabela da divisão na central é a mesma do RF16
    String clubId = (String) read(c, "").get("clubId");
    List<Map<String, Object>> division = confs.stream().flatMap(conf -> l(conf.get("divisions")).stream())
        .filter(d -> l(d.get("rows")).stream().anyMatch(r -> clubId.equals(m(r.get("club")).get("id")))).findFirst().map(d -> l(d.get("rows"))).orElseThrow();
    assertThat(l(read(c, "/dashboard").get("division"))).extracting(r -> m(r.get("club")).get("id"))
        .containsExactlyElementsOf(division.stream().map(r -> m(r.get("club")).get("id")).toList());
    Map<String, Object> news = read(c, "/weeks/1/news");
    assertThat(news).containsEntry("week", 1).containsEntry("weekKind", "REGULAR").containsEntry("completed", true);
    assertThat(l(news.get("items")).get(0)).satisfies(i -> {
      assertThat((String) i.get("kind")).startsWith("USER_");
      assertThat(i).containsEntry("clubId", clubId);
      assertThat((String) i.get("headline")).isNotBlank();
    });
    assertThat(read(c, "/weeks/1/news")).isEqualTo(news);
    Map<String, Object> en = ok(mvc.perform(get(c.base() + "/weeks/1/news").header("Authorization", auth).header("Accept-Language", "en-US")).andReturn());
    assertThat(l(en.get("items")).get(0).get("id")).isEqualTo(l(news.get("items")).get(0).get("id"));
    assertThat(l(en.get("items")).get(0).get("headline")).isNotEqualTo(l(news.get("items")).get(0).get("headline"));
    assertThat(l(read(c, "/dashboard").get("news"))).hasSizeLessThanOrEqualTo(3).first().satisfies(i -> assertThat(m(i)).containsEntry("id", l(news.get("items")).get(0).get("id")));
    assertProblem(call(mvc, get(c.base() + "/weeks/19/news"), auth, null), 400, "INVALID_WEEK");
  }
}
