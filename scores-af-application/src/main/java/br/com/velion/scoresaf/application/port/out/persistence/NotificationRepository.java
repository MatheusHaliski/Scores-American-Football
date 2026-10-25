package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.inbox.Notification;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Notificações (RF24). */
public interface NotificationRepository extends BaseRepository<Notification, UUID> {
  Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
  List<Notification> findByUserIdAndReadFalse(UUID userId);
  long countByUserIdAndReadFalse(UUID userId);
  List<Notification> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);
}
