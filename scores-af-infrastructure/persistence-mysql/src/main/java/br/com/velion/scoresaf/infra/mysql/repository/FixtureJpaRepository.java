package br.com.velion.scoresaf.infra.mysql.repository;

import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.domain.season.Fixture;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data implementa a porta {@link FixtureRepository}; a exclusão em lote é um DELETE JPQL (grava o pendente antes). */
public interface FixtureJpaRepository extends JpaRepository<Fixture, UUID>, FixtureRepository {
  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from Fixture x where x.season.id = :id")
  void deleteBySeasonId(@Param("id") UUID id);

  @Override
  @Modifying(flushAutomatically = true)
  @Query("delete from Fixture x where x.season.id in (select s.id from Season s where s.career.id = :id)")
  void deleteAllOfCareer(@Param("id") UUID id);
}
