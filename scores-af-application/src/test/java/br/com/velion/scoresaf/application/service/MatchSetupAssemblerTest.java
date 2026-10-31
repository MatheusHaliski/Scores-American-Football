package br.com.velion.scoresaf.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.season.Fixture;
import br.com.velion.scoresaf.domain.season.Week;
import br.com.velion.scoresaf.domain.shared.WeekKind;
import org.junit.jupiter.api.Test;

/** D-96: jogo de semana de playoff vai ao motor como eliminatório (sem empate, D-40); o da temporada regular, não. */
class MatchSetupAssemblerTest {

  private static Fixture in(WeekKind kind) {
    Week w = new Week();
    w.setKind(kind);
    Fixture f = new Fixture();
    f.setWeek(w);
    return f;
  }

  @Test
  void jogo_de_playoff_e_eliminatorio_e_o_da_temporada_regular_nao() {
    assertThat(MatchSetupAssembler.knockout(in(WeekKind.REGULAR))).isFalse();
    assertThat(MatchSetupAssembler.knockout(in(WeekKind.PRESEASON))).isFalse();
    for (WeekKind k : new WeekKind[] {WeekKind.PRELIMINARY, WeekKind.WILD_CARD, WeekKind.DIVISIONAL, WeekKind.CONFERENCE, WeekKind.FINAL}) {
      assertThat(MatchSetupAssembler.knockout(in(k))).as(k.name()).isTrue();
    }
  }
}
