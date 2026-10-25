package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.SeasonRepository;
import br.com.velion.scoresaf.domain.season.Season;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data implementa a porta {@link SeasonRepository}; a exclusão em lote é um DELETE JPQL (grava o pendente antes). */
public interface SeasonJpaRepository extends JpaRepository<Season, UUID>, SeasonRepository {
  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from Season x where x.career.id = :id")
  void deleteByCareerId(@Param("id") UUID id);
}
