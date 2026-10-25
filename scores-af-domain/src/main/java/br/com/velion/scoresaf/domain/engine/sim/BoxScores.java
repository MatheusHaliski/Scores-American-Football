package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.EngineState.PlayerRuntime;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore.DefenseLine;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore.KickingLine;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore.PassingLine;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore.PuntingLine;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore.ReceivingLine;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore.ReturnLine;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore.RushingLine;
import br.com.velion.scoresaf.domain.engine.model.PlayerSnapshot;
import br.com.velion.scoresaf.domain.engine.model.TeamBoxScore;
import br.com.velion.scoresaf.domain.engine.model.TeamSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Box score (docs/01 §3.6) montado dos contadores do snapshot — serve para o parcial ao vivo e para o resultado final. O que o
 * motor não gera sai {@code null} ({@link PlayerBoxScore#NOT_GENERATED}).
 */
public final class BoxScores {
  private BoxScores() {}

  public static List<PlayerBoxScore> players(MatchSnapshot s, boolean home) {
    TeamSnapshot team = home ? s.setup().home() : s.setup().away();
    Map<UUID, PlayerRuntime> rt = new HashMap<>();
    for (PlayerRuntime pr : s.engine().players()) rt.put(pr.id(), pr);
    List<PlayerBoxScore> out = new ArrayList<>();
    for (PlayerSnapshot p : team.players()) {
      PlayerRuntime pr = rt.get(p.id());
      if (pr == null) continue;
      PlayerBoxScore b = player(p, team.clubId(), pr.stats());
      if (b.snaps() > 0 || b.passing() != null || b.rushing() != null || b.receiving() != null || b.defense() != null || b.kicking() != null
          || b.punting() != null || b.returns() != null) out.add(b);
    }
    return out;
  }

  static PlayerBoxScore player(PlayerSnapshot p, UUID clubId, List<Integer> st) {
    int att = g(st, Stat.PASS_ATT), sacked = g(st, Stat.SACKED);
    PassingLine passing = att > 0 || sacked > 0
        ? new PassingLine(g(st, Stat.PASS_CMP), att, g(st, Stat.PASS_YDS), g(st, Stat.PASS_TD), g(st, Stat.PASS_INT), sacked, g(st, Stat.SACK_YDS),
            passerRating(g(st, Stat.PASS_CMP), att, g(st, Stat.PASS_YDS), g(st, Stat.PASS_TD), g(st, Stat.PASS_INT)), g(st, Stat.PASS_LONG))
        : null;
    int ratt = g(st, Stat.RUSH_ATT);
    RushingLine rushing = ratt > 0
        ? new RushingLine(ratt, g(st, Stat.RUSH_YDS), round1(g(st, Stat.RUSH_YDS) / (double) ratt), g(st, Stat.RUSH_TD), g(st, Stat.FUMBLES),
            g(st, Stat.FUMBLES_LOST), g(st, Stat.RUSH_LONG))
        : null;
    int tgt = g(st, Stat.TARGETS);
    ReceivingLine receiving = tgt > 0
        ? new ReceivingLine(tgt, g(st, Stat.REC), g(st, Stat.REC_YDS), g(st, Stat.YAC), g(st, Stat.REC_TD), g(st, Stat.DROPS), g(st, Stat.REC_LONG))
        : null;
    int tk = g(st, Stat.TACKLES), tfl = g(st, Stat.TFL), sk = g(st, Stat.SACKS), in = g(st, Stat.DEF_INT), pd = g(st, Stat.PASSES_DEFENDED),
        ff = g(st, Stat.FORCED_FUMBLES), fr = g(st, Stat.FUMBLE_RECOVERIES), dtd = g(st, Stat.DEF_TD);
    DefenseLine defense = tk + tfl + sk + in + pd + ff + fr + dtd > 0 ? new DefenseLine(tk, null, tfl, sk, null, in, pd, ff, fr, dtd) : null;
    int a1 = g(st, Stat.FG_ATT_0_39), a2 = g(st, Stat.FG_ATT_40_49), a3 = g(st, Stat.FG_ATT_50), m1 = g(st, Stat.FG_MADE_0_39),
        m2 = g(st, Stat.FG_MADE_40_49), m3 = g(st, Stat.FG_MADE_50), pa = g(st, Stat.PAT_ATT);
    KickingLine kicking = a1 + a2 + a3 + pa > 0
        ? new KickingLine(m1 + m2 + m3, a1 + a2 + a3, m1, a1, m2, a2, m3, a3, g(st, Stat.FG_LONG), g(st, Stat.PAT_MADE), pa)
        : null;
    int punts = g(st, Stat.PUNTS);
    PuntingLine punting = punts > 0
        ? new PuntingLine(punts, g(st, Stat.PUNT_YDS), round1(g(st, Stat.PUNT_YDS) / (double) punts), g(st, Stat.PUNT_IN20), g(st, Stat.PUNT_TB),
            g(st, Stat.PUNT_LONG))
        : null;
    int kr = g(st, Stat.KR), pr = g(st, Stat.PR);
    ReturnLine returns = kr + pr > 0
        ? new ReturnLine(kr, g(st, Stat.KR_YDS), pr, g(st, Stat.PR_YDS), g(st, Stat.RET_TD), g(st, Stat.RET_LONG))
        : null;
    return new PlayerBoxScore(p.id(), clubId, p.name(), p.position(), p.jerseyNumber(), g(st, Stat.SNAPS), passing, rushing, receiving, defense, kicking,
        punting, returns);
  }

  public static TeamBoxScore team(MatchSnapshot s, boolean home) {
    List<Integer> st = home ? s.engine().homeStats() : s.engine().awayStats();
    UUID club = home ? s.setup().home().clubId() : s.setup().away().clubId();
    int rushYds = t(st, TeamStat.RUSH_YDS);
    int passNet = t(st, TeamStat.PASS_YDS_GROSS) - t(st, TeamStat.SACK_YDS);
    int plays = t(st, TeamStat.PLAYS);
    int total = rushYds + passNet;
    int intThrown = t(st, TeamStat.INT_THROWN), fumLost = t(st, TeamStat.FUMBLES_LOST);
    return new TeamBoxScore(club, home ? s.homeScore() : s.awayScore(), home ? s.homeQuarters() : s.awayQuarters(), t(st, TeamStat.FIRST_DOWNS),
        t(st, TeamStat.FD_RUSH), t(st, TeamStat.FD_PASS), t(st, TeamStat.FD_PENALTY), t(st, TeamStat.THIRD_CONV), t(st, TeamStat.THIRD_ATT),
        t(st, TeamStat.FOURTH_CONV), t(st, TeamStat.FOURTH_ATT), t(st, TeamStat.RZ_TD), t(st, TeamStat.RZ_TRIPS), plays, total,
        plays == 0 ? 0 : round1(total / (double) plays), t(st, TeamStat.RUSH_ATT), rushYds, t(st, TeamStat.PASS_CMP), t(st, TeamStat.PASS_ATT), passNet,
        t(st, TeamStat.SACKS_ALLOWED), t(st, TeamStat.SACK_YDS), intThrown, t(st, TeamStat.FUMBLES), fumLost, intThrown + fumLost,
        t(st, TeamStat.PENALTIES), t(st, TeamStat.PENALTY_YDS), t(st, TeamStat.TOP_SECONDS), t(st, TeamStat.DRIVES), t(st, TeamStat.PUNTS),
        t(st, TeamStat.FG_MADE), t(st, TeamStat.FG_ATT), t(st, TeamStat.DEF_SACKS), t(st, TeamStat.TIMEOUTS_USED));
  }

  /** Passer rating da liga profissional (0–158,3). */
  public static double passerRating(int cmp, int att, int yds, int td, int ints) {
    if (att == 0) return 0;
    double a = clamp(((double) cmp / att - 0.3) * 5);
    double b = clamp(((double) yds / att - 3) * 0.25);
    double c = clamp((double) td / att * 20);
    double d = clamp(2.375 - (double) ints / att * 25);
    return round1((a + b + c + d) / 6 * 100);
  }

  private static double clamp(double v) { return Math.max(0, Math.min(2.375, v)); }

  private static double round1(double v) { return Math.round(v * 10) / 10.0; }

  private static int g(List<Integer> st, Stat s) { return s.ordinal() < st.size() ? st.get(s.ordinal()) : 0; }

  private static int t(List<Integer> st, TeamStat s) { return s.ordinal() < st.size() ? st.get(s.ordinal()) : 0; }
}
