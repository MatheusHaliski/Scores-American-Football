package br.com.velion.scoresaf.web.security;

import br.com.velion.scoresaf.web.error.ErrorWriter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Segurança do esqueleto (bloco 2): stateless, CORS só para a allowlist, cabeçalhos de segurança e tudo fechado por padrão.
 * Públicos: o health (sem detalhes), {@code GET /api/v1/meta} e a documentação da API. O login com JWT RSA, refresh em cookie
 * {@code __Host-} e Argon2id chega no bloco 3 (D-5); até lá, qualquer rota protegida responde 401 no formato padrão.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  /**
   * Sem nenhum usuário. Sem este bean, o Spring Boot cria um usuário em memória e imprime a senha gerada no log — o projeto
   * nunca imprime credencial (docs/01 §6).
   */
  @Bean
  public UserDetailsService noUsers() { return new InMemoryUserDetailsManager(); }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(@Value("${scores.app.cors-allowed-origins:http://localhost:3000}") String origins) {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of(origins.split("\\s*,\\s*")));
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id", "Accept", "Accept-Language"));
    cfg.setExposedHeaders(List.of("X-Correlation-Id", "Retry-After"));
    // credenciais (o cookie de refresh do bloco 3) só com a allowlist acima, nunca com "*"
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/api/**", cfg);
    return src;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, ErrorWriter errors) throws Exception {
    AuthenticationEntryPoint unauthenticated = (req, res, ex) -> {
      res.setHeader("WWW-Authenticate", "Bearer");
      errors.write(req, res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", null);
    };
    AccessDeniedHandler denied = (req, res, ex) -> errors.write(req, res, HttpStatus.FORBIDDEN, "FORBIDDEN", null);
    http
        .csrf(csrf -> csrf.disable())
        // pega o bean corsConfigurationSource acima (o MVC registra outro CorsConfigurationSource, então não injetar por tipo)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(b -> b.disable())
        .formLogin(f -> f.disable())
        .headers(h -> h
            .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; img-src 'self' data: https:; script-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'"))
            .frameOptions(f -> f.deny())
            .contentTypeOptions(ct -> {}))
        .exceptionHandling(e -> e.authenticationEntryPoint(unauthenticated).accessDeniedHandler(denied))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/meta").permitAll()
            .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health", "/actuator/health/**", "/error").permitAll()
            // só o health é exposto; o resto do actuator nunca é público
            .requestMatchers("/actuator/**").denyAll()
            .anyRequest().authenticated());
    return http.build();
  }
}
