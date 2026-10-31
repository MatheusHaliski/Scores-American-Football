package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.PlayoffSeedRepository;
import br.com.velion.scoresaf.domain.season.PlayoffSeed;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data implementa a porta {@link PlayoffSeedRepository}; a exclusão em lote é um DELETE JPQL (grava o pendente antes). */
public interface PlayoffSeedJpaRepository extends JpaRepository<PlayoffSeed, UUID>, PlayoffSeedRepository {
  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from PlayoffSeed x where x.season.id in (select s.id from Season s where s.career.id = :id)")
  void deleteAllOfCareer(@Param("id") UUID id);
}
