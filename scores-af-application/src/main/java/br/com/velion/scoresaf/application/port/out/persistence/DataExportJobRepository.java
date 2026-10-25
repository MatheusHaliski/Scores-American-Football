package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.account.DataExportJob;
import br.com.velion.scoresaf.domain.account.ExportStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pedidos de exportação de dados (RF46 CA07). */
public interface DataExportJobRepository extends BaseRepository<DataExportJob, UUID> {
  List<DataExportJob> findByUserIdOrderByRequestedAtDesc(UUID userId);
  List<DataExportJob> findByStatusAndExpiresAtBefore(ExportStatus status, Instant instant);
}
