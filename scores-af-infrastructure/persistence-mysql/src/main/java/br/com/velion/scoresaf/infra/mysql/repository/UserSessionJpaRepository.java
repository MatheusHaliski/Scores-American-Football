package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.UserSessionRepository;
import br.com.velion.scoresaf.domain.account.UserSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link UserSessionRepository}. */
public interface UserSessionJpaRepository extends JpaRepository<UserSession, UUID>, UserSessionRepository {}
