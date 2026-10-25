package br.com.velion.scoresaf.application.port.out.notify;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** O e-mail de fallback vai para o log: nunca com o token do link, o código ou o e-mail inteiro. */
class LoggingEmailAdapterTest {
  private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailAdapter.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach void attach() { appender.start(); logger.addAppender(appender); }
  @AfterEach void detach() { logger.detachAppender(appender); }

  @Test
  void log_do_email_nao_leva_token_codigo_nem_endereco() {
    String token = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", ""); // fictício, gerado na hora
    new LoggingEmailAdapter().send("ana.souza@exemplo.com", "Scores — redefinição de senha",
        "Para redefinir sua senha acesse: https://scores.app/reset?token=" + token + " (válido por 30 minutos). Código: 739104");
    String logged = appender.list.get(0).getFormattedMessage();
    assertThat(logged).doesNotContain(token).doesNotContain("739104").doesNotContain("ana.souza@exemplo.com")
        .contains("a***@e***.com").contains("redefinição de senha");
  }
}
