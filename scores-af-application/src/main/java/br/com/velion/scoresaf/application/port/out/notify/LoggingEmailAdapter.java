package br.com.velion.scoresaf.application.port.out.notify;

import br.com.velion.scoresaf.domain.common.LogSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback sem provedor de e-mail: registra que um e-mail sairia, com destinatário mascarado e corpo sem link de token
 * nem código. Um adaptador real deve ser @Primary.
 */
@Component
public class LoggingEmailAdapter implements EmailPort {
  private static final Logger log = LoggerFactory.getLogger(LoggingEmailAdapter.class);

  @Override public void send(String toEmail, String subject, String body) {
    log.info("[email-dev] para={} assunto={} corpo={}", LogSafe.maskEmail(toEmail), subject, LogSafe.redact(body));
  }
}
