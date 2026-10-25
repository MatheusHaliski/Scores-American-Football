package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.career.CareerSave;
import java.util.List;
import java.util.UUID;

/** Carreiras salvas (RF11, RF34). */
public interface CareerSaveRepository extends BaseRepository<CareerSave, UUID>, FlushingRepository<CareerSave> {
  List<CareerSave> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
