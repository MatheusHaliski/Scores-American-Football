package br.com.velion.scoresaf.web.config;

import br.com.velion.scoresaf.domain.account.UserLocale;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Internacionalização do backend (RF46 CA05): catálogos {@code classpath:i18n/messages*.properties} (UTF-8, MessageFormat), idiomas
 * suportados pt-BR (padrão), en-US e es-ES resolvidos pelo {@code Accept-Language} (o cliente envia o {@code preferredLocale} da
 * conta; "en" ⇒ en-US, "es" ⇒ es-ES). Respostas levam {@code Content-Language} e {@code Vary: Accept-Language}.
 * Versão do basquete no lugar da do esqueleto: mesma resolução, mais o filtro de {@code Content-Language} e os idiomas vindos
 * de {@link UserLocale} (os mesmos da conta).
 */
@Configuration(proxyBeanMethods = false)
public class I18nConfig {
  public static final Locale PT_BR = UserLocale.PT_BR.toLocale();
  public static final List<Locale> SUPPORTED = Arrays.stream(UserLocale.values()).map(UserLocale::toLocale).toList();

  @Bean
  public MessageSource messageSource() {
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    ms.setBasename("classpath:i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    ms.setDefaultLocale(UserLocale.PT_BR.toLocale());
    ms.setUseCodeAsDefaultMessage(false);
    return ms;
  }

  @Bean
  public LocaleResolver localeResolver() { return resolver(); }

  public static AcceptHeaderLocaleResolver resolver() {
    AcceptHeaderLocaleResolver r = new AcceptHeaderLocaleResolver() {
      @Override public Locale resolveLocale(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) return UserLocale.PT_BR.toLocale();
        try {
          for (Locale.LanguageRange range : Locale.LanguageRange.parse(header)) {
            UserLocale l = UserLocale.parseOrNull(range.getRange());
            if (l != null) return l.toLocale();
          }
        } catch (IllegalArgumentException ignored) { /* cabeçalho malformado ⇒ padrão */ }
        return UserLocale.PT_BR.toLocale();
      }
    };
    r.setSupportedLocales(SUPPORTED);
    r.setDefaultLocale(UserLocale.PT_BR.toLocale());
    return r;
  }

  /** Publica o idioma resolvido no contexto da requisição e nos cabeçalhos de resposta. */
  @Component
  @Order(Ordered.HIGHEST_PRECEDENCE + 20)
  public static class ContentLanguageFilter extends OncePerRequestFilter {
    private final AcceptHeaderLocaleResolver resolver = resolver();
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
      Locale locale = resolver.resolveLocale(req);
      LocaleContextHolder.setLocale(locale);
      res.setHeader("Content-Language", locale.toLanguageTag());
      res.addHeader("Vary", "Accept-Language");
      try { chain.doFilter(req, res); } finally { LocaleContextHolder.resetLocaleContext(); }
    }
  }
}
