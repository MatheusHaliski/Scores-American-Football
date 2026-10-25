package br.com.velion.scoresaf.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LogSafeTest {

  @Test
  void mascara_email_sem_revelar_usuario_nem_dominio() {
    assertThat(LogSafe.maskEmail("ana.souza@exemplo.com")).isEqualTo("a***@e***.com");
    assertThat(LogSafe.maskEmail("x@scores.test")).isEqualTo("x***@s***.test");
    assertThat(LogSafe.maskEmail(null)).isEqualTo("***");
    assertThat(LogSafe.maskEmail("sem-arroba")).isEqualTo("***");
    assertThat(LogSafe.maskEmail("@dominio.com")).isEqualTo("***");
  }

  @Test
  void remove_token_de_link_codigo_numerico_e_sequencia_opaca() {
    String token = fakeToken();
    String text = "Para redefinir acesse: https://scores.app/reset?token=" + token + " (válido por 30 minutos). Código 482913."
        + " Sessão eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJhLWRlLXRlc3Rl";
    String safe = LogSafe.redact(text);
    assertThat(safe).doesNotContain(token).doesNotContain("482913").doesNotContain("eyJhbGciOiJSUzI1NiJ9")
        .contains("token=" + LogSafe.REDACTED).contains("30 minutos");
  }

  /** Token fictício gerado na hora: um literal no código-fonte vira achado do gitleaks no histórico. */
  static String fakeToken() { return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", ""); }
}
