package br.com.velion.scoresaf.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.engine.ratings.OverallCalculator;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.Unit;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PlayerTest {
  @Test
  void recalcula_idade_overall_na_posicao_natural_e_unidade() {
    Player p = new Player();
    p.setPosition(Position.MLB);
    p.setBirthDate(LocalDate.of(2000, 10, 2));
    p.setAttributes(PlayerAttributes.uniform(60).with(br.com.velion.scoresaf.domain.catalog.AttributeKey.TACKLE, 90));
    p.setPotential(10);
    p.recalculate(LocalDate.of(2026, 10, 1));
    assertThat(p.getAge()).isEqualTo(25);
    assertThat(p.getOverall()).isEqualTo(OverallCalculator.overall(p.getAttributes(), Position.MLB)).isGreaterThan(60);
    assertThat(p.getPotential()).isEqualTo(p.getOverall());
    assertThat(p.getUnit()).isEqualTo(Unit.DEFENSE);
    assertThat(p.ratingAt(Position.CB).outOfPosition()).isTrue();
    assertThat(p.ratingAt(Position.MLB).outOfPosition()).isFalse();
  }

  @Test
  void situacoes_do_elenco() {
    assertThat(RosterStatus.ACTIVE.countsTowardRoster()).isTrue();
    assertThat(RosterStatus.INACTIVE.countsTowardRoster()).isTrue();
    assertThat(RosterStatus.PRACTICE_SQUAD.countsTowardRoster()).isFalse();
    assertThat(RosterStatus.IR.countsTowardRoster()).isFalse();
    assertThat(RosterStatus.FREE_AGENT.requiresClub()).isFalse();
    assertThat(RosterStatus.WAIVED.requiresClub()).isFalse();
    assertThat(RosterStatus.PUP.requiresClub()).isTrue();
  }
}
