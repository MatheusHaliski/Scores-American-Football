package br.com.velion.scoresaf.web.support;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * IP do cliente para limites e auditoria (bloco 3), nesta ordem:
 * <ol>
 *   <li>IP assinado pelo BFF do front: HMAC-SHA256({@code EDGE_PROXY_SECRET}, {@code ip|ts}) em {@value #H_IP}, {@value #H_TS} e
 *       {@value #H_SIG}, com tolerância de 60 s e comparação em tempo constante;</li>
 *   <li>cabeçalho de valor único escrito pela borda, se configurado ({@code scores.security.client-ip.trusted-header}, ex.:
 *       {@code X-Real-IP});</li>
 *   <li>IP da conexão TCP.</li>
 * </ol>
 * Nunca usa {@code X-Forwarded-For}: qualquer cliente escreve o que quiser nele. Para baldes, IPv6 conta pelo prefixo /64
 * (trocar o fim do endereço não abre balde novo).
 */
@Component
public class ClientIpResolver {
  public static final String H_IP = "X-Scores-Client-Ip";
  public static final String H_TS = "X-Scores-Proxy-Ts";
  public static final String H_SIG = "X-Scores-Proxy-Sig";
  static final long TOLERANCE_SECONDS = 60;
  static final int MIN_SECRET_LENGTH = 32;
  private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);
  private static final Pattern IPV4 = Pattern.compile("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");
  private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

  private final byte[] secret;
  private final String trustedHeader;
  private final Clock clock;

  @Autowired
  public ClientIpResolver(@Value("${scores.security.edge-proxy-secret:}") String secret,
      @Value("${scores.security.client-ip.trusted-header:}") String trustedHeader) {
    this(secret, trustedHeader, Clock.systemUTC());
  }

  ClientIpResolver(String secret, String trustedHeader, Clock clock) {
    if (secret == null || secret.isBlank()) {
      log.warn("EDGE_PROXY_SECRET ausente: o IP assinado pelo BFF não é aceito (limites usam o IP da conexão).");
      this.secret = null;
    } else if (secret.length() < MIN_SECRET_LENGTH) {
      log.warn("EDGE_PROXY_SECRET com menos de {} caracteres: ignorado. Gere com openssl rand -hex 32.", MIN_SECRET_LENGTH);
      this.secret = null;
    } else {
      this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }
    this.trustedHeader = trustedHeader == null || trustedHeader.isBlank() ? null : trustedHeader.trim();
    this.clock = clock;
  }

  public String resolve(HttpServletRequest req) {
    String signed = signedIp(req);
    if (signed != null) return signed;
    if (trustedHeader != null) {
      String v = req.getHeader(trustedHeader);
      if (v != null && !v.contains(",") && isIp(v.trim())) return v.trim();
    }
    return req.getRemoteAddr();
  }

  /** Chave do balde: IPv4 inteiro; IPv6 pelo prefixo /64. */
  public String bucketKey(HttpServletRequest req) { return bucketKey(resolve(req)); }

  public static String bucketKey(String ip) {
    if (ip == null) return "unknown";
    if (!ip.contains(":")) return ip;
    try {
      byte[] a = InetAddress.getByName(ip).getAddress();
      if (a.length != 16) return ip;
      return "v6:" + HexFormat.of().formatHex(a, 0, 8) + "/64";
    } catch (Exception e) {
      return "v6:invalid";
    }
  }

  /** Assinatura que o BFF manda (testes e documentação do contrato com o front). */
  public static String sign(byte[] secret, String ip, long epochSeconds) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal((ip + "|" + epochSeconds).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("HMAC indisponível", e);
    }
  }

  private String signedIp(HttpServletRequest req) {
    if (secret == null) return null;
    String ip = req.getHeader(H_IP), ts = req.getHeader(H_TS), sig = req.getHeader(H_SIG);
    if (ip == null || ts == null || sig == null || !isIp(ip)) return null;
    long when;
    try { when = Long.parseLong(ts); } catch (NumberFormatException e) { return null; }
    if (Math.abs(clock.instant().getEpochSecond() - when) > TOLERANCE_SECONDS) return null;
    byte[] expected = sign(secret, ip, when).getBytes(StandardCharsets.US_ASCII);
    return MessageDigest.isEqual(expected, sig.toLowerCase().getBytes(StandardCharsets.US_ASCII)) ? ip : null;
  }

  static boolean isIp(String v) {
    if (IPV4.matcher(v).matches()) return true;
    if (!v.contains(":") || !IPV6_CHARS.matcher(v).matches()) return false;
    try { return InetAddress.getByName(v).getAddress().length == 16; } catch (Exception e) { return false; }
  }
}
