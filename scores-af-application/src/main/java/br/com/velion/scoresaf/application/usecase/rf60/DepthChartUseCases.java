package br.com.velion.scoresaf.application.usecase.rf60;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.common.exception.ScoresException;
import br.com.velion.scoresaf.application.dto.DepthChartDtos;
import br.com.velion.scoresaf.application.port.out.persistence.DepthChartRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.service.DepthChartService;
import br.com.velion.scoresaf.application.usecase.rf42.MatchTeams;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.match.ChartSlot;
import br.com.velion.scoresaf.domain.match.DepthChart;
import br.com.velion.scoresaf.domain.match.DepthChartPolicy;
import br.com.velion.scoresaf.domain.shared.Unit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF60 — depth chart da franquia do usuário: leitura (vagas por unidade, overall na vaga, fora de posição, disponibilidade, erros e
 * avisos), gravação inteira com versão otimista (PUT; versão divergente ⇒ 409 DEPTH_CHART_VERSION_CONFLICT com a versão atual nos
 * detalhes) e definição rápida de um papel (KR, PR, holder, back de 3ª descida) a partir do elenco. Regras em {@link DepthChartPolicy}.
 * O motor passa a usar o salvo ({@code SimulateExhibitionMatchUseCase}, {@code MatchTeams}). O plano de jogo é só leitura aqui (6b
 * edita): o padrão do estilo do head coach.
 */
@Service
public class DepthChartUseCases {
  public record SlotCommand(String slot, List<UUID> playerIds) {}
  public record PutCommand(Long version, List<SlotCommand> slots) {}
  public record RoleCommand(String role, Integer depth, Long version) {}

  private final CareerSupport support; private final DepthChartService service; private final DepthChartRepository charts;
  private final PlayerRepository players; private final AuditService audit;

  public DepthChartUseCases(CareerSupport support, DepthChartService service, DepthChartRepository charts, PlayerRepository players, AuditService audit) {
    this.support = support; this.service = service; this.charts = charts; this.players = players; this.audit = audit;
  }

  @Transactional
  public DepthChartDtos.DepthChartDto get(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    Club club = support.userClub(save);
    LeagueRules rules = CareerSupport.rules(save);
    DepthChart dc = service.getOrCreate(save, club, rules.getGameDayActives());
    return dto(dc, club, service.read(dc), rules);
  }

  @Transactional
  public DepthChartDtos.DepthChartDto put(UUID careerId, UUID userId, PutCommand c) {
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    LeagueRules rules = CareerSupport.rules(save);
    DepthChart dc = service.getOrCreate(save, club, rules.getGameDayActives());
    checkVersion(dc, c.version());
    Map<ChartSlot, List<UUID>> chart = new EnumMap<>(ChartSlot.class);
    for (ChartSlot s : ChartSlot.values()) chart.put(s, new ArrayList<>());
    Set<ChartSlot> seen = new LinkedHashSet<>();
    for (SlotCommand sc : c.slots() == null ? List.<SlotCommand>of() : c.slots()) {
      ChartSlot slot = ChartSlot.parse(sc.slot());
      if (slot == null) throw new InvalidInputException("DEPTH_CHART_UNKNOWN_SLOT", "Vaga do depth chart desconhecida: " + sc.slot(), sc.slot());
      if (!seen.add(slot)) throw new InvalidInputException("DEPTH_CHART_DUPLICATE_SLOT", "A vaga " + slot + " aparece duas vezes", slot.name());
      chart.get(slot).addAll(sc.playerIds() == null ? List.of() : sc.playerIds());
    }
    save(dc, club, chart, rules, null);
    audit.ok(AuditActions.DEPTH_CHART_UPDATED, "DepthChart", dc.getId(), "versão " + dc.currentVersion());
    return dto(dc, club, service.read(dc), rules);
  }

  /** Coloca um jogador do elenco num papel (KR, PR, H, THIRD_DOWN_BACK) na profundidade pedida (1 = titular), sem mexer no resto. */
  @Transactional
  public DepthChartDtos.DepthChartDto role(UUID careerId, UUID userId, UUID playerId, RoleCommand c) {
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    ChartSlot slot = ChartSlot.parse(c.role());
    if (slot == null || !slot.isRole()) throw new InvalidInputException("DEPTH_CHART_UNKNOWN_SLOT", "Papel do depth chart desconhecido: " + c.role(), c.role());
    Player p = support.clubPlayer(save, playerId);
    LeagueRules rules = CareerSupport.rules(save);
    DepthChart dc = service.getOrCreate(save, club, rules.getGameDayActives());
    if (c.version() != null) checkVersion(dc, c.version());
    Map<ChartSlot, List<UUID>> chart = service.read(dc);
    List<UUID> ids = chart.get(slot);
    ids.remove(p.getId());
    int depth = c.depth() == null ? 1 : c.depth();
    if (depth < 1 || depth > ChartSlot.MAX_DEPTH) throw new InvalidInputException("INVALID_DEPTH", "Profundidade inválida: " + depth, depth, ChartSlot.MAX_DEPTH);
    ids.add(Math.min(depth - 1, ids.size()), p.getId());
    save(dc, club, chart, rules, slot);
    audit.ok(AuditActions.DEPTH_CHART_UPDATED, "DepthChart", dc.getId(), slot + " " + depth + " = " + p.getId());
    return dto(dc, club, service.read(dc), rules);
  }

  @Transactional(readOnly = true)
  public DepthChartDtos.GamePlanDto gamePlan(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    Club club = support.userClub(save);
    GamePlan g = MatchTeams.plan(club.getManager());
    return new DepthChartDtos.GamePlanDto(g.passRatio(), g.fourthDownAggression().name(), g.tempo().name(), g.defensiveFocus().name(),
        g.blitzFrequency().name(), g.baseDefense().name(), club.getManager() == null ? null : club.getManager().getCoachStyle().name(), "COACH_STYLE", false);
  }

  private static void checkVersion(DepthChart dc, Long expected) {
    long current = dc.currentVersion();
    if (expected == null || expected != current) {
      ScoresException e = new ConflictException("DEPTH_CHART_VERSION_CONFLICT", "O depth chart foi alterado em outra sessão", expected, current);
      throw e.withDetail("currentVersion", current).withDetail("expectedVersion", expected);
    }
  }

  /**
   * Valida e grava. {@code onlySlot} (definição de papel): só barra erros daquela vaga ou do limite de ativos — um depth chart que já
   * estava incompleto por outro motivo não impede trocar o retornador.
   */
  private void save(DepthChart dc, Club club, Map<ChartSlot, List<UUID>> chart, LeagueRules rules, ChartSlot onlySlot) {
    Map<UUID, Player> lookup = lookup(club, chart);
    DepthChartPolicy.Result r = DepthChartPolicy.validate(chart, lookup, club.getId(), rules.getGameDayActives());
    List<DepthChartPolicy.Issue> blocking = r.errors().stream()
        .filter(i -> onlySlot == null || i.slot() == onlySlot || DepthChartPolicy.TOO_MANY_ACTIVES.equals(i.code())).toList();
    if (!blocking.isEmpty()) {
      DepthChartPolicy.Issue first = blocking.get(0);
      ScoresException e = new BusinessRuleException(first.code(), "Depth chart inválido: " + first.code(),
          first.slot() == null ? String.valueOf(rules.getGameDayActives()) : first.slot().name(), first.depth() == null ? "" : first.depth(),
          first.slot() == null ? "" : first.slot().minStarters());
      throw e.withDetail("errors", issues(blocking)).withDetail("warnings", issues(r.warnings()));
    }
    service.write(dc, chart, lookup);
    dc.touch(r.valid());
    charts.saveAndFlush(dc);
  }

  /** Jogadores da franquia mais os ids de fora (para dizer que estão indisponíveis com nome, sem aceitar). */
  private Map<UUID, Player> lookup(Club club, Map<ChartSlot, List<UUID>> chart) {
    Map<UUID, Player> m = DepthChartService.byId(players.findByClubIdOrderByOverallDesc(club.getId()));
    Set<UUID> missing = new LinkedHashSet<>();
    chart.values().forEach(ids -> ids.stream().filter(id -> id != null && !m.containsKey(id)).forEach(missing::add));
    if (!missing.isEmpty()) players.findAllById(missing).forEach(p -> m.put(p.getId(), p));
    return m;
  }

  private static List<DepthChartDtos.IssueDto> issues(List<DepthChartPolicy.Issue> list) {
    return list.stream().map(i -> new DepthChartDtos.IssueDto(i.code(), i.slot() == null ? null : i.slot().name(), i.depth(), i.playerId())).toList();
  }

  private DepthChartDtos.DepthChartDto dto(DepthChart dc, Club club, Map<ChartSlot, List<UUID>> chart, LeagueRules rules) {
    Map<UUID, Player> lookup = lookup(club, chart);
    DepthChartPolicy.Result r = DepthChartPolicy.validate(chart, lookup, club.getId(), rules.getGameDayActives());
    List<DepthChartDtos.UnitDto> units = new ArrayList<>();
    for (Unit u : Unit.values()) {
      List<DepthChartDtos.SlotDto> slots = new ArrayList<>();
      for (ChartSlot s : ChartSlot.values()) {
        if (s.unit() != u) continue;
        List<DepthChartDtos.EntryDto> entries = new ArrayList<>();
        List<UUID> ids = chart.getOrDefault(s, List.of());
        for (int i = 0; i < ids.size(); i++) {
          Player p = lookup.get(ids.get(i));
          if (p == null) continue;
          int natural = p.ratingAt(p.getPosition()).overall();
          entries.add(new DepthChartDtos.EntryDto(i + 1, p.getId(), p.getName(), p.getJerseyNumber(), p.getPosition().name(), DepthChartPolicy.overallAt(p, s),
              natural, p.ratingAt(p.getPosition()).tier().name(), DepthChartPolicy.outOfPosition(p, s), DepthChartPolicy.available(p, club.getId()),
              p.getRosterStatus().name()));
        }
        slots.add(new DepthChartDtos.SlotDto(s.name(), s.position() == null ? null : s.position().name(), s.isRole(), s.minStarters(), entries));
      }
      units.add(new DepthChartDtos.UnitDto(u.name(), slots));
    }
    return new DepthChartDtos.DepthChartDto(dc.getId(), club.getId(), dc.currentVersion(), r.valid(), dc.getUpdatedAt(), rules.getGameDayActives(),
        r.uniquePlayers(), units, issues(r.errors()), issues(r.warnings()));
  }
}
