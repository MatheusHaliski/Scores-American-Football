package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.career.CareerSave;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Carreiras salvas (RF11, RF34). */
public interface CareerSaveRepository extends BaseRepository<CareerSave, UUID>, FlushingRepository<CareerSave> {
  List<CareerSave> findByUserIdOrderByCreatedAtAsc(UUID userId);
  /**
   * Carreira travada para escrita ({@code SELECT … FOR UPDATE}) até o fim da transação: dois {@code POST /weeks/current/play} da
   * mesma carreira rodam um depois do outro (D-91). Deve ser a primeira leitura da transação.
   */
  Optional<CareerSave> findByIdForUpdate(UUID id);
}
