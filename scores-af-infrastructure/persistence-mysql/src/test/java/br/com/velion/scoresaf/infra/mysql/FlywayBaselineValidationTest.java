package br.com.velion.scoresaf.infra.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.application.seed.DefaultWorld;
import br.com.velion.scoresaf.application.seed.DefaultWorldFactory;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.EnumSet;
import br.com.velion.scoresaf.domain.account.UserSettings;
import br.com.velion.scoresaf.infra.mysql.repository.UserAccountJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.UserSettingsJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Executa as migrations Flyway ({@code V1__plataforma.sql}, escrita para MySQL 8) em H2 modo MySQL e sobe o Hibernate com
 * {@code ddl-auto=validate}: prova que o schema versionado (V1 plataforma + V2 domínio AF) é equivalente às entidades JPA e que a V2
 * aceita o mundo semente inteiro (a semente é por código, D-23).
 */
@SpringBootTest(classes = PersistenceTestApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:scoresaf_flyway;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.dialect=br.com.velion.scoresaf.infra.mysql.TestH2Dialect"})
@ActiveProfiles("test")
@Import({CatalogGraph.class, CareerGraph.class, MatchGraph.class, SeasonGraph.class})
class FlywayBaselineValidationTest {

  @Autowired UserAccountJpaRepository users;
  @Autowired UserSettingsJpaRepository settings;
  @Autowired CatalogGraph catalog;
  @Autowired CareerGraph career;
  @Autowired MatchGraph match;
  @Autowired SeasonGraph season;

  @Test
  void deve_validar_entidades_contra_schema_do_flyway_e_gravar_pela_v1() {
    UserAccount u = new UserAccount(); u.setEmail("flyway@example.com"); u.setDisplayName("Flyway"); u.setPasswordHash("x");
    users.saveAndFlush(u);
    UserSettings s = new UserSettings(); s.setUser(u);
    settings.saveAndFlush(s);
    assertThat(settings.findByUserId(u.getId())).get().satisfies(x -> {
      assertThat(x.getPlayCallingLevel()).isEqualTo(PlayCallingLevel.KEY_MOMENTS);
      assertThat(x.getQuarterDurationMinutes()).isEqualTo(UserSettings.DEFAULT_QUARTER_MINUTES);
    });
  }

  /** A V2 aceita o mundo semente inteiro (CHECKs de posição, situação, camisa e capacidade; FKs) e devolve o que foi gravado. */
  @Test
  void mundo_semente_grava_pela_v2_e_rele() {
    DefaultWorld w = new DefaultWorldFactory().build(DefaultWorldFactory.DEFAULT_SEED + 1);
    catalog.persist(w);
    Player p = w.players().get(100);
    assertThat(catalog.players.findById(p.getId())).get().satisfies(x -> {
      assertThat(x.getAttributes().asMap()).isEqualTo(p.getAttributes().asMap());
      assertThat(x.getContract().getBaseSalaryByYear()).isEqualTo(p.getContract().getBaseSalaryByYear());
    });
    assertThat(catalog.players.countByWorldId(w.world().getId())).isEqualTo(32 * 69);
    assertThat(catalog.players.countByClubIdAndRosterStatusIn(p.getClub().getId(), EnumSet.of(RosterStatus.PRACTICE_SQUAD))).isEqualTo(16);
    assertThat(catalog.leagues.findById(w.leagues().get(0).getId())).get().satisfies(l -> assertThat(l.getRules().getPracticeSquadMax()).isEqualTo(16));
  }

  /** A V3 aceita a carreira inteira (CHECKs de slot, situação, horário, vaga e profundidade; FKs) e devolve o que foi gravado. */
  @Test
  void carreira_grava_pela_v3_e_rele() {
    DefaultWorld w = new DefaultWorldFactory().build(DefaultWorldFactory.DEFAULT_SEED + 2);
    catalog.persist(w);
    CareerGraph.Saved saved = career.persist(w, "v3-" + java.util.UUID.randomUUID() + "@example.com");
    assertThat(career.saves.findById(saved.career().getId())).get().satisfies(c -> {
      assertThat(c.getSlot()).isEqualTo(1);
      assertThat(c.getSeed()).isEqualTo(42L);
    });
    assertThat(career.fixtures.countBySeasonId(saved.season().getId())).isEqualTo(1);
    assertThat(career.slots.findByDepthChartId(saved.chart().getId())).hasSize(1);
    assertThat(career.moves.findByCareerIdOrderByCreatedAtAsc(saved.career().getId())).singleElement()
        .satisfies(m -> assertThat(m.getToStatus()).isEqualTo(RosterStatus.WAIVED));
    assertThat(catalog.players.findById(saved.slot().getPlayer().getId())).get().satisfies(p -> assertThat(p.getInjuryWeeksOut()).isZero());
  }

  /** A V4 aceita a partida inteira (CHECKs de situação, nível, faixa do plano e do peso; FKs) e devolve o que foi gravado. */
  @Test
  void partida_grava_pela_v4_e_rele() {
    DefaultWorld w = new DefaultWorldFactory().build(DefaultWorldFactory.DEFAULT_SEED + 3);
    catalog.persist(w);
    CareerGraph.Saved saved = career.persist(w, "v4-" + java.util.UUID.randomUUID() + "@example.com");
    MatchGraph.Saved m = match.persist(saved, saved.slot().getPlayer());
    assertThat(match.plans.findByCareerIdAndClubId(saved.career().getId(), saved.fixture().getHomeClub().getId())).get()
        .satisfies(g -> assertThat(g.getPassRatio()).isEqualTo(62));
    assertThat(match.weights.findByPlaybookId(m.playbook().getId())).hasSize(2);
    assertThat(match.sessions.findByFixtureId(saved.fixture().getId())).get().satisfies(s -> {
      assertThat(s.getStatus()).isEqualTo(br.com.velion.scoresaf.domain.match.MatchSessionStatus.AWAITING_DECISION);
      assertThat(s.getDecisionId()).isEqualTo("d-81");
    });
    assertThat(match.reports.findByFixtureId(saved.fixture().getId())).get().satisfies(r -> assertThat(r.getPlayCount()).isEqualTo(152));
    assertThat(match.stats.findByFixtureId(saved.fixture().getId())).singleElement().satisfies(l -> assertThat(l.getPassYards()).isEqualTo(254));
  }

  /**
   * A V5 (respostas Q-22..Q-28) aceita o jogador exposto a waivers rumo ao practice squad, o movimento WAIVER_CLEARED e o dead money
   * da temporada seguinte (regra do 1º de junho) e devolve o que foi gravado.
   */
  @Test
  void respostas_q22_q28_gravam_pela_v5_e_releem() {
    DefaultWorld w = new DefaultWorldFactory().build(DefaultWorldFactory.DEFAULT_SEED + 4);
    catalog.persist(w);
    CareerGraph.Saved saved = career.persist(w, "v5-" + java.util.UUID.randomUUID() + "@example.com");
    Player p = saved.move().getPlayer();
    p.setRosterStatus(RosterStatus.PRACTICE_SQUAD_WAIVERS);
    catalog.players.saveAndFlush(p);
    RosterMove release = career.moves.findById(saved.move().getId()).orElseThrow();
    release.setDeadMoneyNextSeason(3_000_00L);
    career.moves.saveAndFlush(release);
    RosterMove m = new RosterMove();
    m.setCareer(saved.career()); m.setSeason(saved.season()); m.setPlayer(p); m.setClub(p.getClub()); m.setWeek(1); m.setKind(RosterMoveKind.WAIVER_CLEARED);
    m.setFromStatus(RosterStatus.PRACTICE_SQUAD_WAIVERS); m.setToStatus(RosterStatus.PRACTICE_SQUAD);
    career.moves.saveAndFlush(m);
    assertThat(catalog.players.findById(p.getId())).get().satisfies(x -> assertThat(x.getRosterStatus()).isEqualTo(RosterStatus.PRACTICE_SQUAD_WAIVERS));
    assertThat(career.moves.findByCareerIdOrderByCreatedAtAsc(saved.career().getId())).extracting(RosterMove::getKind)
        .containsExactly(RosterMoveKind.RELEASE, RosterMoveKind.WAIVER_CLEARED);
    assertThat(career.moves.findById(release.getId())).get().satisfies(x -> assertThat(x.getDeadMoneyNextSeason()).isEqualTo(3_000_00L));
  }

  /**
   * A V6 (temporada, bloco 6c) aceita a temporada encerrada (campeã, vice, fim), a semente de playoff, o prêmio, o relatório do analista
   * e o movimento CONTRACT_EXPIRED, e devolve o que foi gravado.
   */
  @Test
  void temporada_grava_pela_v6_e_rele() {
    DefaultWorld w = new DefaultWorldFactory().build(DefaultWorldFactory.DEFAULT_SEED + 5);
    catalog.persist(w);
    CareerGraph.Saved saved = career.persist(w, "v6-" + java.util.UUID.randomUUID() + "@example.com");
    SeasonGraph.Saved s = season.persist(saved, saved.slot().getPlayer());
    assertThat(season.seasons.findById(saved.season().getId())).get().satisfies(x -> {
      assertThat(x.isCompleted()).isTrue();
      assertThat(x.getChampionClub().getId()).isEqualTo(saved.fixture().getHomeClub().getId());
      assertThat(x.getRunnerUpClub().getId()).isEqualTo(saved.fixture().getAwayClub().getId());
    });
    assertThat(season.seeds.findBySeasonIdOrderBySeedAsc(saved.season().getId())).singleElement().satisfies(x -> {
      assertThat(x.isDivisionChampion()).isTrue();
      assertThat(x.getTiebreak()).isEqualTo("HEAD_TO_HEAD");
    });
    assertThat(season.awards.findBySeasonId(saved.season().getId())).singleElement().satisfies(x -> assertThat(x.getScore()).isEqualTo(412.5));
    assertThat(season.reports.findFirstByCareerIdOrderByCreatedAtDesc(saved.career().getId())).get().satisfies(x -> assertThat(x.getWeek()).isEqualTo(3));
    assertThat(season.moves.findById(s.expired().getId())).get().satisfies(x -> assertThat(x.getKind()).isEqualTo(RosterMoveKind.CONTRACT_EXPIRED));
  }

  /** A V7 (D-123) aceita a semana da rodada PRELIMINARY, a primeira das chaves de 5 rodadas, e devolve o que foi gravado. */
  @Test
  void rodada_preliminar_grava_pela_v7_e_rele() {
    DefaultWorld w = new DefaultWorldFactory().build(DefaultWorldFactory.DEFAULT_SEED + 6);
    catalog.persist(w);
    CareerGraph.Saved saved = career.persist(w, "v7-" + java.util.UUID.randomUUID() + "@example.com");
    br.com.velion.scoresaf.domain.season.Week wk = new br.com.velion.scoresaf.domain.season.Week();
    wk.setSeason(saved.season()); wk.setNumber(19); wk.setKind(br.com.velion.scoresaf.domain.shared.WeekKind.PRELIMINARY);
    wk.setStartsOn(java.time.LocalDate.of(2027, 1, 7)); wk.setStatus(br.com.velion.scoresaf.domain.shared.WeekStatus.CURRENT);
    career.weeks.saveAndFlush(wk);
    assertThat(career.weeks.findById(wk.getId())).get()
        .satisfies(x -> assertThat(x.getKind()).isEqualTo(br.com.velion.scoresaf.domain.shared.WeekKind.PRELIMINARY));
  }
}
