package br.com.velion.scoresaf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scores: American Football — VELION TECH CO. Ponto de entrada da aplicação.
 *
 * <p>Perfis: {@code dev} (padrão: H2 em memória + adaptadores em memória), {@code test}, {@code mysql} (MySQL 8 + Flyway) e
 * {@code prod} (sempre com {@code mysql}). Repositórios JPA e entidades são registrados por {@code MysqlPersistenceConfig}
 * (persistence-mysql) — não duplicar aqui. Redis, Cassandra, OpenSearch e S3 entram nos blocos que os usam (D-8), com os mesmos
 * fallbacks em memória do Scores de basquete. O agendamento roda a eliminação de contas com prazo vencido e a limpeza das
 * exportações LGPD expiradas (RF46).
 */
@SpringBootApplication(scanBasePackages = "br.com.velion.scoresaf")
@EnableScheduling
public class ScoresAfApplication {
  public static void main(String[] args) {
    SpringApplication.run(ScoresAfApplication.class, args);
  }
}
