package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.UserSettingsRepository;
import br.com.velion.scoresaf.domain.account.UserSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link UserSettingsRepository}. */
public interface UserSettingsJpaRepository extends JpaRepository<UserSettings, UUID>, UserSettingsRepository {}
