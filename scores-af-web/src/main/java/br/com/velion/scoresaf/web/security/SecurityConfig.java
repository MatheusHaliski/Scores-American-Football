package br.com.velion.scoresaf.web.security;

import br.com.velion.scoresaf.application.port.out.cache.RateLimiterPort;
import br.com.velion.scoresaf.application.port.out.cache.SessionCachePort;
import br.com.velion.scoresaf.application.usecase.rf02.AuthPolicy;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import br.com.velion.scoresaf.web.error.ErrorWriter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Segurança (RNF3/RNF4/RNF5): stateless, JWT RSA (access 15 min), Argon2id, CORS restrito, headers
 * HSTS/CSP/no-sniff, `@PreAuthorize` por recurso. Rotas públicas: auth, {@code GET /api/v1/meta} (esqueleto), OpenAPI e
 * health; o resto do actuator é negado. Substitui o SecurityConfig do esqueleto (que não tinha usuários): com o
 * {@link JwtDecoder} no contexto o Spring Boot não cria usuário em memória nem imprime senha gerada.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

  @Bean
  public RsaKeyProvider rsaKeyProvider(SecurityProperties props) {
    return new RsaKeyProvider(props.jwt().privateKeyPem(), props.jwt().publicKeyPem());
  }

  @Bean
  public JwtEncoder jwtEncoder(RsaKeyProvider keys) {
    RSAKey jwk = new RSAKey.Builder(keys.publicKey()).privateKey(keys.privateKey()).keyID("scoresaf-1").build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
  }

  @Bean
  public JwtDecoder jwtDecoder(RsaKeyProvider keys, SecurityProperties props) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
    String audience = props.jwt().audienceOrDefault();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(props.jwt().issuer()),
        new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audience)),
        new JwtClaimValidator<String>("sid", sid -> sid != null && !sid.isBlank())));
    return decoder;
  }

  @Bean
  public AuthPolicy authPolicy(SecurityProperties props) {
    return new AuthPolicy(props.login().maxFailedAttempts(), props.login().lockMinutes(), props.jwt().refreshTtlDays(), 30);
  }

  /** Argon2id (RNF5): 19 456 KB de memória, 2 iterações, paralelismo 1, salt 16, hash 32. */
  @Bean
  public PasswordEncoder passwordEncoder() { return new Argon2PasswordEncoder(16, 32, 1, 19456, 2); }

  @Bean
  @ConditionalOnProperty(name = "scores.persistence.cache", havingValue = "memory", matchIfMissing = true)
  public RateLimiterPort inMemoryRateLimiter() { return new InMemoryRateLimiter(); }

  /** Projeção das sessões em memória enquanto o cache-redis não chega (D-8); a fonte da verdade é {@code user_session}. */
  @Bean
  @ConditionalOnProperty(name = "scores.persistence.cache", havingValue = "memory", matchIfMissing = true)
  public SessionCachePort inMemorySessionCache() { return new InMemorySessionCache(); }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(@org.springframework.beans.factory.annotation.Value("${scores.app.cors-allowed-origins:http://localhost:3000}") String origins) {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of(origins.split("\\s*,\\s*")));
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id", "Accept", "Accept-Language", RefreshCookies.HEADER_PEDIDO));
    cfg.setExposedHeaders(List.of("X-Correlation-Id", "Retry-After"));
    // O refresh vai em cookie HttpOnly (D-114), então o navegador precisa mandá-lo nas chamadas à API. Credenciais
    // só com a allowlist de origens acima — nunca com "*", que o próprio CORS recusaria junto de allowCredentials.
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/api/**", cfg);
    return src;
  }

  /** O SessionActiveFilter roda dentro da cadeia do Spring Security (depois do bearer); não registrar de novo no servlet. */
  @Bean
  public org.springframework.boot.web.servlet.FilterRegistrationBean<SessionActiveFilter> sessionActiveFilterRegistration(SessionActiveFilter f) {
    var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(f);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, @org.springframework.beans.factory.annotation.Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
      ErrorWriter errors, SessionActiveFilter sessionActive) throws Exception {
    BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    AuthenticationEntryPoint unauthenticated = (req, res, ex) -> {
      bearer.commence(req, res, ex); // mantém o WWW-Authenticate do RFC 6750
      errors.write(req, res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Entre na sua conta para continuar.", null);
    };
    AccessDeniedHandler denied = (req, res, ex) ->
        errors.write(req, res, HttpStatus.FORBIDDEN, "FORBIDDEN", "Você não tem acesso a este recurso.", null);
    http
        .csrf(csrf -> csrf.disable())
        .cors(c -> c.configurationSource(cors))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(h -> h
            .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; img-src 'self' data: https:; script-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'"))
            .frameOptions(f -> f.deny())
            .contentTypeOptions(ct -> {}))
        .exceptionHandling(e -> e.authenticationEntryPoint(unauthenticated).accessDeniedHandler(denied))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/meta").permitAll()
            .requestMatchers("/api/v1/auth/**", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                "/actuator/health", "/actuator/health/**", "/error").permitAll()
            // só o health é exposto (management.endpoints.web.exposure.include); o resto do actuator nunca é público
            .requestMatchers("/actuator/**").denyAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
        .oauth2ResourceServer(o -> o
            .authenticationEntryPoint(unauthenticated)
            .accessDeniedHandler(denied)
            .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .addFilterAfter(sessionActive, BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    return jwt -> {
      List<String> roles = jwt.getClaimAsStringList("roles");
      List<GrantedAuthority> authorities = roles == null ? List.of()
          : roles.stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
      return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    };
  }
}
