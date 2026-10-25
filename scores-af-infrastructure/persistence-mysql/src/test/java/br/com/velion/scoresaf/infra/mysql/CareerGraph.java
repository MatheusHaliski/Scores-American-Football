package br.com.velion.scoresaf.infra.mysql;

import br.com.velion.scoresaf.application.seed.DefaultWorld;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.match.ChartSlot;
import br.com.velion.scoresaf.domain.match.DepthChart;
import br.com.velion.scoresaf.domain.match.DepthChartSlot;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.season.Week;
import br.com.velion.scoresaf.domain.shared.CoachStyle;
import br.com.velion.scoresaf.domain.shared.KickoffSlot;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.WeekStatus;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import br.com.velion.scoresaf.infra.mysql.repository.CareerSaveJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.DepthChartJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.DepthChartSlotJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.FixtureJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.ManagerProfileJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.RosterMoveJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.SeasonJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.UserAccountJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.WeekJpaRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Grava uma carreira mínima (bloco 6a) sobre um mundo já gravado pelo {@link CatalogGraph}: conta, head coach humano, save, temporada,
 * semana, jogo, movimento do elenco e depth chart com uma vaga — todas as tabelas da V3.
 */
@Component
class CareerGraph {
  final UserAccountJpaRepository users; final ManagerProfileJpaRepository managers; final CareerSaveJpaRepository saves; final SeasonJpaRepository seasons;
  final WeekJpaRepository weeks; final FixtureJpaRepository fixtures; final RosterMoveJpaRepository moves; final DepthChartJpaRepository charts;
  final DepthChartSlotJpaRepository slots;

  CareerGraph(UserAccountJpaRepository users, ManagerProfileJpaRepository managers, CareerSaveJpaRepository saves, SeasonJpaRepository seasons,
              WeekJpaRepository weeks, FixtureJpaRepository fixtures, RosterMoveJpaRepository moves, DepthChartJpaRepository charts,
              DepthChartSlotJpaRepository slots) {
    this.users = users; this.managers = managers; this.saves = saves; this.seasons = seasons; this.weeks = weeks; this.fixtures = fixtures;
    this.moves = moves; this.charts = charts; this.slots = slots;
  }

  record Saved(CareerSave career, Season season, Week week, Fixture fixture, RosterMove move, DepthChart chart, DepthChartSlot slot) {}

  Saved persist(DefaultWorld w, String email) {
    w.world().setKind(WorldKind.SAVE);
    UserAccount u = new UserAccount(); u.setEmail(email); u.setDisplayName("Carreira"); u.setPasswordHash("x");
    users.save(u);
    Club home = w.clubs().get(0), away = w.clubs().get(1);
    League league = home.getLeague();
    ManagerProfile coach = new ManagerProfile();
    coach.setWorld(w.world()); coach.setName("Head Coach"); coach.setNationality("BRA"); coach.setHuman(true); coach.setUserAccount(u);
    coach.setCoachStyle(CoachStyle.DEVELOPER);
    managers.save(coach);
    CareerSave c = new CareerSave();
    c.setUser(u); c.setName("Carreira " + email.hashCode()); c.setWorld(w.world()); c.setManager(coach); c.setLeague(league); c.setClub(home); c.setSlot(1);
    c.setSeed(42L); c.setSourceWorldName("Mundo Padrão");
    saves.save(c);
    Season s = new Season();
    s.setCareer(c); s.setLeague(league); s.setYear(2026); s.setRegularSeasonWeeks(18); s.setGamesPerTeam(17); s.setScheduleSeed(7L);
    seasons.save(s);
    Week wk = new Week();
    wk.setSeason(s); wk.setNumber(1); wk.setStartsOn(LocalDate.of(2026, 9, 10)); wk.setStatus(WeekStatus.CURRENT);
    weeks.save(wk);
    Fixture f = new Fixture();
    f.setSeason(s); f.setWeek(wk); f.setWeekNumber(1); f.setKickoffDate(wk.getStartsOn()); f.setKickoffSlot(KickoffSlot.THU_NIGHT);
    f.setHomeClub(home); f.setAwayClub(away); f.setDivisional(true); f.setConferenceGame(true); f.setSeed(99L);
    fixtures.save(f);
    c.setSeason(s);
    saves.save(c);
    Player p = w.players().stream().filter(x -> x.getClub() == home).findFirst().orElseThrow();
    RosterMove m = new RosterMove();
    m.setCareer(c); m.setSeason(s); m.setPlayer(p); m.setClub(home); m.setWeek(1); m.setKind(RosterMoveKind.RELEASE);
    m.setFromStatus(RosterStatus.ACTIVE); m.setToStatus(RosterStatus.WAIVED); m.setDeadMoney(1_000_00L); m.setCapHitRemoved(2_000_00L);
    moves.save(m);
    DepthChart dc = new DepthChart();
    dc.setCareer(c); dc.setClub(home); dc.touch(true);
    charts.save(dc);
    DepthChartSlot slot = DepthChartSlot.of(dc, ChartSlot.THIRD_DOWN_BACK, 1, p);
    slots.saveAll(List.of(slot));
    return new Saved(c, s, wk, f, m, dc, slot);
  }
}
