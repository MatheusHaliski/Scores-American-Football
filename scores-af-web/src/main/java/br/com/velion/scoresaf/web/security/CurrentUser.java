package br.com.velion.scoresaf.web.security;

import br.com.velion.scoresaf.application.common.exception.ForbiddenException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Acesso ao usuário autenticado (subject do JWT). */
@Component
public class CurrentUser {
  public Optional<UUID> id() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) return Optional.empty();
    try { return Optional.of(UUID.fromString(jwt.getSubject())); } catch (IllegalArgumentException e) { return Optional.empty(); }
  }
  public UUID requireId() { return id().orElseThrow(() -> new ForbiddenException("Usuário não autenticado")); }
  public boolean hasRole(String role) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
  }
}
