package br.com.velion.scoresaf.application.usecase.rf46;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConsentRequiredException;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.domain.account.ConsentPurpose;
import br.com.velion.scoresaf.domain.account.ConsentSource;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.common.AuditContext;
import br.com.velion.scoresaf.domain.common.EncryptionKeyHolder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrivacyUseCasesTest {
  private final UserAccountRepository users = mock(UserAccountRepository.class);
  private final AuditService audit = mock(AuditService.class);
  private final ConsentRevocationHandler biometric = mock(ConsentRevocationHandler.class);
  private InMemoryConsentRecords records;
  private PrivacyUseCases useCase;
  private UserAccount user;

  @BeforeAll static void key() { EncryptionKeyHolder.setKeyBase64(""); }

  @BeforeEach void setup() {
    records = new InMemoryConsentRecords();
    when(biometric.purpose()).thenReturn(ConsentPurpose.BIOMETRIC_AVATAR);
    useCase = new PrivacyUseCases(users, records.repo, audit, List.of(biometric));
    user = new UserAccount(); user.setEmail("ana@scores.app"); user.setDisplayName("Ana"); user.setPasswordHash("x");
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(users.save(any())).thenAnswer(i -> i.getArgument(0));
    AuditContext.setRequest("189.4.20.7", "Mozilla/5.0 (Windows NT 10.0) Chrome/140");
  }

  @AfterEach void clear() { AuditContext.clear(); }

  @Test void privacidade_por_padrao_nada_consentido_e_perfil_somente_eu() {
    PrivacyUseCases.PrivacyView v = useCase.view(user.getId());
    assertThat(v.profileVisibility()).isEqualTo("ONLY_ME");
    assertThat(v.consents()).hasSize(ConsentPurpose.values().length).allMatch(s -> !s.granted());
    assertThat(v.consents()).filteredOn(s -> s.purpose().equals("BIOMETRIC_AVATAR")).singleElement()
        .satisfies(s -> { assertThat(s.sensitive()).isTrue(); assertThat(s.legalBasis()).isEqualTo("Art. 11, I"); });
  }

  @Test void conceder_grava_prova_com_versao_origem_e_hash_do_ip_e_e_idempotente() {
    useCase.set(user.getId(), ConsentPurpose.ANALYTICS, true, ConsentSource.SETTINGS);
    useCase.set(user.getId(), ConsentPurpose.ANALYTICS, true, ConsentSource.SETTINGS);
    assertThat(records.rows).hasSize(1).first().satisfies(r -> {
      assertThat(r.isGranted()).isTrue();
      assertThat(r.getTextVersion()).isEqualTo(ConsentPurpose.ANALYTICS.currentTextVersion());
      assertThat(r.getIpHash()).hasSize(64).doesNotContain("189.4");
      assertThat(r.getSource()).isEqualTo(ConsentSource.SETTINGS);
    });
    verify(audit, times(1)).ok(eq(AuditActions.CONSENT_GRANTED), eq("UserAccount"), eq(user.getId()), startsWith("ANALYTICS"));
  }

  @Test void revogar_custa_uma_chamada_e_dispara_a_cascata_biometrica() {
    useCase.set(user.getId(), ConsentPurpose.BIOMETRIC_AVATAR, true, ConsentSource.AVATAR_FLOW);
    verify(biometric, never()).onRevoked(any());
    useCase.update(user.getId(), new PrivacyUseCases.UpdateCommand(Map.of("BIOMETRIC_AVATAR", false), null, "SETTINGS"));
    verify(biometric).onRevoked(user.getId());
    assertThat(useCase.isGranted(user.getId(), ConsentPurpose.BIOMETRIC_AVATAR)).isFalse();
    assertThat(useCase.history(user.getId())).extracting(PrivacyUseCases.ConsentHistoryEntry::granted).containsExactly(false, true);
    assertThatThrownBy(() -> useCase.require(user.getId(), ConsentPurpose.BIOMETRIC_AVATAR)).isInstanceOf(ConsentRequiredException.class);
  }

  @Test void finalidade_sensivel_no_texto_antigo_conta_como_nao_dada_ate_o_reaceite() {
    useCase.set(user.getId(), ConsentPurpose.BIOMETRIC_AVATAR, true, ConsentSource.SETTINGS);
    useCase.set(user.getId(), ConsentPurpose.ANALYTICS, true, ConsentSource.SETTINGS);
    records.rows.forEach(r -> r.setTextVersion("2025-01-v0"));

    assertThatThrownBy(() -> useCase.require(user.getId(), ConsentPurpose.BIOMETRIC_AVATAR)).isInstanceOf(ConsentRequiredException.class);
    assertThat(useCase.isGranted(user.getId(), ConsentPurpose.ANALYTICS)).as("finalidade comum segue valendo").isTrue();
    assertThat(useCase.view(user.getId()).consents()).filteredOn(s -> s.purpose().equals("BIOMETRIC_AVATAR")).singleElement()
        .satisfies(s -> { assertThat(s.granted()).isTrue(); assertThat(s.outdated()).isTrue(); });

    // reaceite: grava um registro novo na versão vigente, sem disparar a cascata de revogação
    useCase.set(user.getId(), ConsentPurpose.BIOMETRIC_AVATAR, true, ConsentSource.AVATAR_FLOW);
    useCase.require(user.getId(), ConsentPurpose.BIOMETRIC_AVATAR);
    verify(biometric, never()).onRevoked(any());
  }

  @Test void marketing_espelha_o_campo_legado_e_visibilidade_e_validada() {
    useCase.update(user.getId(), new PrivacyUseCases.UpdateCommand(Map.of("MARKETING_EMAIL", true), "PUBLIC", null));
    assertThat(user.isMarketingConsent()).isTrue();
    assertThat(user.getProfileVisibility().name()).isEqualTo("PUBLIC");
    assertThatThrownBy(() -> useCase.update(user.getId(), new PrivacyUseCases.UpdateCommand(null, "EVERYONE", null)))
        .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("INVALID_PROFILE_VISIBILITY");
    assertThatThrownBy(() -> useCase.update(user.getId(), new PrivacyUseCases.UpdateCommand(Map.of("SELL_DATA", true), null, null)))
        .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("INVALID_CONSENT_PURPOSE");
  }
}
