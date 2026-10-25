package br.com.velion.scoresaf.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.domain.account.AccountOrigin;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserRole;
import br.com.velion.scoresaf.domain.common.EncryptionKeyHolder;
import br.com.velion.scoresaf.domain.common.Hashing;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapTest {
  private final UserAccountRepository users = mock(UserAccountRepository.class);
  private final PasswordHasher hasher = new PasswordHasher(new PasswordEncoder() {
    @Override public String encode(CharSequence raw) { return "h:" + raw; }
    @Override public boolean matches(CharSequence raw, String encoded) { return ("h:" + raw).equals(encoded); }
  }, 2, 10, 2000);
  // senha gerada na execução (12+ caracteres): nenhum segredo fixo em teste (docs/01 §6)
  private static final String STRONG = "T" + java.util.UUID.randomUUID();

  @BeforeAll static void key() { EncryptionKeyHolder.setKeyBase64(""); }

  private AdminBootstrap boot(String email, String password, boolean promote) {
    return new AdminBootstrap(users, hasher, mock(AuditService.class), email, password, promote);
  }

  @Test
  void sem_email_nao_faz_nada() {
    assertThat(boot("", STRONG, false).bootstrap()).isEqualTo(AdminBootstrap.Outcome.SKIPPED_NO_EMAIL);
    verify(users, never()).save(any());
  }

  @Test
  void ja_existe_admin_nao_cria_outro() {
    when(users.existsByRole(UserRole.ADMIN)).thenReturn(true);
    assertThat(boot("op@scores.app", STRONG, false).bootstrap()).isEqualTo(AdminBootstrap.Outcome.SKIPPED_ADMIN_EXISTS);
    verify(users, never()).save(any());
  }

  @Test
  void conta_existente_so_vira_admin_com_a_flag_explicita() {
    UserAccount existing = new UserAccount();
    when(users.findByEmailHash(Hashing.sha256Hex("op@scores.app"))).thenReturn(Optional.of(existing));
    assertThat(boot("op@scores.app", STRONG, false).bootstrap()).isEqualTo(AdminBootstrap.Outcome.SKIPPED_EXISTING_ACCOUNT);
    assertThat(existing.getRole()).isEqualTo(UserRole.PLAYER);
    assertThat(boot("op@scores.app", STRONG, true).bootstrap()).isEqualTo(AdminBootstrap.Outcome.PROMOTED);
    assertThat(existing.getRole()).isEqualTo(UserRole.ADMIN);
  }

  @Test
  void sem_senha_forte_nao_cria_e_nunca_usa_valor_padrao() {
    when(users.findByEmailHash(any())).thenReturn(Optional.empty());
    assertThat(boot("op@scores.app", "", false).bootstrap()).isEqualTo(AdminBootstrap.Outcome.SKIPPED_WEAK_PASSWORD);
    assertThat(boot("op@scores.app", STRONG.substring(0, AdminBootstrap.MIN_PASSWORD - 1), false).bootstrap()).isEqualTo(AdminBootstrap.Outcome.SKIPPED_WEAK_PASSWORD);
    verify(users, never()).save(any());
  }

  @Test
  void cria_admin_com_senha_do_segredo_e_origem_system() {
    when(users.findByEmailHash(any())).thenReturn(Optional.empty());
    assertThat(boot("Op@Scores.app", STRONG, false).bootstrap()).isEqualTo(AdminBootstrap.Outcome.CREATED);
    ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(saved.getValue().getAccountOrigin()).isEqualTo(AccountOrigin.SYSTEM);
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("h:" + STRONG).doesNotContain("admin");
  }
}
