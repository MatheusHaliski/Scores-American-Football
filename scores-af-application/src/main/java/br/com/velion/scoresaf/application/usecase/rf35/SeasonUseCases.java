package br.com.velion.scoresaf.application.usecase.rf35;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.common.exception.ScoresException;
import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.dto.SeasonDtos;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonRepository;
import br.com.velion.scoresaf.application.service.AiContractRenewal;
import br.com.velion.scoresaf.application.service.CapSheet;
import br.com.velion.scoresaf.application.service.DepthChartService;
import br.com.velion.scoresaf.application.service.MoraleService;
import br.com.velion.scoresaf.application.service.OffseasonPhase;
import br.com.velion.scoresaf.application.service.SeasonScheduler;
import br.com.velion.scoresaf.application.service.SeasonStatsService;
import br.com.velion.scoresaf.application.service.WaiverWire;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.PlayerProgression;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.engine.morale.PlayerMoraleEngine;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.SeasonAwards;
import br.com.velion.scoresaf.domain.season.Standings;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF35 — fim de temporada: o resumo ({@code GET /season/summary}: campeã, campanha do usuário, prêmios e líderes, entressafra) e o
 * avanço ({@code POST /season/advance}, D-99), que fecha a temporada e abre a seguinte na ordem da offseason da D-10:
 *
 * <ol>
 *   <li>Waivers pendentes resolvidos (Q-39) e a moral final do elenco guardada (volta 30 % para 60, D-102).</li>
 *   <li>Renovações: as franquias da IA renovam, por regra determinística, o necessário para voltar a 53 + practice squad cheio
 *   ({@link AiContractRenewal}, Q-42, D-120); a do usuário não renova sozinha. Quem não renova vira agente livre ({@code FREE_AGENT},
 *   sem franquia) com o movimento {@code CONTRACT_EXPIRED} (a franquia de origem fica no movimento: é a pendência de renovação do bloco
 *   7, D-100). Os demais contratos seguem: o ano do contrato é o da temporada, então os anos restantes caem 1 sozinhos.</li>
 *   <li>Free agency (bloco 7a, D-136): depois do calendário novo, a janela abre na temporada nova (fase FREE_AGENCY, dia 0; a IA
 *   contrata e a janela fecha pelos dias de {@code POST /free-agency/advance}). Draft: gancho do bloco 7b ({@link OffseasonPhase}),
 *   hoje pulado.</li>
 *   <li>Pré-temporada: envelhecimento e progressão pelos atributos, sem sorteio ({@link PlayerProgression}, D-101), experiência +1 para
 *   quem estava numa franquia, lesões e suspensões zeradas; calendário novo pelo {@link SeasonScheduler} com a colocação final das
 *   divisões; o depth chart do usuário perde quem saiu (e volta ao padrão se ficar inválido). O dead money que a regra do 1º de junho
 *   jogou para a temporada seguinte (D-79) passa a contar na nova temporada ({@link CapSheet}).</li>
 * </ol>
 * Só depois da final (422 SEASON_NOT_FINISHED); a carreira é travada antes de tudo e o pedido pode dizer qual temporada quer fechar
 * ({@code seasonId}): temporada já fechada responde 409 SEASON_ALREADY_ADVANCED, sem criar outra.
 */
@Service
public class SeasonUseCases {
  /** Dia e mês de referência da idade na temporada (como o mundo semente: 1º de setembro). */
  static final int REFERENCE_MONTH = 9, REFERENCE_DAY = 1;

  private final CareerSupport support; private final SeasonRepository seasons; private final CareerSaveRepository saves; private final PlayerRepository players;
  private final RosterMoveRepository moves; private final SeasonResults results; private final SeasonStatsService stats; private final SeasonScheduler scheduler;
  private final WaiverWire waivers; private final MoraleService morale; private final DepthChartService depthCharts; private final List<OffseasonPhase> phases;
  private final AuditService audit; private final AiContractRenewal aiRenewal; private final CapSheet cap;

  public SeasonUseCases(CareerSupport support, SeasonRepository seasons, CareerSaveRepository saves, PlayerRepository players, RosterMoveRepository moves,
                        SeasonResults results, SeasonStatsService stats, SeasonScheduler scheduler, WaiverWire waivers, MoraleService morale,
                        DepthChartService depthCharts, List<OffseasonPhase> phases, AuditService audit, AiContractRenewal aiRenewal,
                        CapSheet cap) {
    this.support = support; this.seasons = seasons; this.saves = saves; this.players = players; this.moves = moves; this.results = results;
    this.stats = stats; this.scheduler = scheduler; this.waivers = waivers; this.morale = morale; this.depthCharts = depthCharts; this.phases = phases;
    this.audit = audit; this.aiRenewal = aiRenewal; this.cap = cap;
  }

  // ================================================================ resumo

  @Transactional(readOnly = true)
  public SeasonDtos.SeasonSummaryDto summary(UUID careerId, UUID userId, Integer number) {
    CareerSave save = support.owned(careerId, userId);
    Season current = save.getSeason();
    if (current == null) throw new BusinessRuleException("NO_SEASON", "Carreira sem temporada");
    Season season = number == null ? current : seasons.findByCareerIdOrderByNumberAsc(save.getId()).stream().filter(s -> s.getNumber() == number)
        .findFirst().orElseThrow(() -> new NotFoundException("Temporada", number));
    LeagueRules rules = CareerSupport.rules(save);
    SeasonResults.View v = results.view(season, rules);
    UUID user = save.getClub() == null ? null : save.getClub().getId();
    SeasonDtos.CampaignDto campaign = null;
    if (user != null && v.table().line(user.toString()) != null) {
      Standings.Line l = v.table().line(user.toString());
      Club club = SeasonResults.club(v, user);
      campaign = new SeasonDtos.CampaignDto(SeasonResults.record(v, user), l.pointsFor(), l.pointsAgainst(),
          club.getDivision() == null ? null : SeasonResults.rank(v.table().divisions().get(club.getDivision().getId().toString()), user),
          club.getConference() == null ? null : SeasonResults.rank(v.table().conferences().get(club.getConference().getId().toString()), user),
          v.seedOf(user), v.playoffResult(user));
    }
    List<SeasonAwards.PlayerLine> lines = stats.lines(season);
    boolean provisional = v.awards().isEmpty();
    List<SeasonDtos.AwardDto> awards;
    if (provisional) {
      awards = SeasonAwards.compute(lines, SeasonStatsService.teams(v.league(), v.table()), season.getGamesPerTeam()).stream()
          .map(a -> new SeasonDtos.AwardDto(a.type().name(), a.playerId() == null ? null : UUID.fromString(a.playerId()), a.name(), a.position(),
              CareerAssembler.club(SeasonResults.club(v, UUID.fromString(a.clubId()))), a.score(), a.statLine())).toList();
    } else {
      awards = v.awards().stream().sorted(Comparator.comparingInt(a -> a.getType().ordinal())).map(SeasonResults::award).toList();
    }
    Map<String, List<SeasonDtos.LeaderDto>> leaders = new LinkedHashMap<>();
    SeasonAwards.leaders(lines, 5).forEach((k, l) -> leaders.put(k, l.stream().map(x -> new SeasonDtos.LeaderDto(UUID.fromString(x.playerId()), x.name(),
        x.position(), x.clubId() == null ? null : CareerAssembler.club(SeasonResults.club(v, UUID.fromString(x.clubId()))), x.value(), x.games())).toList()));
    boolean isCurrent = season.getId().equals(current.getId());
    List<SeasonDtos.ExpiringContractDto> expiring = isCurrent && user != null ? expiring(user, season, rules) : List.of();
    SeasonDtos.OffseasonDto offseason = new SeasonDtos.OffseasonDto(isCurrent && season.isCompleted(), steps(isCurrent ? "PENDING" : "DONE",
        isCurrent ? "PENDING" : "SKIPPED"), expiring);
    return new SeasonDtos.SeasonSummaryDto(season.getId(), season.getNumber(), season.getYear(), season.getPhase().name(), season.isCompleted(),
        season.getChampionClub() == null ? null : CareerAssembler.club(season.getChampionClub()),
        season.getRunnerUpClub() == null ? null : CareerAssembler.club(season.getRunnerUpClub()), user, campaign, provisional, awards, leaders, offseason);
  }

  private static List<SeasonDtos.OffseasonStepDto> steps(String ownStatus, String hookStatus) {
    return List.of(new SeasonDtos.OffseasonStepDto("RENEWALS", ownStatus, "SEASON_ADVANCE"),
        new SeasonDtos.OffseasonStepDto(SeasonPhase.FREE_AGENCY.name(), ownStatus, "FREE_AGENCY"),
        new SeasonDtos.OffseasonStepDto(SeasonPhase.DRAFT.name(), hookStatus, "BLOCK_7"),
        new SeasonDtos.OffseasonStepDto(SeasonPhase.PRESEASON.name(), ownStatus, "SEASON_ADVANCE"));
  }

  /** Contratos da franquia que terminam na temporada (pendências de renovação). */
  private List<SeasonDtos.ExpiringContractDto> expiring(UUID club, Season season, LeagueRules rules) {
    return players.findByClubIdOrderByOverallDesc(club).stream()
        .filter(p -> p.getRosterStatus().requiresClub() && p.getContract() != null && p.getContract().endSeason() <= season.getYear())
        .map(p -> contract(p, season.getYear(), rules)).toList();
  }

  private static SeasonDtos.ExpiringContractDto contract(Player p, int year, LeagueRules rules) {
    return new SeasonDtos.ExpiringContractDto(p.getId(), p.getName(), p.getPosition().name(), p.getAge(), p.getOverall(), p.getContract().endSeason(),
        CapSheet.capHit(p, year, rules));
  }

  // ================================================================ avanço

  @Transactional
  public SeasonDtos.SeasonAdvanceDto advance(UUID careerId, UUID userId, UUID expectedSeasonId) {
    // primeira leitura da transação: trava a carreira (dois avanços juntos rodam um depois do outro, D-91)
    saves.findByIdForUpdate(careerId);
    CareerSave save = support.writable(careerId, userId);
    Season old = save.getSeason();
    if (old == null) throw new BusinessRuleException("NO_SEASON", "Carreira sem temporada");
    if (expectedSeasonId != null && !expectedSeasonId.equals(old.getId())) {
      Season asked = seasons.findById(expectedSeasonId).filter(s -> s.getCareer().getId().equals(save.getId()))
          .orElseThrow(() -> new NotFoundException("Temporada", expectedSeasonId));
      if (asked.getNumber() < old.getNumber()) {
        ScoresException e = new ConflictException("SEASON_ALREADY_ADVANCED", "A temporada " + asked.getNumber() + " já foi encerrada", old.getNumber());
        throw e.withDetail("seasonId", asked.getId()).withDetail("currentSeasonId", old.getId()).withDetail("currentSeasonNumber", old.getNumber());
      }
    }
    if (!old.isCompleted()) {
      ScoresException e = new BusinessRuleException("SEASON_NOT_FINISHED", "A temporada ainda não terminou", old.getPhase().name());
      throw e.withDetail("phase", old.getPhase().name()).withDetail("currentWeek", old.getCurrentWeek());
    }
    LeagueRules rules = CareerSupport.rules(save);
    Club userClub = save.getClub();
    int newYear = old.getYear() + 1;
    LocalDate reference = LocalDate.of(newYear, REFERENCE_MONTH, REFERENCE_DAY);

    // 1. waivers pendentes e moral final do elenco
    waivers.resolve(save, old, old.getCurrentWeek());
    Map<UUID, PlayerMoraleEngine.State> finalMorale = userClub == null ? Map.of() : morale.compute(save).states();

    // 2. colocação final nas divisões (ordem dos confrontos por colocação do calendário novo)
    SeasonResults.View view = results.view(old, rules);
    Map<UUID, Integer> place = new HashMap<>();
    view.table().divisions().values().forEach(list -> list.forEach(r -> place.put(UUID.fromString(r.line().team().key()), r.rank() - 1)));

    // 3. contratos que terminam (o jogador ainda com a franquia) e 4. envelhecimento e progressão de todos os jogadores do mundo
    List<Player> world = players.findByWorldId(save.getWorld().getId());
    UUID userClubId = userClub == null ? null : userClub.getId();
    List<SeasonDtos.ExpiringContractDto> mine = new ArrayList<>();
    Map<UUID, List<AiContractRenewal.Expired>> expiredByClub = new LinkedHashMap<>();
    Map<UUID, List<Player>> stayingByClub = new LinkedHashMap<>();
    int improved = 0, declined = 0;
    for (Player p : world) {
      boolean onClub = p.getClub() != null && p.getRosterStatus().requiresClub();
      boolean user = onClub && p.getClub().getId().equals(userClubId);
      if (onClub && p.getContract() != null && p.getContract().endSeason() <= old.getYear()) {
        if (user) mine.add(contract(p, old.getYear(), rules));
        expiredByClub.computeIfAbsent(p.getClub().getId(), k -> new ArrayList<>()).add(new AiContractRenewal.Expired(p, p.getRosterStatus()));
      } else if (onClub) {
        stayingByClub.computeIfAbsent(p.getClub().getId(), k -> new ArrayList<>()).add(p);
      }
      int before = p.getOverall();
      boolean specialist = p.getPosition() == Position.K || p.getPosition() == Position.P || p.getPosition() == Position.LS;
      p.setAttributes(PlayerProgression.apply(p.getAttributes(), p.getAge(), p.getOverall(), p.getPotential(), specialist).attributes());
      p.recalculate(reference);
      if (onClub) p.setExperienceYears(p.getExperienceYears() + 1);
      p.setInjuryWeeksOut(0);
      p.setSuspensionWeeks(0);
      p.setCondition(100);
      PlayerMoraleEngine.State st = finalMorale.get(p.getId());
      p.setMorale(MoraleService.nextSeasonMorale(st == null ? p.getMorale() : st.rounded()));
      if (user && p.getOverall() > before) improved++;
      if (user && p.getOverall() < before) declined++;
    }
    // renovações: as franquias da IA renovam o necessário para voltar aos limites do elenco (Q-42, D-120, gancho do bloco 7); a do
    // usuário não renova sozinha; quem não renova vira agente livre com o movimento CONTRACT_EXPIRED (pendência do bloco 7, D-100)
    int expired = 0, aiRenewals = 0;
    List<RosterMove> expiredMoves = new ArrayList<>();
    for (Map.Entry<UUID, List<AiContractRenewal.Expired>> e : expiredByClub.entrySet()) {
      boolean user = e.getKey().equals(userClubId);
      java.util.Set<UUID> renewed = user ? java.util.Set.of()
          : new java.util.HashSet<>(aiRenewal.renew(e.getKey(), stayingByClub.getOrDefault(e.getKey(), List.of()), e.getValue(), newYear, rules,
              CapSheet.salaryCap(save), cap.deadMoney(save, e.getKey(), newYear)));
      aiRenewals += renewed.size();
      for (AiContractRenewal.Expired x : e.getValue()) {
        Player p = x.player();
        if (renewed.contains(p.getId())) continue;
        RosterMove m = new RosterMove();
        m.setCareer(save); m.setSeason(old); m.setPlayer(p); m.setClub(p.getClub()); m.setWeek(old.getCurrentWeek()); m.setKind(RosterMoveKind.CONTRACT_EXPIRED);
        m.setFromStatus(x.from()); m.setToStatus(RosterStatus.FREE_AGENT);
        expiredMoves.add(m);
        if (user) audit.ok(AuditActions.CONTRACT_EXPIRED, "Player", p.getId(), "fim de contrato em " + old.getYear() + ": agente livre (pendência do bloco 7)");
        p.setClub(null);
        p.setRosterStatus(RosterStatus.FREE_AGENT);
        expired++;
      }
    }
    players.saveAll(world);
    moves.saveAll(expiredMoves);

    long carried = moves.findByCareerIdAndSeasonIdOrderByCreatedAtAsc(save.getId(), old.getId()).stream()
        .filter(m -> m.getKind() == RosterMoveKind.RELEASE).mapToLong(RosterMove::getDeadMoneyNextSeason).sum();

    // 6. pré-temporada: calendário novo e a carreira na temporada nova
    Season next = scheduler.createSeason(save, old.getLeague(), old.getNumber() + 1, newYear, place);
    save.setSeason(next);
    save.setCurrentWeek(1);
    save.setLastPlayedAt(Instant.now());
    saves.save(save);
    // 5. free agency (bloco 7a: abre a janela na temporada nova, D-136) e draft (gancho do bloco 7b), na ordem da D-10
    Map<SeasonPhase, String> hookStatus = new LinkedHashMap<>();
    for (OffseasonPhase ph : phases.stream().sorted(Comparator.comparingInt((OffseasonPhase x) -> x.phase() == SeasonPhase.FREE_AGENCY ? 0 : 1)).toList()) {
      hookStatus.put(ph.phase(), ph.run(save, old));
    }
    if (userClub != null) depthCharts.refreshAfterRosterChange(save, userClub, rules.getGameDayActives());
    audit.ok(AuditActions.SEASON_ADVANCED, "Season", next.getId(), "temporada " + old.getNumber() + " → " + next.getNumber() + "; agentes livres: "
        + expired + "; renovações da IA: " + aiRenewals + "; free agency " + hookStatus.getOrDefault(SeasonPhase.FREE_AGENCY, "SKIPPED") + "; draft " + hookStatus.getOrDefault(SeasonPhase.DRAFT, "SKIPPED"));
    List<SeasonDtos.OffseasonStepDto> done = List.of(new SeasonDtos.OffseasonStepDto("RENEWALS", "DONE", "SEASON_ADVANCE"),
        new SeasonDtos.OffseasonStepDto(SeasonPhase.FREE_AGENCY.name(), hookStatus.getOrDefault(SeasonPhase.FREE_AGENCY, "SKIPPED"), "FREE_AGENCY"),
        new SeasonDtos.OffseasonStepDto(SeasonPhase.DRAFT.name(), hookStatus.getOrDefault(SeasonPhase.DRAFT, "SKIPPED"), "BLOCK_7"),
        new SeasonDtos.OffseasonStepDto(SeasonPhase.PRESEASON.name(), "DONE", "SEASON_ADVANCE"));
    CareerDtos.CareerDto career = CareerAssembler.career(save, CareerDtos.RecordDto.of(0, 0, 0));
    return new SeasonDtos.SeasonAdvanceDto(old.getId(), next.getId(), next.getNumber(), next.getYear(), next.getPhase().name(), next.getCurrentWeek(), done,
        expired, aiRenewals, mine, improved, declined, carried, career);
  }
}
