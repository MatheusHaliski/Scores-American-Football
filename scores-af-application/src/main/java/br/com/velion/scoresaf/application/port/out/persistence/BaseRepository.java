package br.com.velion.scoresaf.application.port.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Porta de persistência base (um agregado por porta). Assinaturas idênticas às do Spring Data
 * ({@code XJpaRepository extends JpaRepository<X, UUID>, XRepository}) — a infraestrutura implementa as duas
 * sem ambiguidade de sobrecarga. {@code ID} é sempre {@code UUID} neste projeto.
 */
public interface BaseRepository<T, ID> {
  <S extends T> S save(S entity);
  <S extends T> List<S> saveAll(Iterable<S> entities);
  Optional<T> findById(ID id);
  boolean existsById(ID id);
  void deleteById(ID id);
  void delete(T entity);
  Page<T> findAll(Pageable pageable);
  List<T> findAllById(Iterable<ID> ids);
  long count();
}
