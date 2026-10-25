package br.com.velion.scoresaf.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Posições, unidades e faixas de altura e peso (docs/01 §3.3). */
class PositionTest {
  @Test
  void vinte_posicoes_nas_tres_unidades() {
    assertThat(Position.values()).hasSize(20);
    assertThat(Arrays.stream(Position.values()).filter(p -> p.unit() == Unit.OFFENSE)).hasSize(10);
    assertThat(Arrays.stream(Position.values()).filter(p -> p.unit() == Unit.DEFENSE)).hasSize(7);
    assertThat(Arrays.stream(Position.values()).filter(p -> p.unit() == Unit.SPECIAL_TEAMS)).containsExactly(Position.K, Position.P, Position.LS);
    assertThat(Arrays.stream(Position.values()).filter(p -> p.group() == PositionGroup.OL))
        .containsExactly(Position.LT, Position.LG, Position.C, Position.RG, Position.RT);
  }

  @ParameterizedTest
  @EnumSource(Position.class)
  void faixas_de_altura_e_peso_sao_coerentes(Position p) {
    assertThat(p.minHeightCm()).isLessThan(p.maxHeightCm());
    assertThat(p.minWeightKg()).isLessThan(p.maxWeightKg());
    assertThat(p.acceptsHeight(p.minHeightCm())).isTrue();
    assertThat(p.acceptsHeight(p.maxHeightCm() + 1)).isFalse();
    assertThat(p.acceptsWeight(p.minWeightKg() - 1)).isFalse();
  }

  @Test
  void linha_ofensiva_e_mais_pesada_que_cornerback_e_parse_aceita_minusculas() {
    assertThat(Position.LT.minWeightKg()).isGreaterThan(Position.CB.maxWeightKg());
    assertThat(Position.parse(" lt ")).isEqualTo(Position.LT);
    assertThat(Position.parse("PG")).isNull();
    assertThat(Position.parse(null)).isNull();
  }

  @Test
  void faltas_do_catalogo_inicial() {
    assertThat(PenaltyCode.values()).hasSize(13);
    assertThat(PenaltyCode.HOLDING_OFFENSE.yards()).isEqualTo(10);
    assertThat(PenaltyCode.HOLDING_DEFENSE.automaticFirstDown()).isTrue();
    assertThat(PenaltyCode.ROUGHING_THE_PASSER.yards()).isEqualTo(15);
    assertThat(PenaltyCode.INTENTIONAL_GROUNDING.lossOfDown()).isTrue();
    assertThat(PenaltyCode.PASS_INTERFERENCE_DEFENSE.spotFoul()).isTrue();
    assertThat(PenaltyCode.FALSE_START.side()).isEqualTo(PenaltyCode.Side.OFFENSE);
  }
}
