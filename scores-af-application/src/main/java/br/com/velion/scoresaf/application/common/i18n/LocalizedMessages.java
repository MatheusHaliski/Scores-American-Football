package br.com.velion.scoresaf.application.common.i18n;

import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserLocale;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Resolução de mensagens do backend por chave + parâmetros (RF46 CA05): e-mails transacionais, títulos/detalhes RFC 7807 e
 * textos gerados pelo jogo. Catálogos em {@code classpath:i18n/messages[_en_US|_es_ES].properties} (MessageFormat); pt-BR é o
 * padrão e o fallback. Nunca devolve {@code null}: sem chave, devolve o {@code fallback} informado (ou a própria chave).
 */
@Component
public class LocalizedMessages {
  private final MessageSource source;
  public LocalizedMessages(MessageSource source) { this.source = source; }

  public String get(String key, Locale locale, Object... args) { return get(key, null, locale, args); }

  public String get(String key, String fallback, Locale locale, Object... args) {
    Locale l = locale == null ? UserLocale.PT_BR.toLocale() : locale;
    return source.getMessage(key, args, fallback == null ? key : fallback, l);
  }

  public String forUser(UserAccount user, String key, Object... args) {
    return get(key, user == null || user.getLocale() == null ? UserLocale.PT_BR.toLocale() : user.getLocale().toLocale(), args);
  }
}
