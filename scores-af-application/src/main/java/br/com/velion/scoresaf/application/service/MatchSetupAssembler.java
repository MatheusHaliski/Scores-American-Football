package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.usecase.rf42.MatchTeams;
import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.shared.WeekKind;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Monta o {@link MatchSetup} de um jogo do calendário (RF27/RF42): elencos e depth charts (o salvo da franquia do usuário, RF60; o
 * automático nas da IA), regras da liga e clima do estádio do mandante ({@link MatchTeams}), campo neutro do jogo e a semente do jogo
 * ({@code Fixture.seed}, derivada da semente da carreira: mesma carreira ⇒ mesmo jogo). Na franquia do usuário entram o plano de
 * jogo e o playbook salvos (RF61) e, no jogo dele ao vivo, o nível de chamada de jogada da conta (RF10/RF62), congelado no kickoff. Jogo
 * de playoff é eliminatório ({@code knockout}: prorrogação até sair o vencedor, D-40).
 */
@Component
public class MatchSetupAssembler {
  private final PlayerRepository players; private final DepthChartService depthCharts; private final GamePlanService plans;

  public MatchSetupAssembler(PlayerRepository players, DepthChartService depthCharts, GamePlanService plans) {
    this.players = players; this.depthCharts = depthCharts; this.plans = plans;
  }

  /** Jogo de playoff (semana de rodada eliminatória): o motor não deixa terminar empatado (D-40, D-96). */
  public static boolean knockout(Fixture f) {
    WeekKind k = f.getWeek() == null ? WeekKind.REGULAR : f.getWeek().getKind();
    return k != WeekKind.REGULAR && k != WeekKind.PRESEASON;
  }

  /** Jogo simulado inteiro pela IA (nenhuma decisão é aberta), com o plano salvo do usuário se a franquia dele jogar. */
  public MatchSetup build(CareerSave save, Fixture f) { return build(save, f, null); }

  /** {@code level} não nulo: partida ao vivo do usuário, que abre decisões para a franquia dele nesse nível. */
  public MatchSetup build(CareerSave save, Fixture f, PlayCallingLevel level) {
    Club home = f.getHomeClub(), away = f.getAwayClub();
    MatchSetup s = MatchTeams.setup(f.getId(), home, players.findByClubIdOrderByOverallDesc(home.getId()), depthCharts.forClub(home.getId()),
        away, players.findByClubIdOrderByOverallDesc(away.getId()), depthCharts.forClub(away.getId()), f.getSeed());
    s = new MatchSetup(s.fixtureId(), s.home(), s.away(), s.rules(), s.weather(), s.seed(), s.playCallingLevel(), null, f.isNeutralSite(), knockout(f));
    UUID user = save.getClub() == null ? null : save.getClub().getId();
    if (user != null && f.involves(user)) {
      s = s.withTeamPlan(user, plans.engineFor(save, save.getClub()));
      if (level != null) s = s.withUser(user, level);
    }
    return s;
  }
}
