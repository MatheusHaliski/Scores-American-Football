package br.com.velion.scoresaf.config;

import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Semente na subida (RF5/RF34, D-23): grava o mundo padrão fictício se ainda não existir (idempotente). Ligada por
 * {@code scores.seed.enabled} ({@code SCORES_SEED_ENABLED}, padrão {@code true}; desligada no perfil de teste, onde cada teste
 * chama o {@link DefaultWorldSeeder} quando precisa).
 */
@Component
@ConditionalOnProperty(name = "scores.seed.enabled", havingValue = "true")
public class SeedRunner implements ApplicationRunner {
  private final DefaultWorldSeeder seeder;

  public SeedRunner(DefaultWorldSeeder seeder) { this.seeder = seeder; }

  @Override
  public void run(ApplicationArguments args) { seeder.seedIfMissing(); }
}
