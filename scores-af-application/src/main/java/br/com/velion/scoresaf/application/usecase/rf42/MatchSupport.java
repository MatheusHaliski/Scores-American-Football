package br.com.velion.scoresaf.application.usecase.rf42;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.common.exception.ScoresException;
import br.com.velion.scoresaf.application.common.json.Jsons;
import br.com.velion.scoresaf.application.dto.LiveMatchDto;
import br.com.velion.scoresaf.application.dto.MatchDtos;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.port.out.persistence.MatchSessionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WeekRepository;
import br.com.velion.scoresaf.application.service.MatchService;
import br.com.velion.scoresaf.application.service.PlayoffService;
import br.com.velion.scoresaf.application.service.WeekProcessor;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.engine.model.AdvanceMode;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.MatchStatus;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.PlayResult;
import br.com.velion.scoresaf.domain.engine.model.PlayType;
import br.com.velion.scoresaf.domain.match.MatchSession;
import br.com.velion.scoresaf.domain.match.MatchSessionStatus;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Week;
import br.com.velion.scoresaf.domain.shared.FixtureStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Apoio dos casos de uso de partida (como o {@code MatchSupport} do basquete): sessão do dono (BOLA: partida de outro usuário responde
 * 404, como id inexistente, e grava {@code ACCESS_DENIED}, D-59), leitura e gravação do snapshot na sessão, situação da sessão
 * derivada do motor, prazo da decisão aberta (RF62), fim da partida (resultado, relatório e fechamento da semana) e os DTOs do
 * estado ao vivo e do painel da semana.
 */
@Component
public class MatchSupport {
  /** Prazo para o manager chamar a jogada (RF62, D-61); vencido, a IA chama no próximo avanço. */
  public static final Duration DECISION_TIMEOUT = Duration.ofSeconds(30);
  /** Lances recentes no estado ao vivo ({@code live.lastEvents}). */
  public static final int LAST_EVENTS = 20;

  private final MatchSessionRepository sessions; private final FixtureRepository fixtures; private final WeekRepository weeks; private final MatchService matches;
  private final WeekProcessor weekProcessor; private final AuditService audit; private final PlayoffService playoffs;

  public MatchSupport(MatchSessionRepository sessions, FixtureRepository fixtures, WeekRepository weeks, MatchService matches, WeekProcessor weekProcessor,
                      AuditService audit, PlayoffService playoffs) {
    this.sessions = sessions; this.fixtures = fixtures; this.weeks = weeks; this.matches = matches; this.weekProcessor = weekProcessor; this.audit = audit;
    this.playoffs = playoffs;
  }

  /** Sessão do dono. Inexistente ou de outro usuário: 404 (não revela que a partida existe), com auditoria no segundo caso. */
  public MatchSession ownedSession(UUID matchId, UUID userId) {
    MatchSession s = sessions.findById(matchId).orElseThrow(() -> new NotFoundException("Partida", matchId));
    requireOwner(s.getCareer(), userId, "MatchSession", matchId);
    return s;
  }

  /**
   * Sessão do dono travada para escrita (avançar, chamar a jogada, ajustar): a trava é a primeira leitura da transação, então uma
   * segunda escrita na mesma partida espera a primeira confirmar e já lê a sessão gravada (versão nova ou partida encerrada ⇒ 409),
   * em vez de simular de novo e bater na chave única do resultado (D-91). Inexistente ou de outro usuário: 404, como em
   * {@link #ownedSession}.
   */
  public MatchSession lockedSession(UUID matchId, UUID userId) {
    MatchSession s = sessions.findByIdForUpdate(matchId).orElseThrow(() -> new NotFoundException("Partida", matchId));
    requireOwner(s.getCareer(), userId, "MatchSession", matchId);
    return s;
  }

  /** Carreira do dono para uma rota de partida: de outro usuário ⇒ 404 + {@code ACCESS_DENIED}. */
  public void requireOwner(CareerSave save, UUID userId, String resource, UUID id) {
    if (save == null || save.getUser() == null || !save.getUser().getId().equals(userId)) {
      audit.denied(AuditActions.ACCESS_DENIED, resource, id, "usuário " + userId + " não é dono da partida");
      throw new NotFoundException("Partida", id);
    }
  }

  public static MatchSnapshot read(MatchSession s) {
    try {
      return Jsons.MAPPER.readValue(s.getSnapshotJson(), MatchSnapshot.class);
    } catch (Exception e) {
      throw new IllegalStateException("snapshot da partida " + s.getId() + " ilegível", e);
    }
  }

  static String write(MatchSnapshot s) {
    try {
      return Jsons.MAPPER.writeValueAsString(s);
    } catch (Exception e) {
      throw new IllegalStateException("snapshot da partida não serializou", e);
    }
  }

  /** Situação da sessão: a do motor, com BREAK/HALFTIME quando o último lance foi um fim de período e o jogo segue. */
  public static MatchSessionStatus statusOf(MatchSnapshot s) {
    if (s.status() == MatchStatus.NOT_STARTED) return MatchSessionStatus.NOT_STARTED;
    if (s.status() == MatchStatus.FINISHED) return MatchSessionStatus.FINISHED;
    if (s.status() == MatchStatus.AWAITING_DECISION) return MatchSessionStatus.AWAITING_DECISION;
    if (!s.events().isEmpty()) {
      PlayEvent last = s.events().get(s.events().size() - 1);
      if (last.type() == PlayType.END_OF_PERIOD) return last.result() == PlayResult.END_OF_HALF ? MatchSessionStatus.HALFTIME : MatchSessionStatus.BREAK;
    }
    return MatchSessionStatus.LIVE;
  }

  /** Versão do snapshot que o cliente mandou confere com a atual; senão 409 MATCH_VERSION_CONFLICT com as duas. */
  public static void checkVersion(MatchSession s, Long expected, boolean required) {
    if (expected == null && !required) return;
    long current = s.getSnapshotVersion();
    if (expected == null || expected != current) {
      ScoresException e = new ConflictException("MATCH_VERSION_CONFLICT", "A partida avançou em outra sessão", expected, current);
      throw e.withDetail("currentVersion", current).withDetail("expectedVersion", expected);
    }
  }

  /** A decisão aberta venceu o prazo. */
  public static boolean expired(MatchSession s, Instant now) {
    return s.getDecisionId() != null && s.getDecisionDeadlineAt() != null && !now.isBefore(s.getDecisionDeadlineAt());
  }

  /**
   * Grava o snapshot novo na sessão (colunas de consulta, prazo da decisão nova) e, se a partida acabou, grava o resultado, o
   * relatório e fecha a semana (simula o resto dela e avança).
   */
  public void store(MatchSession session, MatchSnapshot next) {
    Instant now = Instant.now();
    session.setSnapshotJson(write(next));
    session.setSnapshotVersion(next.version());
    session.setStatus(statusOf(next));
    session.setQuarter(next.quarter());
    session.setClockSeconds(next.clockSeconds());
    session.setPossessionClubId(next.possessionClubId());
    session.setHomeScore(next.homeScore());
    session.setAwayScore(next.awayScore());
    session.setRngCursor(next.rngCursor());
    if (next.pendingDecision() == null) {
      session.setDecisionId(null);
      session.setDecisionDeadlineAt(null);
    } else if (!next.pendingDecision().id().equals(session.getDecisionId())) {
      session.setDecisionId(next.pendingDecision().id());
      session.setDecisionDeadlineAt(now.plus(DECISION_TIMEOUT));
    }
    Fixture f = session.getFixture();
    if (next.finished()) {
      session.setFinishedAt(now);
      sessions.save(session);
      MatchResult result = matches.simulator().finish(next);
      matches.persistResult(session.getCareer(), f, next.setup(), result, true);
      Season season = f.getSeason();
      weekProcessor.close(session.getCareer(), season, f.getWeekNumber());
    } else {
      if (f.getStatus() != FixtureStatus.LIVE) { f.setStatus(FixtureStatus.LIVE); fixtures.save(f); }
      sessions.save(session);
    }
  }

  /** Modos de avanço aceitos agora: nenhum com a partida encerrada ou com decisão aberta no prazo. */
  public static List<String> advanceModes(MatchSession s, Instant now) {
    if (s.isFinished() || (s.getStatus() == MatchSessionStatus.AWAITING_DECISION && !expired(s, now))) return List.of();
    return Arrays.stream(AdvanceMode.values()).map(Enum::name).toList();
  }

  public static MatchDtos.MatchStateDto state(MatchSession s, MatchSnapshot snap) {
    UUID user = snap.setup().userClubId();
    return new MatchDtos.MatchStateDto(s.getId(), s.getCareer().getId(), s.getFixture().getId(), s.getFixture().getWeekNumber(), s.getStatus().name(), user,
        user != null && user.equals(snap.setup().home().clubId()), s.getPlayCallingLevel().name(), s.getStatus().adjustable(), advanceModes(s, Instant.now()),
        s.getDecisionDeadlineAt(), (int) DECISION_TIMEOUT.toSeconds(), LiveMatchDto.of(snap, LAST_EVENTS));
  }

  /** Painel da semana (RF27): todos os jogos por horário, folgas, a partida do usuário e a situação ao vivo dela. */
  public MatchDtos.WeekBoardDto board(CareerSave save, Season season, int week, List<Club> leagueClubs) {
    List<Fixture> fx = fixtures.findBySeasonIdAndWeekNumber(season.getId(), week).stream().sorted(CareerAssembler.FIXTURE_ORDER).toList();
    UUID userClub = save.getClub() == null ? null : save.getClub().getId();
    Week w = weeks.findBySeasonIdAndNumber(season.getId(), week).orElse(null);
    Set<UUID> playing = new HashSet<>();
    List<MatchDtos.WeekGameDto> games = new ArrayList<>();
    UUID userFixture = null, matchId = null;
    for (Fixture f : fx) {
      playing.add(f.getHomeClub().getId());
      playing.add(f.getAwayClub().getId());
      MatchDtos.LiveLineDto live = null;
      MatchSession session = sessions.findByFixtureId(f.getId()).orElse(null);
      if (session != null) {
        live = new MatchDtos.LiveLineDto(session.getId(), session.getStatus().name(), session.getQuarter(), session.getClockSeconds(),
            session.getPossessionClubId(), 0, 0, 0, session.getHomeScore(), session.getAwayScore());
        if (!session.isFinished()) {
          MatchSnapshot snap = read(session);
          live = new MatchDtos.LiveLineDto(session.getId(), session.getStatus().name(), snap.quarter(), snap.clockSeconds(), snap.possessionClubId(),
              snap.down(), snap.distance(), snap.ballOnAbsYd(), snap.homeScore(), snap.awayScore());
        }
      }
      if (userClub != null && f.involves(userClub)) { userFixture = f.getId(); matchId = session == null ? null : session.getId(); }
      games.add(new MatchDtos.WeekGameDto(CareerAssembler.fixture(f, userClub), CareerAssembler.club(f.getHomeClub()), CareerAssembler.club(f.getAwayClub()), live));
    }
    // semana de playoff: folga é só a semente que folga na rodada (as eliminadas e as de fora não "folgam", D-105)
    Set<UUID> playoffByes = playoffs.byes(season, week);
    List<UUID> byes = leagueClubs.stream().map(Club::getId).filter(id -> !playing.contains(id) && (playoffByes == null || playoffByes.contains(id))).toList();
    boolean completed = !fx.isEmpty() && fx.stream().allMatch(Fixture::isFinished);
    return new MatchDtos.WeekBoardDto(season.getId(), week, season.getRegularSeasonWeeks(), season.getCurrentWeek(), w == null ? null : w.getKind().name(),
        w == null ? null : w.getStatus().name(), w == null ? null : w.getStartsOn(), completed, userClub != null && byes.contains(userClub), userFixture,
        matchId, byes, games);
  }
}
