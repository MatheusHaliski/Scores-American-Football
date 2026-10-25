package br.com.velion.scoresaf.web.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Emite access tokens JWT RS256 com {@code iss}, {@code aud}, TTL curto (15 min) e {@code sid} = família da sessão (D-25):
 * o {@code SessionActiveFilter} recusa o token se a família não tem mais sessão ativa. Refresh tokens são opacos e persistidos.
 */
@Service
public class TokenService {
  private final JwtEncoder encoder;
  private final SecurityProperties props;

  public TokenService(JwtEncoder encoder, SecurityProperties props) { this.encoder = encoder; this.props = props; }

  public IssuedToken issueAccessToken(UUID userId, String displayName, List<String> roles, UUID sessionFamily) {
    Instant now = Instant.now();
    Instant exp = now.plus(Duration.ofMinutes(props.jwt().accessTtlMinutes()));
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(props.jwt().issuer())
        .audience(List.of(props.jwt().audienceOrDefault()))
        .issuedAt(now)
        .expiresAt(exp)
        .subject(userId.toString())
        .claim("name", displayName)
        .claim("roles", roles)
        .claim("sid", sessionFamily.toString())
        .id(UUID.randomUUID().toString())
        .build();
    return new IssuedToken(encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue(), exp);
  }

  public record IssuedToken(String value, Instant expiresAt) {}
}
