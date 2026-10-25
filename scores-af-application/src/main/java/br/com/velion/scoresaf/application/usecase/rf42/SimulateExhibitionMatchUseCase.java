package br.com.velion.scoresaf.application.usecase.rf42;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.sim.DefaultMatchSimulator;
import br.com.velion.scoresaf.domain.engine.sim.MatchSimulator;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jogo inteiro entre duas franquias do mesmo mundo, IA × IA, sem gravar nada (bloco 5: prova a integração do motor com o catálogo
 * do bloco 4). A partida ao vivo com MatchSession, decisões e rotas é do bloco 6. Mesma semente ⇒ mesmo resultado.
 */
@Component
public class SimulateExhibitionMatchUseCase {
  private final ClubRepository clubs;
  private final PlayerRepository players;
  private final SavedDepthCharts depthCharts;
  private final MatchSimulator simulator = new DefaultMatchSimulator();

  public SimulateExhibitionMatchUseCase(ClubRepository clubs, PlayerRepository players) { this(clubs, players, SavedDepthCharts.NONE); }

  /** Com o depth chart salvo das franquias (RF60): o motor usa o salvo quando existe. */
  @org.springframework.beans.factory.annotation.Autowired
  public SimulateExhibitionMatchUseCase(ClubRepository clubs, PlayerRepository players, SavedDepthCharts depthCharts) {
    this.clubs = clubs;
    this.players = players;
    this.depthCharts = depthCharts;
  }

  @Transactional(readOnly = true)
  public MatchResult execute(UUID homeClubId, UUID awayClubId, long seed) {
    if (homeClubId.equals(awayClubId)) throw new BusinessRuleException("MATCH_SAME_CLUB", "Uma franquia não joga contra si mesma");
    Club home = clubs.findById(homeClubId).orElseThrow(() -> new NotFoundException("Franquia", homeClubId));
    Club away = clubs.findById(awayClubId).orElseThrow(() -> new NotFoundException("Franquia", awayClubId));
    if (home.getWorld() == null || away.getWorld() == null || !home.getWorld().getId().equals(away.getWorld().getId())) {
      throw new BusinessRuleException("MATCH_DIFFERENT_WORLDS", "As duas franquias precisam ser do mesmo mundo");
    }
    MatchSetup setup = MatchTeams.setup(fixtureId(homeClubId, awayClubId, seed), home, players.findByClubIdOrderByOverallDesc(homeClubId),
        depthCharts.forClub(homeClubId), away, players.findByClubIdOrderByOverallDesc(awayClubId), depthCharts.forClub(awayClubId), seed);
    return simulator.simulate(setup);
  }

  /** Id estável da partida de exibição (mesmos clubes e semente ⇒ mesmo id). */
  static UUID fixtureId(UUID home, UUID away, long seed) {
    return UUID.nameUUIDFromBytes((home + ":" + away + ":" + seed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
