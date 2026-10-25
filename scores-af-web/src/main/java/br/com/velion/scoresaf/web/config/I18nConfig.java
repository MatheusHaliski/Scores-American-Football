package br.com.velion.scoresaf.web.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Idiomas do backend (RNF13): catálogos {@code classpath:i18n/messages*.properties} em UTF-8; pt-BR (padrão), en-US e es-ES
 * pelo {@code Accept-Language} ("en" vira en-US, "es" vira es-ES; o resto, pt-BR).
 */
@Configuration(proxyBeanMethods = false)
public class I18nConfig {
  public static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
  public static final List<Locale> SUPPORTED = List.of(PT_BR, Locale.forLanguageTag("en-US"), Locale.forLanguageTag("es-ES"));

  @Bean
  public MessageSource messageSource() {
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    ms.setBasename("classpath:i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    ms.setDefaultLocale(PT_BR);
    return ms;
  }

  @Bean
  public LocaleResolver localeResolver() {
    return new AcceptHeaderLocaleResolver() {
      @Override public Locale resolveLocale(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) return PT_BR;
        try {
          for (Locale.LanguageRange range : Locale.LanguageRange.parse(header)) {
            String lang = range.getRange().toLowerCase(Locale.ROOT);
            if (lang.startsWith("en")) return SUPPORTED.get(1);
            if (lang.startsWith("es")) return SUPPORTED.get(2);
            if (lang.startsWith("pt")) return PT_BR;
          }
        } catch (IllegalArgumentException ignored) { /* cabeçalho malformado: padrão */ }
        return PT_BR;
      }
    };
  }
}
