package br.com.velion.scoresaf.application.usecase.rf46;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.AuthenticationFailedException;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.i18n.LocalizedMessages;
import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.port.out.notify.EmailPort;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserSession;
import br.com.velion.scoresaf.domain.account.UserStatus;
import br.com.velion.scoresaf.domain.common.EncryptionKeyHolder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import br.com.velion.scoresaf.application.security.PasswordAttemptGuard;
import br.com.velion.scoresaf.application.security.PasswordHasher;
import br.com.velion.scoresaf.application.security.TestRateLimiter;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountDeletionUseCasesTest {
  // senhas geradas na execução: nenhum segredo fixo em teste (docs/01 §6)
  private static final String SENHA = "T" + java.util.UUID.randomUUID();
  private static final String ERRADA = "E" + java.util.UUID.randomUUID();
  private final UserAccountRepository users = mock(UserAccountRepository.class);
  private final UserSessionRepository sessions = mock(UserSessionRepository.class);
  private final SessionCachePort cache = mock(SessionCachePort.class);
  private final EmailPort email = mock(EmailPort.class);
  private final AccountErasureHandler eraser = mock(AccountErasureHandler.class);
  private final PasswordEncoder encoder = new PasswordEncoder() {
    @Override public String encode(CharSequence raw) { return "h:" + raw; }
    @Override public boolean matches(CharSequence raw, String encoded) { return ("h:" + raw).equals(encoded); }
  };
  private AccountDeletionUseCases useCase; private UserAccount user; private UserSession session;

  @BeforeAll static void key() { EncryptionKeyHolder.setKeyBase64(""); }

  @BeforeEach void setup() {
    useCase = new AccountDeletionUseCases(users, sessions, cache, new PasswordAttemptGuard(new TestRateLimiter(), new PasswordHasher(encoder, 2, 10, 2000), br.com.velion.scoresaf.application.usecase.rf02.AuthPolicy.defaults()), email, new LocalizedMessages(new StaticMessageSource()), mock(AuditService.class), List.of(eraser));
    user = new UserAccount(); user.setEmail("ana@scores.app"); user.setDisplayName("Ana"); user.setPasswordHash("h:" + SENHA);
    session = new UserSession(); session.setUser(user); session.setRefreshTokenHash("abc");
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(users.save(any())).thenAnswer(i -> i.getArgument(0));
    when(sessions.findByUserIdAndRevokedAtIsNull(user.getId())).thenReturn(List.of(session));
  }

  @Test void exige_senha_e_palavra_de_confirmacao_em_qualquer_idioma() {
    assertThatThrownBy(() -> useCase.request(user.getId(), ERRADA, "EXCLUIR")).isInstanceOf(AuthenticationFailedException.class);
    assertThatThrownBy(() -> useCase.request(user.getId(), SENHA, "sim")).isInstanceOf(BusinessRuleException.class)
        .extracting("code").isEqualTo("DELETION_CONFIRMATION_MISMATCH");
    AccountDeletionUseCases.DeletionStatus s = useCase.request(user.getId(), SENHA, "eliminar");
    assertThat(s.status()).isEqualTo("PENDING_DELETION");
    assertThat(s.scheduledFor()).isCloseTo(Instant.now().plus(7, ChronoUnit.DAYS), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
    assertThat(session.getRevokedAt()).isNotNull();
    verify(email).send(anyString(), anyString(), anyString());
  }

  @Test void cancelar_dentro_do_prazo_reativa_a_conta() {
    useCase.request(user.getId(), SENHA, "DELETE");
    assertThat(useCase.cancel(user.getId()).status()).isEqualTo("ACTIVE");
    assertThat(user.getDeletionRequestedAt()).isNull();
    assertThatThrownBy(() -> useCase.cancel(user.getId())).isInstanceOf(BusinessRuleException.class);
  }

  @Test void prazo_vencido_anonimiza_de_forma_irreversivel_e_apaga_midia() {
    user.setStatus(UserStatus.PENDING_DELETION); user.setDeletionRequestedAt(Instant.now().minus(8, ChronoUnit.DAYS));
    when(users.findByStatusAndDeletionRequestedAtBefore(any(), any())).thenReturn(List.of(user));
    assertThat(useCase.eraseDue()).isEqualTo(1);
    assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(user.getEmail()).startsWith("deleted+").endsWith("@scoresaf.invalid");
    assertThat(user.getPasswordHash()).isEqualTo("!deleted");
    verify(eraser).erase(user.getId());
  }
}
