package br.com.velion.scoresaf.domain.engine.sim;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.RulesSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Jogo de playoff (RF66, D-40, D-96): os jogos entre equipes iguais que terminam empatados na temporada regular (a regra da liga
 * permite empate) seguem para mais prorrogações quando são eliminatórios ({@code MatchSetup.knockout}) e sempre têm vencedor.
 */
class KnockoutTest {
  private static final int TIES = 3, MAX_GAMES = 4000;

  @Test
  void jogo_eliminatorio_nunca_termina_empatado() {
    DefaultMatchSimulator sim = new DefaultMatchSimulator();
    List<MatchSetup> tied = new ArrayList<>();
    for (int i = 0; i < MAX_GAMES && tied.size() < TIES; i++) {
      MatchSetup s = TestTeams.setup(TestTeams.team("Casa", 70, 30_000L + i), TestTeams.team("Fora", 70, 40_000L + i), RulesSnapshot.pro(), 50_000L + i);
      if (sim.simulate(s).winnerClubId() == null) tied.add(s);
    }
    assertThat(tied).as("jogos empatados na temporada regular (a regra da liga permite)").hasSize(TIES);
    for (MatchSetup s : tied) {
      MatchResult r = sim.simulate(s.asKnockout());
      assertThat(r.winnerClubId()).isNotNull();
      assertThat(r.homeScore()).isNotEqualTo(r.awayScore());
      assertThat(r.overtime()).isTrue();
    }
  }
}
