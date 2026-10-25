package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.DataExportJobRepository;
import br.com.velion.scoresaf.domain.account.DataExportJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link DataExportJobRepository}. */
public interface DataExportJobJpaRepository extends JpaRepository<DataExportJob, UUID>, DataExportJobRepository {}
