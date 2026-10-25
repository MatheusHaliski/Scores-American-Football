package br.com.velion.scoresaf.web.config;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;

/**
 * Perfil {@code prod}: nenhum segredo pode estar vazio. Sem isto, a API subiria com par JWT efêmero, chave de cifra de
 * desenvolvimento e bancos sem senha. A mensagem cita só o <b>nome</b> da variável, nunca o valor.
 */
@Configuration
@Profile("prod")
public class ProductionConfigGuard {

  public ProductionConfigGuard(Environment env) {
    List<String> problems = problems(env);
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Configuração de produção incompleta (perfil prod): " + String.join("; ", problems));
    }
  }

  public static List<String> problems(PropertyResolver env) {
    List<String> p = new ArrayList<>();
    required(env, p, "scores.security.jwt.private-key-pem", "JWT_PRIVATE_KEY_PEM");
    required(env, p, "scores.security.jwt.public-key-pem", "JWT_PUBLIC_KEY_PEM");
    String dataKey = env.getProperty("scores.security.encryption.data-key", "");
    if (dataKey.isBlank()) p.add("DATA_ENCRYPTION_KEY vazia");
    else if (decodedLength(dataKey) != 32) p.add("DATA_ENCRYPTION_KEY deve ser Base64 de 32 bytes");
    String url = env.getProperty("spring.datasource.url", "");
    if (url.isBlank() || url.startsWith("jdbc:h2:")) p.add("spring.datasource.url deve apontar para o MySQL (perfil mysql), não para o H2");
    required(env, p, "spring.datasource.password", "MYSQL_PASSWORD");
    String origins = env.getProperty("scores.app.cors-allowed-origins", "");
    if (origins.isBlank() || origins.contains("*") || origins.contains("localhost") || origins.contains("127.0.0.1")) {
      p.add("APP_CORS_ALLOWED_ORIGINS deve listar só as origens públicas do front (sem *, localhost ou 127.0.0.1)");
    }
    // links curtos da mídia privada (bloco E): sem a chave, cada instância assinaria com uma chave efêmera
    String linkSecret = env.getProperty("scores.media.link-secret", "");
    if (linkSecret.isBlank()) p.add("MEDIA_LINK_SECRET vazia");
    else if (linkSecret.length() < 32) p.add("MEDIA_LINK_SECRET deve ter 32+ caracteres (openssl rand -hex 32)");
    if ("cassandra".equals(env.getProperty("scores.persistence.timeline"))) required(env, p, "spring.cassandra.password", "CASSANDRA_PASSWORD");
    if ("opensearch".equals(env.getProperty("scores.persistence.search"))) required(env, p, "scores.opensearch.password", "OPENSEARCH_PASSWORD");
    if ("s3".equals(env.getProperty("scores.persistence.media"))) {
      required(env, p, "scores.s3.access-key-id", "S3_ACCESS_KEY_ID");
      required(env, p, "scores.s3.secret-access-key", "S3_SECRET_ACCESS_KEY");
    }
    if ("redis".equals(env.getProperty("scores.persistence.cache"))) {
      String redis = env.getProperty("spring.data.redis.url", "");
      if (!redis.contains("@")) p.add("REDIS_URL deve ter usuário e senha (redis[s]://usuario:senha@host)");
    }
    return p;
  }

  private static void required(PropertyResolver env, List<String> p, String property, String variable) {
    if (env.getProperty(property, "").isBlank()) p.add(variable + " vazia");
  }

  private static int decodedLength(String base64) {
    try { return Base64.getDecoder().decode(base64.trim()).length; }
    catch (IllegalArgumentException e) {
      try { return Base64.getUrlDecoder().decode(base64.trim()).length; } catch (IllegalArgumentException e2) { return -1; }
    }
  }
}
