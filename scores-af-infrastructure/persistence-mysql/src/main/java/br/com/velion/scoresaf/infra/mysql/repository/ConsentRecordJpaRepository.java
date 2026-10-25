package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.ConsentRecordRepository;
import br.com.velion.scoresaf.domain.account.ConsentRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementa a porta {@link ConsentRecordRepository}. */
public interface ConsentRecordJpaRepository extends JpaRepository<ConsentRecord, UUID>, ConsentRecordRepository {}
