package br.com.velion.scoresaf.application.usecase.support;

import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Season;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Monta os DTOs da carreira, do calendário e da campanha (vitórias, derrotas e empates a partir dos jogos encerrados). */
public final class CareerAssembler {
  private CareerAssembler() {}

  /** Campanha e pontos de cada franquia nos jogos encerrados da temporada. */
  public record Tally(int wins, int losses, int ties, int pointsFor, int pointsAgainst) {
    public double winPct() { int g = wins + losses + ties; return g == 0 ? 0.0 : Math.round((wins + ties / 2.0) / g * 1000) / 1000.0; }
    public CareerDtos.RecordDto record() { return CareerDtos.RecordDto.of(wins, losses, ties); }
  }

  public static Map<UUID, Tally> tallies(List<Fixture> fixtures) {
    Map<UUID, int[]> acc = new HashMap<>();
    for (Fixture f : fixtures) {
      if (!f.isFinished() || f.getHomeScore() == null || f.getAwayScore() == null) continue;
      int h = f.getHomeScore(), a = f.getAwayScore();
      bump(acc, f.getHomeClub().getId(), h, a);
      bump(acc, f.getAwayClub().getId(), a, h);
    }
    Map<UUID, Tally> out = new HashMap<>();
    acc.forEach((k, v) -> out.put(k, new Tally(v[0], v[1], v[2], v[3], v[4])));
    return out;
  }

  private static void bump(Map<UUID, int[]> acc, UUID club, int pf, int pa) {
    int[] v = acc.computeIfAbsent(club, k -> new int[5]);
    if (pf > pa) v[0]++; else if (pf < pa) v[1]++; else v[2]++;
    v[3] += pf; v[4] += pa;
  }

  public static Tally tally(Map<UUID, Tally> all, UUID clubId) { return all.getOrDefault(clubId, new Tally(0, 0, 0, 0, 0)); }

  public static CareerDtos.CareerDto career(CareerSave s, CareerDtos.RecordDto record) {
    Season se = s.getSeason();
    Club c = s.getClub();
    ManagerProfile m = s.getManager();
    LeagueRules rules = CareerSupport.rules(s);
    return new CareerDtos.CareerDto(s.getId(), s.getName(), s.getSlot(), s.getStatus().name(), s.getDifficulty().name(), s.getSeed(), s.getCurrentWeek(),
        s.getEmploymentStatus().name(), s.getWorld().getId(), s.getWorld().getSourceWorld() == null ? null : s.getWorld().getSourceWorld().getId(),
        s.getSourceWorldName(), s.getLeague() == null ? null : s.getLeague().getId(), s.getLeague() == null ? null : s.getLeague().getName(),
        s.getLeague() == null ? null : s.getLeague().getRuleset().name(),
        c == null ? null : c.getId(), c == null ? null : c.getName(), c == null ? null : c.getShortName(), c == null ? null : c.getCity(),
        c == null ? null : c.getPrimaryColor(), c == null ? null : c.getSecondaryColor(),
        m.getId(), m.getName(), m.getNationality(), m.getCoachStyle().name(),
        se == null ? null : se.getId(), se == null ? null : se.getYear(), se == null ? null : se.getPhase().name(),
        se == null ? rules.getRegularSeasonWeeks() : se.getRegularSeasonWeeks(), record, s.getCreatedAt(), s.getLastPlayedAt(),
        s.getVersion() == null ? 0 : s.getVersion());
  }

  public static CareerDtos.ClubRefDto club(Club c) {
    return new CareerDtos.ClubRefDto(c.getId(), c.getName(), c.getShortName(), c.getCity(), c.getPrimaryColor(), c.getSecondaryColor(),
        c.getConference() == null ? null : c.getConference().getShortName(), c.getDivision() == null ? null : c.getDivision().getName());
  }

  public static CareerDtos.FixtureDto fixture(Fixture f, UUID userClubId) {
    return new CareerDtos.FixtureDto(f.getId(), f.getWeekNumber(), f.getKickoffDate(), f.getKickoffSlot().name(), f.getStatus().name(),
        f.getHomeClub().getId(), f.getAwayClub().getId(), f.getHomeScore(), f.getAwayScore(), f.isDivisional(), f.isConferenceGame(),
        userClubId != null && f.involves(userClubId));
  }

  /** Ordem do jogo na semana: horário (quinta, domingo cedo, tarde, noite, segunda), depois o nome do mandante. */
  public static final java.util.Comparator<Fixture> FIXTURE_ORDER = java.util.Comparator.comparingInt(Fixture::getWeekNumber)
      .thenComparingInt(f -> f.getKickoffSlot().ordinal()).thenComparing(f -> f.getHomeClub().getName());
}
