package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.AdvanceMode;
import br.com.velion.scoresaf.domain.engine.model.MatchDecision;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import java.util.List;

/** Implementação padrão do {@link MatchSimulator}: sem estado, segura para uso concorrente. */
public final class DefaultMatchSimulator implements MatchSimulator {

  @Override
  public MatchSnapshot start(MatchSetup setup) {
    if (setup.home() == null || setup.away() == null) throw new IllegalArgumentException("partida precisa de duas equipes");
    if (setup.home().clubId().equals(setup.away().clubId())) throw new IllegalArgumentException("uma equipe não joga contra si mesma");
    return SnapshotCodec.initial(setup);
  }

  @Override
  public MatchSnapshot advance(MatchSnapshot snapshot, AdvanceMode mode, List<MatchDecision> decisions) {
    LiveMatch m = SnapshotCodec.load(snapshot);
    GameEngine engine = new GameEngine(m);
    for (MatchDecision d : decisions == null ? List.<MatchDecision>of() : decisions) engine.apply(d);
    engine.advance(mode == null ? AdvanceMode.PLAY : mode);
    return SnapshotCodec.save(m);
  }

  @Override
  public MatchResult finish(MatchSnapshot s) {
    if (!s.finished()) throw new IllegalStateException("partida ainda não terminou");
    int h = s.homeScore(), a = s.awayScore();
    return new MatchResult(s.setup().fixtureId(), s.setup().seed(), s.engineVersion(), s.setup().rules().version(), s.setup().home().clubId(),
        s.setup().away().clubId(), h, a, s.homeQuarters(), s.awayQuarters(), s.quarter() > 4,
        h > a ? s.setup().home().clubId() : a > h ? s.setup().away().clubId() : null,
        BoxScores.team(s, true), BoxScores.team(s, false), BoxScores.players(s, true), BoxScores.players(s, false), s.drives(), s.events(),
        s.decisionsLog());
  }
}
