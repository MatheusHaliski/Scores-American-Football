package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.DepthChartRepository;
import br.com.velion.scoresaf.application.port.out.persistence.DepthChartSlotRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.GamePlanSetupRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchReportRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlaybookSetupRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlaybookWeightRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerMatchStatsRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exclusão definitiva de uma carreira (RF11 DELETE, RF46 eliminação da conta): box score dos jogadores, relatórios, partidas ao
 * vivo (com o snapshot e o log de decisões), playbook, plano de jogo (bloco 6b), depth chart, movimentos do elenco, calendário,
 * temporadas, o save e o mundo SAVE inteiro, nessa ordem (filhos antes dos pais; FKs em ForeignKeyPolicy). Projeções de outros
 * bancos (play-by-play, inbox, cache) entram aqui quando existirem.
 */
@Service
public class CareerDeletion {
  private final CareerSaveRepository saves; private final SeasonRepository seasons; private final WeekRepository weeks;
  private final FixtureRepository fixtures; private final RosterMoveRepository moves; private final DepthChartRepository charts;
  private final DepthChartSlotRepository slots; private final WorldGraphStore graph; private final MatchSessionRepository sessions;
  private final MatchReportRepository reports; private final PlayerMatchStatsRepository stats; private final GamePlanSetupRepository plans;
  private final PlaybookSetupRepository playbooks; private final PlaybookWeightRepository weights;

  public CareerDeletion(CareerSaveRepository saves, SeasonRepository seasons, WeekRepository weeks, FixtureRepository fixtures, RosterMoveRepository moves,
                        DepthChartRepository charts, DepthChartSlotRepository slots, WorldGraphStore graph, MatchSessionRepository sessions,
                        MatchReportRepository reports, PlayerMatchStatsRepository stats, GamePlanSetupRepository plans, PlaybookSetupRepository playbooks,
                        PlaybookWeightRepository weights) {
    this.saves = saves; this.seasons = seasons; this.weeks = weeks; this.fixtures = fixtures; this.moves = moves; this.charts = charts;
    this.slots = slots; this.graph = graph; this.sessions = sessions; this.reports = reports; this.stats = stats; this.plans = plans;
    this.playbooks = playbooks; this.weights = weights;
  }

  /** Tudo em DELETEs em lote por carreira (nada de filho carregado no contexto apontando para o save apagado). */
  @Transactional
  public void delete(CareerSave save) {
    World world = save.getWorld();
    save.setSeason(null);
    saves.saveAndFlush(save);
    stats.deleteByCareerId(save.getId());
    reports.deleteByCareerId(save.getId());
    sessions.deleteByCareerId(save.getId());
    weights.deleteAllOfCareer(save.getId());
    playbooks.deleteByCareerId(save.getId());
    plans.deleteByCareerId(save.getId());
    slots.deleteAllOfCareer(save.getId());
    charts.deleteByCareerId(save.getId());
    moves.deleteByCareerId(save.getId());
    fixtures.deleteAllOfCareer(save.getId());
    weeks.deleteAllOfCareer(save.getId());
    seasons.deleteByCareerId(save.getId());
    saves.delete(save);
    if (world != null && world.getKind() == WorldKind.SAVE) graph.delete(world);
  }
}
