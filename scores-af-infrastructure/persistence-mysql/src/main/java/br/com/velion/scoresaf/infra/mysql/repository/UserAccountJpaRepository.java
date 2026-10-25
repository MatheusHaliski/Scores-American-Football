package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.domain.account.UserAccount;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link UserAccountRepository}. */
public interface UserAccountJpaRepository extends JpaRepository<UserAccount, UUID>, UserAccountRepository {}
