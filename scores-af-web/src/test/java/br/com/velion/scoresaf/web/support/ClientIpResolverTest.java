package br.com.velion.scoresaf.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {
  // segredo gerado na execução: nenhum valor fixo em teste (docs/01 §6)
  private static final String SECRET = randomHex();

  private static String randomHex() {
    byte[] b = new byte[32];
    new java.security.SecureRandom().nextBytes(b);
    return java.util.HexFormat.of().formatHex(b);
  }
  private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private static MockHttpServletRequest req(String remote) {
    MockHttpServletRequest r = new MockHttpServletRequest();
    r.setRemoteAddr(remote);
    return r;
  }

  private static void sign(MockHttpServletRequest r, String ip, long ts, String secret) {
    r.addHeader(ClientIpResolver.H_IP, ip);
    r.addHeader(ClientIpResolver.H_TS, String.valueOf(ts));
    r.addHeader(ClientIpResolver.H_SIG, ClientIpResolver.sign(secret.getBytes(StandardCharsets.UTF_8), ip, ts));
  }

  @Test
  void aceita_ip_assinado_pelo_bff_dentro_de_60s() {
    ClientIpResolver resolver = new ClientIpResolver(SECRET, "", clock);
    MockHttpServletRequest r = req("10.0.0.5");
    sign(r, "203.0.113.7", NOW.getEpochSecond() - 30, SECRET);
    assertThat(resolver.resolve(r)).isEqualTo("203.0.113.7");
  }

  @Test
  void recusa_assinatura_errada_vencida_ou_segredo_curto() {
    ClientIpResolver resolver = new ClientIpResolver(SECRET, "", clock);
    MockHttpServletRequest wrong = req("10.0.0.5");
    sign(wrong, "203.0.113.7", NOW.getEpochSecond(), "outro-segredo-qualquer-com-mais-de-32-caracteres");
    assertThat(resolver.resolve(wrong)).isEqualTo("10.0.0.5");
    MockHttpServletRequest old = req("10.0.0.5");
    sign(old, "203.0.113.7", NOW.getEpochSecond() - 61, SECRET);
    assertThat(resolver.resolve(old)).isEqualTo("10.0.0.5");
    ClientIpResolver weak = new ClientIpResolver("curto", "", clock);
    MockHttpServletRequest r = req("10.0.0.5");
    sign(r, "203.0.113.7", NOW.getEpochSecond(), "curto");
    assertThat(weak.resolve(r)).isEqualTo("10.0.0.5");
  }

  @Test
  void nunca_usa_x_forwarded_for() {
    ClientIpResolver resolver = new ClientIpResolver(SECRET, "", clock);
    MockHttpServletRequest r = req("198.51.100.9");
    r.addHeader("X-Forwarded-For", "1.2.3.4");
    assertThat(resolver.resolve(r)).isEqualTo("198.51.100.9");
  }

  @Test
  void cabecalho_da_borda_so_quando_configurado_e_com_valor_unico() {
    MockHttpServletRequest r = req("10.0.0.5");
    r.addHeader("X-Real-IP", "198.51.100.20");
    assertThat(new ClientIpResolver("", "", clock).resolve(r)).isEqualTo("10.0.0.5");
    assertThat(new ClientIpResolver("", "X-Real-IP", clock).resolve(r)).isEqualTo("198.51.100.20");
    MockHttpServletRequest multi = req("10.0.0.5");
    multi.addHeader("X-Real-IP", "198.51.100.20, 1.2.3.4");
    assertThat(new ClientIpResolver("", "X-Real-IP", clock).resolve(multi)).isEqualTo("10.0.0.5");
  }

  @Test
  void ipv6_conta_pelo_prefixo_64() {
    assertThat(ClientIpResolver.bucketKey("2001:db8:abcd:12:1::1")).isEqualTo(ClientIpResolver.bucketKey("2001:db8:abcd:12:ffff:ffff:ffff:fffe"));
    assertThat(ClientIpResolver.bucketKey("2001:db8:abcd:12::1")).isNotEqualTo(ClientIpResolver.bucketKey("2001:db8:abcd:13::1"));
    assertThat(ClientIpResolver.bucketKey("203.0.113.7")).isEqualTo("203.0.113.7");
  }
}
