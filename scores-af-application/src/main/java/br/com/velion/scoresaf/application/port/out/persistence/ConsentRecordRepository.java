package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.account.ConsentPurpose;
import br.com.velion.scoresaf.domain.account.ConsentRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Registros de consentimento (RF46 CA03) — append-only. */
public interface ConsentRecordRepository extends BaseRepository<ConsentRecord, UUID> {
  List<ConsentRecord> findByUserIdOrderByRecordedAtDesc(UUID userId);
  Optional<ConsentRecord> findFirstByUserIdAndPurposeOrderByRecordedAtDesc(UUID userId, ConsentPurpose purpose);
}
