package br.com.velion.scoresaf.application.usecase.rf46;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.i18n.LocalizedMessages;
import br.com.velion.scoresaf.application.port.out.media.MediaStoragePort;
import br.com.velion.scoresaf.application.port.out.notify.EmailPort;
import br.com.velion.scoresaf.application.port.out.persistence.DataExportJobRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.domain.account.DataExportJob;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.common.EncryptionKeyHolder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

class DataExportUseCasesTest {
  private final UserAccountRepository users = mock(UserAccountRepository.class);
  private final DataExportJobRepository jobs = mock(DataExportJobRepository.class);
  private final AccountDataCollector collector = mock(AccountDataCollector.class);
  private final MediaStoragePort media = mock(MediaStoragePort.class);
  private final List<DataExportJob> saved = new ArrayList<>();
  private DataExportUseCases useCase; private UserAccount user;

  @BeforeAll static void key() { EncryptionKeyHolder.setKeyBase64(""); }

  @BeforeEach void setup() {
    useCase = new DataExportUseCases(users, jobs, collector, media, mock(EmailPort.class), new LocalizedMessages(new StaticMessageSource()), mock(AuditService.class));
    user = new UserAccount(); user.setEmail("ana@scores.app"); user.setDisplayName("Ana"); user.setPasswordHash("x");
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(jobs.save(any())).thenAnswer(i -> { DataExportJob j = i.getArgument(0); if (!saved.contains(j)) saved.add(j); return j; });
    when(jobs.findByUserIdOrderByRequestedAtDesc(user.getId())).thenAnswer(i -> List.copyOf(saved));
    when(jobs.findById(any())).thenAnswer(i -> saved.stream().filter(j -> j.getId().equals(i.getArgument(0))).findFirst());
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("schema", "scores-data-export/v2");
    data.put("careers", List.of(Map.of("name", "=HYPERLINK(\"http://evil\")", "status", "ACTIVE")));
    when(collector.collect(user)).thenReturn(data);
    when(media.get(anyString())).thenReturn(Optional.of("{}".getBytes()));
  }

  @Test void gera_json_e_csv_privados_por_24h_e_reaproveita_pedido_recente() {
    DataExportUseCases.ExportJobDto dto = useCase.request(user.getId());
    assertThat(dto.status()).isEqualTo("READY");
    assertThat(dto.expiresAt()).isAfter(Instant.now().plusSeconds(23 * 3600));
    assertThat(dto.jsonUrl()).endsWith("format=json");
    verify(media).put(eq(MediaStoragePort.EXPORTS + user.getId() + "/" + dto.id() + ".json"), any(), eq("application/json"));
    assertThat(MediaStoragePort.isPrivateKey(MediaStoragePort.EXPORTS + "x")).isTrue();
    assertThat(useCase.request(user.getId()).id()).isEqualTo(dto.id());
  }

  @Test void arquivo_expirado_nao_e_baixado() {
    DataExportUseCases.ExportJobDto dto = useCase.request(user.getId());
    saved.get(0).setExpiresAt(Instant.now().minusSeconds(1));
    assertThatThrownBy(() -> useCase.download(user.getId(), dto.id(), "json")).isInstanceOf(BusinessRuleException.class)
        .extracting("code").isEqualTo("EXPORT_EXPIRED");
  }

  /** A eliminação da conta (AccountErasureHandler) apaga os arquivos ainda dentro das 24 h; no basquete ninguém chamava isto. */
  @Test void eliminacao_da_conta_apaga_os_arquivos_de_exportacao() {
    DataExportUseCases.ExportJobDto dto = useCase.request(user.getId());
    AccountErasureHandler eraser = useCase;
    eraser.erase(user.getId());
    String base = MediaStoragePort.EXPORTS + user.getId() + "/" + dto.id();
    verify(media).delete(base + ".json");
    verify(media).delete(base + ".csv");
    assertThat(useCase.get(user.getId(), dto.id()).status()).isEqualTo("EXPIRED");
  }

  @Test void csv_neutraliza_injecao_de_formula() {
    String csv = AccountDataCollector.toCsv(Map.of("careers", List.of(Map.of("name", "=HYPERLINK(1)"))));
    assertThat(csv).contains("'=HYPERLINK(1)").doesNotContain(";=HYPERLINK");
  }
}
