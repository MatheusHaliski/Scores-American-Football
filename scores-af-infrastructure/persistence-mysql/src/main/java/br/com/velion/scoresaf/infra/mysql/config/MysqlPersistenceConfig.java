package br.com.velion.scoresaf.infra.mysql.config;

import br.com.velion.scoresaf.infra.mysql.support.AssignedIdJpaRepository;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuração da fonte da verdade relacional (MySQL 8 em produção, H2 modo MySQL em dev/teste).
 * Repositórios Spring Data em {@code br.com.velion.scoresaf.infra.mysql}; entidades em {@code br.com.velion.scoresaf.domain}.
 * Base dos repositórios: {@link AssignedIdJpaRepository} (persist para entidade nova com UUID atribuído, D-9).
 */
@Configuration
@EnableJpaRepositories(basePackages = "br.com.velion.scoresaf.infra.mysql", repositoryBaseClass = AssignedIdJpaRepository.class)
@EntityScan(basePackages = "br.com.velion.scoresaf.domain")
@EnableTransactionManagement
@Import(EncryptionKeyInitializer.class)
public class MysqlPersistenceConfig {}
