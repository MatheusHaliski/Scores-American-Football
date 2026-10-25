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
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Mapeamento JPA da plataforma sobre H2 (modo MySQL): cifra do e-mail com busca por hash, persist das entidades com UUID
 * atribuído pelo repositório base (D-9), família de sessão (D-25 do basquete) e as configurações do futebol americano (D-10).
 * Os testes das entidades do jogo (jogador, clube, carreira, partida) voltam com o domínio AF no bloco 4.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EntityMappingDataJpaTest {

  @Autowired TestEntityManager em;
  @Autowired UserAccountJpaRepository users;
  @Autowired UserSessionJpaRepository sessions;
  @Autowired UserSettingsJpaRepository settings;
  @Autowired ConsentRecordJpaRepository consents;

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
