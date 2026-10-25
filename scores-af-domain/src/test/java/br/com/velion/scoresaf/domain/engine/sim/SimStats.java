package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.TeamBoxScore;
import java.util.Locale;

/** Médias por equipe e por jogo para os testes de plausibilidade (docs/01 §3.5). */
final class SimStats {
  int games;
  double points, yards, drives, cmp, att, sacks, ints, fumLost, thirdConv, thirdAtt, fgMade, fgAtt, plays, penalties, punts, rushYds, passYds,
      firstDowns, top, fourthAtt;
  int homeWins, ties, overtimes;

  void add(MatchResult r) {
    games++;
    for (TeamBoxScore t : new TeamBoxScore[] {r.homeTeam(), r.awayTeam()}) {
      points += t.points(); yards += t.totalYards(); drives += t.drives(); cmp += t.passCompletions(); att += t.passAttempts();
      sacks += t.sacksAllowed(); ints += t.interceptionsThrown(); fumLost += t.fumblesLost(); thirdConv += t.thirdDownConversions();
      thirdAtt += t.thirdDownAttempts(); fgMade += t.fieldGoalsMade(); fgAtt += t.fieldGoalAttempts(); penalties += t.penalties(); punts += t.punts();
      rushYds += t.rushYards(); passYds += t.passYards(); firstDowns += t.firstDowns(); top += t.timeOfPossessionSeconds(); fourthAtt += t.fourthDownAttempts();
    }
    plays += r.playCount();
    if (r.homeScore() > r.awayScore()) homeWins++;
    if (r.homeScore() == r.awayScore()) ties++;
    if (r.overtime()) overtimes++;
  }

  double perTeam(double v) { return v / (games * 2.0); }
  double avgPoints() { return perTeam(points); }
  double avgYards() { return perTeam(yards); }
  double avgDrives() { return perTeam(drives); }
  double cmpPct() { return cmp / att; }
  double avgSacks() { return perTeam(sacks); }
  double avgInts() { return perTeam(ints); }
  double avgFumLost() { return perTeam(fumLost); }
  double thirdPct() { return thirdConv / thirdAtt; }
  double fgPct() { return fgMade / fgAtt; }
  double playsPerGame() { return plays / games; }

  String summary() {
    return String.format(Locale.ROOT,
        "games=%d pts=%.2f yds=%.1f (rush %.1f pass %.1f) drives=%.2f cmp=%.3f att=%.1f sacks=%.2f int=%.2f fumLost=%.2f 3rd=%.3f fg=%.3f (att %.2f) "
            + "plays=%.1f pen=%.2f punts=%.2f 1D=%.1f 4thAtt=%.2f top=%.0f homeWin=%.3f ties=%d ot=%d",
        games, avgPoints(), avgYards(), perTeam(rushYds), perTeam(passYds), avgDrives(), cmpPct(), perTeam(att), avgSacks(), avgInts(), avgFumLost(),
        thirdPct(), fgPct(), perTeam(fgAtt), playsPerGame(), perTeam(penalties), perTeam(punts), perTeam(firstDowns), perTeam(fourthAtt), perTeam(top),
        homeWins / (double) games, ties, overtimes);
  }
}
