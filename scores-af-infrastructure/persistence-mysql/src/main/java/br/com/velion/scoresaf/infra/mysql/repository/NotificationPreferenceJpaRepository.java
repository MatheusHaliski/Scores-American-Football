package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.NotificationPreferenceRepository;
import br.com.velion.scoresaf.domain.account.NotificationPreference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link NotificationPreferenceRepository}. */
public interface NotificationPreferenceJpaRepository extends JpaRepository<NotificationPreference, UUID>, NotificationPreferenceRepository {}
