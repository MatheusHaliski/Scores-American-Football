package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.NotificationRepository;
import br.com.velion.scoresaf.domain.inbox.Notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link NotificationRepository}. */
public interface NotificationJpaRepository extends JpaRepository<Notification, UUID>, NotificationRepository {}
