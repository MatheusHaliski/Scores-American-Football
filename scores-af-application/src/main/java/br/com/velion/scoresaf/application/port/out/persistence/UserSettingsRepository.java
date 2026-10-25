package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.account.UserSettings;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Configurações do usuário (RF10). */
public interface UserSettingsRepository extends BaseRepository<UserSettings, UUID> {
  Optional<UserSettings> findByUserId(UUID userId);
}
