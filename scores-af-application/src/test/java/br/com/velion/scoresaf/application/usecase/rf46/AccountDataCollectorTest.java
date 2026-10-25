package br.com.velion.scoresaf.application.usecase.rf46;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.velion.scoresaf.application.port.out.persistence.AuditLogRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ConsentRecordRepository;
import br.com.velion.scoresaf.application.port.out.persistence.NotificationPreferenceRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSettingsRepository;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.common.EncryptionKeyHolder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

/**
 * Exportação LGPD (RF46 CA07). No basquete o coletor lia carreiras, compras e o Digital Double direto; aqui cada módulo do jogo
 * entra por um {@link AccountDataContributor} (D-12). O teste prova que a seção de um contribuidor chega ao arquivo, ao lado das
 * seções da plataforma, e que o arquivo nunca traz o hash da senha.
 */
class AccountDataCollectorTest {
  @BeforeAll static void key() { EncryptionKeyHolder.setKeyBase64(""); }

  private static AccountDataCollector collector(List<AccountDataContributor> contributors) {
    AuditLogRepository logs = mock(AuditLogRepository.class);
    when(logs.findByActorOrderByTimestampDesc(any(), any())).thenReturn(Page.empty());
    return new AccountDataCollector(mock(UserSettingsRepository.class), mock(ConsentRecordRepository.class),
        mock(NotificationPreferenceRepository.class), mock(UserSessionRepository.class), logs, contributors);
  }

  private static UserAccount user() {
    UserAccount u = new UserAccount(); u.setEmail("ana@scoresaf.app"); u.setDisplayName("Ana"); u.setPasswordHash("h:" + UUID.randomUUID());
    return u;
  }

  @Test
  void exportacao_inclui_as_secoes_dos_contribuidores_do_jogo() {
    UserAccount u = user();
    List<Map<String, Object>> careers = List.of(AccountDataCollector.row("name", "Minha carreira", "status", "ACTIVE"));
    AccountDataContributor careersModule = (userId, out) -> { if (userId.equals(u.getId())) out.put("careers", careers); };

    Map<String, Object> out = collector(List.of(careersModule)).collect(u);

    assertThat(out).containsKeys("schema", "account", "consents", "notificationPreferences", "sessions", "activity", "careers");
    assertThat(out.get("careers")).isEqualTo(careers);
    assertThat(String.valueOf(out)).doesNotContain(u.getPasswordHash());
  }

  @Test
  void sem_contribuidores_so_vem_a_plataforma() {
    Map<String, Object> out = collector(null).collect(user());
    assertThat(out).containsKeys("account", "consents", "sessions", "activity").doesNotContainKeys("careers", "purchases", "customPlayer");
  }
}
