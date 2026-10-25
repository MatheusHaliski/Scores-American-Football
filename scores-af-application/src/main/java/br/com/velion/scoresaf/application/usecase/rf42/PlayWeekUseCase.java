package br.com.velion.scoresaf.application.usecase.rf42;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.dto.MatchDtos;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSettingsRepository;
import br.com.velion.scoresaf.application.service.DepthChartService;
import br.com.velion.scoresaf.application.service.MatchService;
import br.com.velion.scoresaf.application.service.MatchSetupAssembler;
import br.com.velion.scoresaf.application.service.WeekProcessor;
import br.com.velion.scoresaf.application.usecase.rf60.DepthChartUseCases;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.account.UserSettings;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.match.DepthChart;
import br.com.velion.scoresaf.domain.match.DepthChartPolicy;
import br.com.velion.scoresaf.domain.match.MatchSession;
import br.com.velion.scoresaf.domain.match.MatchSessionStatus;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.FixtureStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF27 / RF7 — jogar a semana atual ({@code /rounds/current/play} do basquete → {@code /weeks/current/play}): confere o depth chart
 * (422 DEPTH_CHART_INVALID com {@code errors[]}), cria a partida ao vivo do usuário (MatchSession com o snapshot inicial do motor,
 * semente do jogo, nível de chamada de jogada da conta congelado) e devolve o painel da semana. Os outros jogos da semana são
 * simulados quando a semana fecha (ao fim da partida do usuário); semana de folga fecha na hora. Idempotente: partida já criada
 * não é recriada. Fim da temporada regular: 409 NO_NEXT_GAME. Dois pedidos juntos da mesma carreira rodam um depois do outro (a
 * carreira é travada antes de tudo, D-91): o segundo encontra a partida criada pelo primeiro em vez de criar outra.
 */
@Service
public class PlayWeekUseCase {
  private final CareerSupport support; private final MatchSupport matches; private final FixtureRepository fixtures; private final MatchSessionRepository sessions;
  private final PlayerRepository players; private final ClubRepository clubs; private final UserSettingsRepository settings; private final DepthChartService depthCharts;
  private final MatchSetupAssembler assembler; private final MatchService matchService; private final WeekProcessor weekProcessor; private final AuditService audit;
  private final CareerSaveRepository saves;

  public PlayWeekUseCase(CareerSupport support, MatchSupport matches, FixtureRepository fixtures, MatchSessionRepository sessions, PlayerRepository players,
                         ClubRepository clubs, UserSettingsRepository settings, DepthChartService depthCharts, MatchSetupAssembler assembler,
                         MatchService matchService, WeekProcessor weekProcessor, AuditService audit, CareerSaveRepository saves) {
    this.support = support; this.matches = matches; this.fixtures = fixtures; this.sessions = sessions; this.players = players; this.clubs = clubs;
    this.settings = settings; this.depthCharts = depthCharts; this.assembler = assembler; this.matchService = matchService; this.weekProcessor = weekProcessor;
    this.audit = audit; this.saves = saves;
  }

  @Transactional
  public MatchDtos.WeekBoardDto execute(UUID careerId, UUID userId) {
    // primeira leitura da transação: trava a carreira (o dono é conferido logo abaixo; outro usuário ou id inexistente desfaz tudo)
    saves.findByIdForUpdate(careerId);
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    Season season = save.getSeason();
    if (season == null) throw new BusinessRuleException("NO_SEASON", "Carreira sem temporada");
    int week = season.getCurrentWeek();
    if (week > season.getRegularSeasonWeeks()) throw new ConflictException("NO_NEXT_GAME", "A temporada regular terminou: não há próximo jogo");
    Fixture mine = fixtures.findBySeasonIdAndWeekNumber(season.getId(), week).stream().filter(f -> f.involves(club.getId())).findFirst().orElse(null);
    if (mine != null && !mine.isFinished()) {
      if (sessions.findByFixtureId(mine.getId()).isEmpty()) start(save, club, mine, userId);
    } else {
      weekProcessor.close(save, season, week);
    }
    return matches.board(save, season, week, clubs.findByLeagueIdOrderByNameAsc(season.getLeague().getId()));
  }

  private void start(CareerSave save, Club club, Fixture f, UUID userId) {
    LeagueRules rules = CareerSupport.rules(save);
    DepthChart dc = depthCharts.getOrCreate(save, club, rules.getGameDayActives());
    DepthChartPolicy.Result r = DepthChartPolicy.validate(depthCharts.read(dc), DepthChartService.byId(players.findByClubIdOrderByOverallDesc(club.getId())),
        club.getId(), rules.getGameDayActives());
    if (!r.valid()) {
      DepthChartPolicy.Issue first = r.errors().get(0);
      throw new BusinessRuleException("DEPTH_CHART_INVALID", "Depth chart inválido para o jogo: " + first.code(), first.code())
          .withDetail("errors", DepthChartUseCases.issues(r.errors())).withDetail("warnings", DepthChartUseCases.issues(r.warnings()));
    }
    PlayCallingLevel level = settings.findByUserId(userId).map(UserSettings::getPlayCallingLevel).orElse(PlayCallingLevel.KEY_MOMENTS);
    MatchSetup setup = assembler.build(save, f, level);
    MatchSnapshot snap = matchService.simulator().start(setup);
    MatchSession s = new MatchSession();
    s.setFixture(f); s.setCareer(save); s.setStatus(MatchSessionStatus.NOT_STARTED); s.setPlayCallingLevel(level); s.setSeed(setup.seed());
    s.setEngineVersion(snap.engineVersion());
    matches.store(s, snap);
    f.setStatus(FixtureStatus.LIVE);
    fixtures.save(f);
    audit.ok(AuditActions.MATCH_STARTED, "MatchSession", s.getId(), "seed=" + setup.seed() + " fixture=" + f.getId() + " nível=" + level);
  }
}
