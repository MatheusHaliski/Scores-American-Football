package br.com.velion.scoresaf.infra.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.account.ConsentPurpose;
import br.com.velion.scoresaf.domain.account.ConsentRecord;
import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserSession;
import br.com.velion.scoresaf.domain.account.UserSettings;
import br.com.velion.scoresaf.domain.common.Hashing;
import br.com.velion.scoresaf.infra.mysql.repository.ConsentRecordJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.UserAccountJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.UserSessionJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.UserSettingsJpaRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorld;
import br.com.velion.scoresaf.application.seed.DefaultWorldFactory;
import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Division;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.Uniform;
import br.com.velion.scoresaf.domain.catalog.UniformSlot;
import br.com.velion.scoresaf.domain.game.Ruleset;
import br.com.velion.scoresaf.domain.shared.OvertimeFormat;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Mapeamento JPA da plataforma sobre H2 (modo MySQL): cifra do e-mail com busca por hash, persist das entidades com UUID
 * atribuído pelo repositório base (D-9), família de sessão (D-25 do basquete) e as configurações do futebol americano (D-10).
 * Bloco 4: o catálogo do futebol americano (mundo, liga com regras embutidas, conferência, divisão, franquia, estádio, uniforme,
 * capacete, jogador com 49 subatributos e contrato com salários por ano) — o mundo semente inteiro é gravado e relido.
 */
@DataJpaTest
@Import(CatalogGraph.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EntityMappingDataJpaTest {

  @Autowired TestEntityManager em;
  @Autowired UserAccountJpaRepository users;
  @Autowired UserSessionJpaRepository sessions;
  @Autowired UserSettingsJpaRepository settings;
  @Autowired ConsentRecordJpaRepository consents;
  @Autowired CatalogGraph catalog;

  @Test
  void deve_cifrar_email_e_buscar_por_hash_quando_persistir_user_account() {
    UserAccount u = new UserAccount();
    u.setEmail("  Ana.Silva@Example.com ");
    u.setDisplayName("Ana Silva");
    u.setPasswordHash("$argon2id$fake");
    users.saveAndFlush(u);
    em.clear();

    Object raw = em.getEntityManager().createNativeQuery("select email from user_account where id = ?1").setParameter(1, u.getId()).getSingleResult();
    assertThat(raw.toString()).isNotEqualTo("ana.silva@example.com").doesNotContain("example.com");

    UserAccount found = users.findByEmailHash(Hashing.emailHash("ana.silva@example.com")).orElseThrow();
    assertThat(found.getEmail()).isEqualTo("ana.silva@example.com");
    assertThat(found.getDisplayName()).isEqualTo("Ana Silva");
    assertThat(users.existsByEmailHash(Hashing.emailHash("outro@example.com"))).isFalse();
  }

  @Test
  void entidade_nova_com_uuid_atribuido_e_persistida_e_nao_mesclada() {
    UserAccount u = user("persist@example.com");
    UserAccount saved = users.save(u);
    assertThat(saved).as("persist devolve a própria instância (merge devolveria uma cópia)").isSameAs(u);
    assertThat(u.getCreatedAt()).as("@PrePersist roda na instância do chamador").isNotNull();
    em.flush();
    assertThat(u.isNew()).as("@PostPersist marca a instância depois do insert").isFalse();
    em.clear();
    UserAccount loaded = users.findById(u.getId()).orElseThrow();
    assertThat(loaded.isNew()).as("carregada do banco não é nova").isFalse();
    loaded.setDisplayName("Outro nome");
    users.saveAndFlush(loaded);
    em.clear();
    assertThat(users.findById(u.getId()).orElseThrow().getDisplayName()).isEqualTo("Outro nome");
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void familia_de_sessao_ativa_e_consultada_por_indice() {
    UserAccount u = users.save(user("sessao@example.com"));
    UserSession s = new UserSession();
    s.setUser(u);
    s.setRefreshTokenHash(Hashing.sha256Hex(UUID.randomUUID().toString()));
    s.setExpiresAt(Instant.now().plusSeconds(3600));
    s.setFamilyId(s.getId());
    sessions.saveAndFlush(s);
    em.clear();

    assertThat(sessions.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(s.family(), Instant.now())).isTrue();
    UserSession loaded = sessions.findByRefreshTokenHash(s.getRefreshTokenHash()).orElseThrow();
    loaded.setRevokedAt(Instant.now());
    sessions.saveAndFlush(loaded);
    assertThat(sessions.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(s.family(), Instant.now())).isFalse();
  }

  @Test
  void configuracoes_nascem_com_os_padroes_do_futebol_americano() {
    UserAccount u = users.save(user("config@example.com"));
    UserSettings s = new UserSettings();
    s.setUser(u);
    settings.saveAndFlush(s);
    em.clear();

    UserSettings loaded = settings.findByUserId(u.getId()).orElseThrow();
    assertThat(loaded.getQuarterDurationMinutes()).isEqualTo(UserSettings.DEFAULT_QUARTER_MINUTES);
    assertThat(loaded.getPlayCallingLevel()).isEqualTo(PlayCallingLevel.KEY_MOMENTS);
  }

  @Test
  void consentimentos_sao_lidos_do_mais_recente_para_o_mais_antigo() {
    UserAccount u = users.save(user("consent@example.com"));
    consents.save(consent(u, true, Instant.now().minusSeconds(60)));
    consents.save(consent(u, false, Instant.now()));
    em.flush(); em.clear();
    assertThat(consents.findFirstByUserIdAndPurposeOrderByRecordedAtDesc(u.getId(), ConsentPurpose.ANALYTICS)).get()
        .satisfies(r -> assertThat(r.isGranted()).isFalse());
    assertThat(consents.findByUserIdOrderByRecordedAtDesc(u.getId())).hasSize(2);
  }

  @Test
  void mundo_semente_inteiro_grava_e_rele_com_regras_atributos_e_contrato() {
    DefaultWorld w = new DefaultWorldFactory().build();
    catalog.persist(w);
    em.flush(); em.clear();

    assertThat(catalog.worlds.findFirstByKindAndOwnerUserIsNullOrderByCreatedAtAsc(WorldKind.TEMPLATE)).get()
        .satisfies(x -> assertThat(x.getSeed()).isEqualTo(DefaultWorldFactory.DEFAULT_SEED));
    League league = catalog.leagues.findByWorldIdOrderByTierAscNameAsc(w.world().getId()).get(0);
    assertThat(league.getRuleset()).isEqualTo(Ruleset.PRO);
    assertThat(league.getRules().getRosterMax()).isEqualTo(53);
    assertThat(league.getRules().getOvertimeFormat()).isEqualTo(OvertimeFormat.BOTH_POSSESS_THEN_SUDDEN_DEATH);
    assertThat(league.getRules().getHashFromSidelineFeet()).isEqualTo(70.75);
    List<Division> divs = catalog.divisions.findByConferenceLeagueIdOrderByConferenceSortOrderAscSortOrderAsc(league.getId());
    assertThat(divs).hasSize(8);
    assertThat(catalog.conferences.findByLeagueIdOrderBySortOrderAsc(league.getId())).hasSize(2);
    assertThat(catalog.clubs.countByLeagueId(league.getId())).isEqualTo(32);
    assertThat(catalog.clubs.countByDivisionId(divs.get(0).getId())).isEqualTo(4);

    Player original = w.players().get(7);
    Player loaded = catalog.players.findById(original.getId()).orElseThrow();
    assertThat(loaded.getAttributes().asMap()).isEqualTo(original.getAttributes().asMap());
    assertThat(loaded.getAttributes().get(AttributeKey.RELEASE)).isEqualTo(original.getAttributes().getRelease());
    assertThat(loaded.getContract().getBaseSalaryByYear()).isEqualTo(original.getContract().getBaseSalaryByYear());
    assertThat(loaded.getContract().capHitByYear()).isEqualTo(original.getContract().capHitByYear());
    assertThat(loaded.getPosition()).isEqualTo(original.getPosition());
    assertThat(loaded.getOverall()).isEqualTo(original.getOverall());

    Club club = catalog.clubs.findById(original.getClub().getId()).orElseThrow();
    assertThat(club.getStadium().getCapacity()).isBetween(60_000, 80_000);
    assertThat(catalog.sectors.findByStadiumId(club.getStadium().getId())).hasSize(5);
    assertThat(catalog.uniforms.findByClubId(club.getId())).hasSize(3);
    assertThat(catalog.helmets.findByClubId(club.getId())).isPresent();
    assertThat(catalog.players.countByClubIdAndRosterStatusIn(club.getId(), EnumSet.of(RosterStatus.ACTIVE, RosterStatus.INACTIVE))).isEqualTo(53);
    assertThat(catalog.players.countByClubIdAndRosterStatusIn(club.getId(), EnumSet.of(RosterStatus.PRACTICE_SQUAD))).isEqualTo(16);
    assertThat(catalog.players.existsByClubIdAndJerseyNumberAndIdNot(club.getId(), loaded.getJerseyNumber(), loaded.getId())).isFalse();
    assertThat(catalog.players.countByWorldId(w.world().getId())).isEqualTo(32 * 69);
  }

  @Test
  void dois_uniformes_no_mesmo_slot_sao_recusados_pelo_banco() {
    DefaultWorld w = new DefaultWorldFactory().build(7L);
    catalog.persist(w);
    em.flush();
    Uniform dup = new Uniform();
    dup.setClub(w.clubs().get(0));
    dup.setSlot(UniformSlot.HOME);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> { catalog.uniforms.save(dup); em.flush(); })
        .isInstanceOfAny(DataIntegrityViolationException.class, org.hibernate.exception.ConstraintViolationException.class,
            jakarta.persistence.PersistenceException.class);
  }

  // ---- helpers
  private static UserAccount user(String email) {
    UserAccount u = new UserAccount(); u.setEmail(email); u.setDisplayName("Test"); u.setPasswordHash("x");
    return u;
  }

  private static ConsentRecord consent(UserAccount u, boolean granted, Instant at) {
    ConsentRecord r = new ConsentRecord();
    r.setUser(u); r.setPurpose(ConsentPurpose.ANALYTICS); r.setGranted(granted); r.setTextVersion(ConsentPurpose.ANALYTICS.currentTextVersion());
    r.setRecordedAt(at);
    return r;
  }
}
