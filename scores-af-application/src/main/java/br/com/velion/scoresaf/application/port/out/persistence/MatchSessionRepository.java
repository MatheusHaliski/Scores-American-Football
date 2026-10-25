package br.com.velion.scoresaf.application.port.out.persistence;

import br.com.velion.scoresaf.domain.match.MatchSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Partidas ao vivo do usuário (RF27/RF29/RF62). */
public interface MatchSessionRepository extends BaseRepository<MatchSession, UUID>, FlushingRepository<MatchSession> {
  Optional<MatchSession> findByFixtureId(UUID fixtureId);
  /**
   * Sessão travada para escrita ({@code SELECT … FOR UPDATE}) até o fim da transação: duas escritas na mesma partida (dois
   * {@code FULL} com a mesma versão) rodam uma depois da outra, e a segunda lê a sessão já gravada pela primeira (D-91). Deve ser a
   * primeira leitura da transação, para nada da sessão vir antes da trava.
   */
  Optional<MatchSession> findByIdForUpdate(UUID id);
  List<MatchSession> findByCareerIdOrderByCreatedAtAsc(UUID careerId);
  /** Exclusão em lote. */
  void deleteByCareerId(UUID careerId);
}
