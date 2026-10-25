package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.season.Season;
import java.util.List;
import java.util.UUID;

/** Temporadas das carreiras (RF16, RF35). */
public interface SeasonRepository extends BaseRepository<Season, UUID> {
  List<Season> findByCareerIdOrderByNumberAsc(UUID careerId);
  /** Exclusão em lote (a infraestrutura executa um DELETE só, depois de gravar o que está pendente). */
  void deleteByCareerId(UUID careerId);
}
