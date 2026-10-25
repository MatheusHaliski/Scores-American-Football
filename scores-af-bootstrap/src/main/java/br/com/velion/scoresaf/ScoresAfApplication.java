package br.com.velion.scoresaf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Scores: American Football — VELION TECH CO. Ponto de entrada da aplicação.
 *
 * <p>Perfis: {@code dev} (padrão) e {@code test}. Bancos e adaptadores (MySQL/Flyway, Redis, Cassandra, OpenSearch, S3) entram
 * nos blocos que os usam (D-8), com os mesmos fallbacks em memória do Scores de basquete.
 */
@SpringBootApplication(scanBasePackages = "br.com.velion.scoresaf")
public class ScoresAfApplication {
  public static void main(String[] args) {
    SpringApplication.run(ScoresAfApplication.class, args);
  }
}
