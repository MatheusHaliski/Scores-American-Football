package br.com.velion.scoresaf.application.usecase.rf41;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.common.i18n.LocalizedMessages;
import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.application.dto.AnalysisDtos;
import br.com.velion.scoresaf.application.port.out.persistence.AnalysisReportRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.service.DepthChartService;
import br.com.velion.scoresaf.application.service.GamePlanService;
import br.com.velion.scoresaf.application.usecase.rf42.MatchTeams;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.analysis.AnalysisReport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.engine.analyst.ClubAnalyst;
import br.com.velion.scoresaf.domain.engine.model.DepthChart;
import br.com.velion.scoresaf.domain.engine.model.DepthSlot;
import br.com.velion.scoresaf.domain.engine.model.PlayerSnapshot;
import br.com.velion.scoresaf.domain.engine.model.TeamSnapshot;
import br.com.velion.scoresaf.domain.match.ChartSlot;
import br.com.velion.scoresaf.domain.match.DepthChartPolicy;
import br.com.velion.scoresaf.domain.match.GamePlanSetup;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Analista do sistema ({@code AnalysisController} do basquete, aqui só por regras e sem provedor externo, D-104): o
 * {@code POST /analysis/refresh} monta as unidades de todas as franquias da liga (titulares do depth chart efetivo), os próximos 3 jogos
 * do usuário, as vagas do depth chart salvo e o plano de jogo salvo, roda o {@link ClubAnalyst} e grava o relatório; o
 * {@code GET /analysis} devolve o mais recente (404 ANALYSIS_NOT_FOUND antes do primeiro). O limite de pedidos é o do basquete: 6 por
 * minuto por IP nas rotas {@code /analysis} ({@code AuthRateLimitFilter}).
 */
@Service
public class AnalysisUseCases {
  private static final int UPCOMING = 3;

  private final CareerSupport support; private final AnalysisReportRepository reports; private final FixtureRepository fixtures; private final ClubRepository clubs;
  private final PlayerRepository players; private final DepthChartService depthCharts; private final GamePlanService plans; private final LocalizedMessages messages;
  private final AuditService audit;

  public AnalysisUseCases(CareerSupport support, AnalysisReportRepository reports, FixtureRepository fixtures, ClubRepository clubs, PlayerRepository players,
                          DepthChartService depthCharts, GamePlanService plans, LocalizedMessages messages, AuditService audit) {
    this.support = support; this.reports = reports; this.fixtures = fixtures; this.clubs = clubs; this.players = players; this.depthCharts = depthCharts;
    this.plans = plans; this.messages = messages; this.audit = audit;
  }

  @Transactional(readOnly = true)
  public AnalysisDtos.AnalysisDto get(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    AnalysisReport r = reports.findFirstByCareerIdOrderByCreatedAtDesc(save.getId())
        .orElseThrow(() -> new NotFoundException("ANALYSIS_NOT_FOUND", "Nenhum relatório do analista ainda: peça uma atualização", save.getId()));
    return dto(r, LocaleContextHolder.getLocale());
  }

  /** Relatório mais recente para a central, ou {@code null}. */
  public AnalysisDtos.AnalysisSummaryDto latestSummary(CareerSave save, Locale locale) {
    return reports.findFirstByCareerIdOrderByCreatedAtDesc(save.getId()).map(r -> {
      AnalysisDtos.AnalysisDto d = dto(r, locale);
      return new AnalysisDtos.AnalysisSummaryDto(d.id(), d.week(), d.generatedAt(), d.summaryCode(), d.summary(),
          d.upcoming().isEmpty() ? null : d.upcoming().get(0).winProbability());
    }).orElse(null);
  }

  @Transactional
  public AnalysisDtos.AnalysisDto refresh(UUID careerId, UUID userId) {
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    Season season = save.getSeason();
    if (season == null) throw new BusinessRuleException("NO_SEASON", "Carreira sem temporada");
    int actives = CareerSupport.rules(save).getGameDayActives();
    List<Club> league = clubs.findByLeagueIdOrderByNameAsc(season.getLeague().getId());
    Map<UUID, ClubAnalyst.Units> units = new HashMap<>();
    List<Player> ourRoster = players.findByClubIdOrderByOverallDesc(club.getId());
    Map<ChartSlot, List<UUID>> saved = depthCharts.read(depthCharts.getOrCreate(save, club, actives));
    for (Club c : league) {
      TeamSnapshot t = c.getId().equals(club.getId())
          ? MatchTeams.team(club, ourRoster, actives, DepthChartPolicy.toEngine(saved))
          : MatchTeams.team(c, players.findByClubIdOrderByOverallDesc(c.getId()), actives, depthCharts.forClub(c.getId()));
      units.put(c.getId(), units(t));
    }
    List<ClubAnalyst.Upcoming> upcoming = new ArrayList<>();
    for (Fixture f : fixtures.findBySeasonId(season.getId()).stream().sorted(CareerAssembler.FIXTURE_ORDER).toList()) {
      if (upcoming.size() >= UPCOMING) break;
      if (f.isFinished() || !f.involves(club.getId())) continue;
      boolean home = f.getHomeClub().getId().equals(club.getId());
      UUID opp = home ? f.getAwayClub().getId() : f.getHomeClub().getId();
      upcoming.add(new ClubAnalyst.Upcoming(f.getId().toString(), opp.toString(), f.getWeekNumber(), home, f.isNeutralSite(), units.get(opp)));
    }
    GamePlanSetup plan = plans.planOrCreate(save, club);
    ClubAnalyst.Report report = ClubAnalyst.analyze(units.get(club.getId()), new ArrayList<>(units.values()), upcoming, slots(saved, ourRoster, club),
        new ClubAnalyst.Plan(plan.getPassRatio(), plan.getDefensiveFocus().name(), plan.getFourthDownAggression().name()));
    AnalysisReport e = new AnalysisReport();
    e.setCareer(save); e.setSeason(season); e.setWeek(season.getCurrentWeek());
    try {
      e.setReportJson(Jsons.MAPPER.writeValueAsString(report));
    } catch (Exception ex) {
      throw new IllegalStateException("relatório do analista não serializou", ex);
    }
    reports.save(e);
    audit.ok(AuditActions.ANALYSIS_GENERATED, "AnalysisReport", e.getId(), "regras; semana " + e.getWeek() + "; " + report.summary());
    return dto(e, LocaleContextHolder.getLocale());
  }

  /** Média dos titulares por unidade no depth chart efetivo (cada jogador conta uma vez por unidade). */
  static ClubAnalyst.Units units(TeamSnapshot t) {
    DepthChart chart = t.resolvedDepthChart();
    Map<UUID, PlayerSnapshot> byId = new HashMap<>();
    t.players().forEach(p -> byId.put(p.id(), p));
    return new ClubAnalyst.Units(avg(chart, byId, DepthSlot.QB, 1), avg(chart, byId, DepthSlot.LT, 1, DepthSlot.LG, 1, DepthSlot.C, 1, DepthSlot.RG, 1, DepthSlot.RT, 1),
        avg(chart, byId, DepthSlot.RB, 1), avg(chart, byId, DepthSlot.WR, 3, DepthSlot.TE, 1), avg(chart, byId, DepthSlot.DE, 2, DepthSlot.DT, 2),
        avg(chart, byId, DepthSlot.OLB, 2, DepthSlot.MLB, 1), avg(chart, byId, DepthSlot.CB, 2, DepthSlot.FS, 1, DepthSlot.SS, 1),
        avg(chart, byId, DepthSlot.K, 1, DepthSlot.P, 1));
  }

  private static int avg(DepthChart chart, Map<UUID, PlayerSnapshot> byId, Object... slotsAndCounts) {
    Set<UUID> used = new LinkedHashSet<>();
    int sum = 0, n = 0;
    for (int i = 0; i < slotsAndCounts.length; i += 2) {
      DepthSlot slot = (DepthSlot) slotsAndCounts[i];
      int want = (Integer) slotsAndCounts[i + 1], taken = 0;
      for (UUID id : chart.get(slot)) {
        if (taken >= want) break;
        PlayerSnapshot p = byId.get(id);
        if (p == null || !used.add(id)) continue;
        sum += p.overallAt(slot.position() == null ? p.position() : slot.position());
        n++; taken++;
      }
    }
    return n == 0 ? 0 : (int) Math.round(sum / (double) n);
  }

  /** Vagas de posição do depth chart salvo com os titulares e os disponíveis do mesmo grupo de posição (ou com a posição secundária). */
  static List<ClubAnalyst.SlotInput> slots(Map<ChartSlot, List<UUID>> saved, List<Player> roster, Club club) {
    Map<UUID, Player> byId = new HashMap<>();
    List<Player> available = roster.stream().filter(p -> DepthChartPolicy.available(p, club.getId())).toList();
    available.forEach(p -> byId.put(p.getId(), p));
    Set<UUID> starters = new HashSet<>();
    for (ChartSlot s : ChartSlot.values()) {
      if (s.isRole() || s.minStarters() == 0) continue;
      List<UUID> ids = saved.getOrDefault(s, List.of());
      for (int i = 0; i < Math.min(s.minStarters(), ids.size()); i++) starters.add(ids.get(i));
    }
    List<ClubAnalyst.SlotInput> out = new ArrayList<>();
    for (ChartSlot s : ChartSlot.values()) {
      if (s.isRole() || s.minStarters() == 0) continue;
      List<UUID> ids = saved.getOrDefault(s, List.of());
      Set<UUID> mineHere = new HashSet<>();
      for (int i = 0; i < Math.min(s.minStarters(), ids.size()); i++) mineHere.add(ids.get(i));
      List<ClubAnalyst.Candidate> cands = new ArrayList<>();
      for (Player p : available) {
        int depth = ids.indexOf(p.getId()) + 1;
        boolean fits = p.getPosition().group() == s.position().group() || p.getSecondaryPosition() == s.position();
        if (depth == 0 && !fits) continue;
        cands.add(new ClubAnalyst.Candidate(s.name(), depth, p.getId().toString(), p.getName(), DepthChartPolicy.overallAt(p, s),
            starters.contains(p.getId()) && !mineHere.contains(p.getId())));
      }
      out.add(new ClubAnalyst.SlotInput(s.name(), s.minStarters(), cands));
    }
    return out;
  }

  AnalysisDtos.AnalysisDto dto(AnalysisReport e, Locale locale) {
    ClubAnalyst.Report r;
    try {
      r = Jsons.MAPPER.readValue(e.getReportJson(), ClubAnalyst.Report.class);
    } catch (Exception ex) {
      throw new IllegalStateException("relatório do analista " + e.getId() + " ilegível", ex);
    }
    Map<UUID, Club> byId = new HashMap<>();
    clubs.findByLeagueIdOrderByNameAsc(e.getSeason().getLeague().getId()).forEach(c -> byId.put(c.getId(), c));
    List<AnalysisDtos.UnitDto> units = new ArrayList<>();
    int[] o = r.units().values(), l = r.league().values();
    for (int i = 0; i < ClubAnalyst.UNITS.size(); i++) units.add(new AnalysisDtos.UnitDto(ClubAnalyst.UNITS.get(i), o[i], l[i], o[i] - l[i]));
    List<AnalysisDtos.OutlookDto> upcoming = r.upcoming().stream().map(u -> new AnalysisDtos.OutlookDto(UUID.fromString(u.fixtureId()), u.week(),
        CareerAssembler.club(byId.get(UUID.fromString(u.opponentId()))), u.home(), u.neutralSite(), u.strength(), u.opponentStrength(), u.winProbability(),
        u.verdict(), u.biggestThreat(), u.matchups().stream().map(m -> new AnalysisDtos.MatchupDto(m.key(), m.ours(), m.theirs(), m.edge(), m.advantage())).toList()))
        .toList();
    Object best = r.summaryParams().get("bestUnit"), worst = r.summaryParams().get("worstUnit");
    String opponent = upcoming.isEmpty() ? "" : upcoming.get(0).opponent().name();
    double p = upcoming.isEmpty() ? 0 : upcoming.get(0).winProbability();
    String summary = messages.get("analysis.summary." + r.summary(), r.summary(), locale, unit(best, locale), unit(worst, locale), opponent, p);
    return new AnalysisDtos.AnalysisDto(e.getId(), e.getSeason().getId(), e.getWeek(), e.getSource(), e.getCreatedAt(), r.summary(), summary, r.summaryParams(),
        r.strength(), r.leagueStrength(), units, r.strengths().stream().map(AnalysisUseCases::unit).toList(),
        r.weaknesses().stream().map(AnalysisUseCases::unit).toList(), upcoming,
        r.depthChart().stream().map(d -> new AnalysisDtos.DepthChangeDto(d.slot(), UUID.fromString(d.inPlayerId()), d.inName(), d.inOverall(),
            UUID.fromString(d.outPlayerId()), d.outName(), d.outOverall(), d.gain())).toList(),
        r.gamePlan().stream().map(a -> new AnalysisDtos.PlanAdviceDto(a.field(), a.current(), a.recommended(), a.reason())).toList());
  }

  private String unit(Object code, Locale locale) { return code == null ? "" : messages.get("analysis.unit." + code, code.toString(), locale); }

  private static AnalysisDtos.UnitDto unit(ClubAnalyst.Finding f) { return new AnalysisDtos.UnitDto(f.unit(), f.rating(), f.leagueAverage(), f.diff()); }
}
