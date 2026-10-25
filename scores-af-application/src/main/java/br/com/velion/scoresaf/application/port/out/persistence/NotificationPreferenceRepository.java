package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.account.NotificationPreference;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import br.com.velion.scoresaf.domain.account.NotificationKind;

/** Preferências de notificação (RF10/RF24). */
public interface NotificationPreferenceRepository extends BaseRepository<NotificationPreference, UUID> {
  List<NotificationPreference> findByUserId(UUID userId);
  Optional<NotificationPreference> findByUserIdAndType(UUID userId, NotificationKind type);
}
