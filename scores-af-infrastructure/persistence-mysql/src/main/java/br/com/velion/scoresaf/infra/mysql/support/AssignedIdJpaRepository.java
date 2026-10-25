package br.com.velion.scoresaf.infra.mysql.support;

import br.com.velion.scoresaf.domain.common.AuditableEntity;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repositório base de todas as portas (D-9). As entidades nascem com o UUID atribuído ({@link AuditableEntity}); sem isto o
 * Spring Data veria o id preenchido, trataria toda entidade nova como existente e faria {@code merge}: a instância passada
 * continuaria transiente e os metadados de {@code @PrePersist} iriam para a cópia. No basquete a entidade implementava o
 * {@code Persistable} do Spring Data; aqui o domínio não importa Spring, então a decisão entre {@code persist} e {@code merge}
 * lê {@link AuditableEntity#isNew()} neste ponto da infraestrutura.
 */
public class AssignedIdJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> {
  private final EntityManager em;

  public AssignedIdJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
    super(entityInformation, entityManager);
    this.em = entityManager;
  }

  @Override
  @Transactional
  public <S extends T> S save(S entity) {
    if (entity instanceof AuditableEntity e && e.isNew()) {
      em.persist(entity);
      return entity;
    }
    return super.save(entity);
  }
}
