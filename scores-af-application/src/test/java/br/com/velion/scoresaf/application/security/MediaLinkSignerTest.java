package br.com.velion.scoresaf.application.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Links curtos assinados (bloco E): só o token íntegro e dentro do prazo abre a mídia. A chave é gerada na hora. */
class MediaLinkSignerTest {
  private static final Instant T0 = Instant.parse("2026-10-10T12:00:00Z");
  private final String secret = randomSecret();
  private final UUID ref = UUID.randomUUID(), owner = UUID.randomUUID();

  private static String randomSecret() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return HexFormat.of().formatHex(b);
  }

  private MediaLinkSigner at(Instant now) { return new MediaLinkSigner(secret, Duration.ofSeconds(120), Clock.fixed(now, ZoneOffset.UTC)); }

  @Test
  void token_integro_abre_ate_expirar() {
    MediaLinkSigner.SignedLink link = at(T0).sign(ref, owner);
    assertThat(link.expiresAt()).isEqualTo(T0.plusSeconds(120));
    assertThat(at(T0.plusSeconds(119)).verify(link.token())).hasValueSatisfying(c -> {
      assertThat(c.subjectId()).isEqualTo(ref);
      assertThat(c.ownerId()).isEqualTo(owner);
    });
    assertThat(at(T0.plusSeconds(120)).verify(link.token())).as("expirado").isEmpty();
  }

  @Test
  void token_adulterado_ou_de_outra_chave_e_recusado() {
    String token = at(T0).sign(ref, owner).token();
    String payload = token.substring(0, token.indexOf('.'));
    String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
        new String(java.util.Base64.getUrlDecoder().decode(payload)).replace(owner.toString(), UUID.randomUUID().toString()).getBytes());
    assertThat(at(T0).verify(forged + token.substring(token.indexOf('.')))).as("dono trocado").isEmpty();
    String zeros = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    assertThat(at(T0).verify(payload + "." + zeros)).as("assinatura trocada").isEmpty();
    assertThat(new MediaLinkSigner(randomSecret(), Duration.ofSeconds(120), Clock.fixed(T0, ZoneOffset.UTC)).verify(token)).as("outra chave").isEmpty();
    assertThat(at(T0).verify("lixo")).isEmpty();
    assertThat(at(T0).verify(null)).isEmpty();
    assertThat(at(T0).verify("a.b.c")).isEmpty();
  }

  @Test
  void prazo_maior_que_o_ttl_configurado_e_recusado() {
    String longLived = new MediaLinkSigner(secret, Duration.ofDays(1), Clock.fixed(T0, ZoneOffset.UTC)).sign(ref, owner).token();
    assertThat(at(T0).verify(longLived)).isEmpty();
  }

  @Test
  void sem_segredo_usa_chave_efemera_que_nao_vale_em_outra_instancia() {
    MediaLinkSigner a = new MediaLinkSigner("", Duration.ofSeconds(120), Clock.fixed(T0, ZoneOffset.UTC));
    MediaLinkSigner b = new MediaLinkSigner("curto", Duration.ofSeconds(120), Clock.fixed(T0, ZoneOffset.UTC));
    String token = a.sign(ref, owner).token();
    assertThat(a.verify(token)).isPresent();
    assertThat(b.verify(token)).isEmpty();
    assertThat(token).doesNotContain(secret);
  }
}
