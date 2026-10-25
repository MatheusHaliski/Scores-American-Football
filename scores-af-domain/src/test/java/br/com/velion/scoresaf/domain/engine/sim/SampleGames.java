package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import br.com.velion.scoresaf.domain.engine.model.RulesSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Amostras fixas de jogos IA × IA (semente fixa: os testes de regra procuram os lances nelas de forma determinística). */
final class SampleGames {
  private SampleGames() {}

  private static List<MatchResult> pro;
  private static List<MatchResult> college;

  static synchronized List<MatchResult> pro() {
    if (pro == null) pro = run(RulesSnapshot.pro(), 240, 10_000, UnaryOperator.identity());
    return pro;
  }

  static synchronized List<MatchResult> college() {
    if (college == null) college = run(RulesSnapshot.college(), 160, 20_000, UnaryOperator.identity());
    return college;
  }

  static List<MatchResult> run(RulesSnapshot rules, int n, long seedBase, UnaryOperator<MatchSetup> tweak) {
    DefaultMatchSimulator sim = new DefaultMatchSimulator();
    List<MatchResult> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      int pa = 62 + (i % 6) * 4;
      MatchSetup s = TestTeams.setup(TestTeams.team("Casa" + (i % 9), pa, seedBase + i * 7L), TestTeams.team("Fora" + (i % 11), pa + (i % 3) - 1, seedBase + i * 13L + 1),
          rules, seedBase + i);
      out.add(sim.simulate(tweak.apply(s)));
    }
    return out;
  }

  /** Próximo lance (depois do índice {@code i}) que é jogada de verdade (snap, chute ou falta antes do snap). */
  static PlayEvent nextPlay(List<PlayEvent> ev, int i) {
    for (int j = i + 1; j < ev.size(); j++) if (ev.get(j).type().isPlay() || ev.get(j).type() == br.com.velion.scoresaf.domain.engine.model.PlayType.NO_PLAY) return ev.get(j);
    return null;
  }

  static boolean home(MatchResult r, UUID club) { return r.homeClubId().equals(club); }

  /** Jarda relativa ao time com a bola (0 = própria linha do gol). */
  static int rel(MatchResult r, PlayEvent e) { return home(r, e.possessionClubId()) ? e.ballOnAbsYd() : 100 - e.ballOnAbsYd(); }
}
