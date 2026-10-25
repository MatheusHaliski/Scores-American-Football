package br.com.velion.scoresaf.domain.engine.sim;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.engine.model.EventKeys;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.PlayEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RF54: todo evento é coerente — chave i18n do catálogo, bola 0–100, descida 0–4, placar só sobe por pontuação. */
class EventInvariantsTest {

  @Test
  void eventos_sao_coerentes() {
    List<MatchResult> games = new ArrayList<>(SampleGames.pro());
    games.addAll(SampleGames.college());
    for (MatchResult r : games) {
      int seq = 0, quarter = 1, home = 0, away = 0;
      for (PlayEvent e : r.events()) {
        assertThat(e.seq()).isEqualTo(++seq);
        assertThat(EventKeys.ALL).as("chave do catálogo").contains(e.textKey());
        assertThat(e.params().keySet()).as("parâmetros obrigatórios de %s", e.textKey()).containsAll(EventKeys.REQUIRED_PARAMS.get(e.textKey()));
        assertThat(e.params().values()).allMatch(v -> v != null && !v.isBlank());
        assertThat(e.ballOnAbsYd()).isBetween(0, 100);
        assertThat(e.down()).isBetween(0, 4);
        assertThat(e.distance()).isBetween(0, 99);
        assertThat(e.quarter()).isGreaterThanOrEqualTo(quarter);
        quarter = e.quarter();
        assertThat(e.homeScore()).isGreaterThanOrEqualTo(home);
        assertThat(e.awayScore()).isGreaterThanOrEqualTo(away);
        assertThat(e.homeScore() - home + e.awayScore() - away).isEqualTo(e.points());
        home = e.homeScore();
        away = e.awayScore();
        if (e.type().isScrimmage()) assertThat(e.down()).isBetween(1, 4);
      }
      assertThat(home).isEqualTo(r.homeScore());
      assertThat(away).isEqualTo(r.awayScore());
      assertThat(r.drives()).allMatch(d -> d.result() != null && d.plays() >= 0 && d.startAbsYd() >= 0 && d.startAbsYd() <= 100);
    }
  }

  @Test
  void textos_sao_chaves_e_nunca_frases() {
    assertThat(EventKeys.ALL).doesNotHaveDuplicates().allMatch(k -> k.startsWith("match:event.") && !k.contains(" "));
    assertThat(EventKeys.REQUIRED_PARAMS.keySet()).containsExactlyInAnyOrderElementsOf(EventKeys.ALL);
  }
}
