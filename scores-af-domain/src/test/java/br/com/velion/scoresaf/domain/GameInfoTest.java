package br.com.velion.scoresaf.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.game.GameInfo;
import br.com.velion.scoresaf.domain.game.Ruleset;
import org.junit.jupiter.api.Test;

class GameInfoTest {
  @Test
  void sem_versao_vira_dev_e_regras_padrao_sao_as_profissionais() {
    GameInfo g = GameInfo.of(" ");
    assertThat(g.version()).isEqualTo("dev");
    assertThat(g.name()).isEqualTo("Scores: American Football");
    assertThat(g.sport()).isEqualTo("AMERICAN_FOOTBALL");
    assertThat(g.defaultRuleset()).isEqualTo(Ruleset.PRO);
  }
}
