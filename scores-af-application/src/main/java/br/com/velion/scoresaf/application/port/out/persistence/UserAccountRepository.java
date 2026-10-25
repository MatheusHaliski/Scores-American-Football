package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.account.UserAccount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import br.com.velion.scoresaf.domain.account.UserStatus;

/** Contas (RF1/RF2/RF3). Busca por e-mail via hash SHA-256. */
public interface UserAccountRepository extends BaseRepository<UserAccount, UUID> {
  Optional<UserAccount> findByEmailHash(String emailHash);
  boolean existsByEmailHash(String emailHash);
  Page<UserAccount> findByStatus(UserStatus status, Pageable pageable);
  List<UserAccount> findByDeletionRequestedAtIsNotNull();
  /** RF46 CA07 — contas em prazo de arrependimento vencido (anonimização). */
  List<UserAccount> findByStatusAndDeletionRequestedAtBefore(UserStatus status, java.time.Instant instant);
  /** AdminBootstrap: existe algum administrador? */
  boolean existsByRole(br.com.velion.scoresaf.domain.account.UserRole role);
}
