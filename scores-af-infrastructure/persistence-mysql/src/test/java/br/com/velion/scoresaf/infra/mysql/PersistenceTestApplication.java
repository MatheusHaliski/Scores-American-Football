package br.com.velion.scoresaf.infra.mysql;

import br.com.velion.scoresaf.infra.mysql.config.MysqlPersistenceConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/** Configuração Spring Boot local ao módulo para os testes @DataJpaTest (H2 em modo MySQL). */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(MysqlPersistenceConfig.class)
public class PersistenceTestApplication {}
