package br.com.velion.scoresaf.application.port.out.persistence;

/**
 * Gravação imediata (mesma assinatura do Spring Data): uma violação de unicidade aparece na chamada, onde o caso de uso a
 * traduz em conflito (409), e não no commit.
 */
public interface FlushingRepository<T> {
  <S extends T> S saveAndFlush(S entity);
}
