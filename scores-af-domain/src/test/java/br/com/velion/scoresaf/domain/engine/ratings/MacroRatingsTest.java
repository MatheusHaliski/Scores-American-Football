package br.com.velion.scoresaf.domain.engine.ratings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.PositionFamily;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Macros do card por família (D-6). */
class MacroRatingsTest {

  @Test
  void codigos_por_familia_na_ordem_do_plano() {
    assertThat(PositionFamily.QUARTERBACK.macroCodes()).containsExactly("PRE", "BRA", "LEI", "MOB", "CAL", "LID");
    assertThat(PositionFamily.SKILL.macroCodes()).containsExactly("VEL", "AGI", "MAO", "ROT", "QBT", "FOR");
    assertThat(PositionFamily.OFFENSIVE_LINE.macroCodes()).containsExactly("BLP", "BLC", "FOR", "TEC", "AGI", "CON");
    assertThat(PositionFamily.DEFENSE.macroCodes()).containsExactly("TAC", "IMP", "COB", "PRS", "VEL", "LEI");
    assertThat(PositionFamily.KICKER.macroCodes()).containsExactly("POT", "PRE", "CON");
  }

  @ParameterizedTest
  @EnumSource(PositionFamily.class)
  void cada_macro_soma_1_e_sai_na_ordem_da_familia(PositionFamily f) {
    Map<String, Map<AttributeKey, Integer>> def = MacroRatings.definition(f);
    assertThat(List.copyOf(def.keySet())).isEqualTo(f.macroCodes());
    def.forEach((code, w) -> assertThat(w.values().stream().mapToInt(Integer::intValue).sum()).as("%s/%s", f, code).isEqualTo(1000));
    MacroRatings m = MacroRatings.of(PlayerAttributes.uniform(71), f);
    assertThat(List.copyOf(m.values().keySet())).isEqualTo(f.macroCodes());
    assertThat(m.values().values()).allMatch(v -> v == 71);
  }

  @Test
  void familias_das_posicoes() {
    assertThat(Position.QB.family()).isEqualTo(PositionFamily.QUARTERBACK);
    for (Position p : List.of(Position.RB, Position.FB, Position.WR, Position.TE)) assertThat(p.family()).isEqualTo(PositionFamily.SKILL);
    for (Position p : List.of(Position.LT, Position.LG, Position.C, Position.RG, Position.RT, Position.LS)) assertThat(p.family()).isEqualTo(PositionFamily.OFFENSIVE_LINE);
    for (Position p : List.of(Position.DE, Position.DT, Position.OLB, Position.MLB, Position.CB, Position.FS, Position.SS)) assertThat(p.family()).isEqualTo(PositionFamily.DEFENSE);
    for (Position p : List.of(Position.K, Position.P)) assertThat(p.family()).isEqualTo(PositionFamily.KICKER);
  }

  @Test
  void macro_reflete_o_subatributo_que_o_define() {
    PlayerAttributes qb = PlayerAttributes.uniform(50).with(AttributeKey.THROW_POWER, 99).with(AttributeKey.POISE, 30);
    MacroRatings m = MacroRatings.of(qb, PositionFamily.QUARTERBACK);
    assertThat(m.get("BRA")).isEqualTo((99 * 800 + 50 * 200 + 500) / 1000);
    assertThat(m.get("CAL")).isEqualTo(30);
    assertThat(m.get("PRE")).isEqualTo(50);

    PlayerAttributes cb = PlayerAttributes.uniform(60).with(AttributeKey.MAN_COVERAGE, 90).with(AttributeKey.ZONE_COVERAGE, 90).with(AttributeKey.PRESS, 90);
    MacroRatings d = MacroRatings.of(cb, PositionFamily.DEFENSE);
    assertThat(d.get("COB")).isEqualTo(90);
    assertThat(d.get("TAC")).isEqualTo(60);

    MacroRatings k = MacroRatings.of(PlayerAttributes.uniform(40).with(AttributeKey.KICK_POWER, 95), PositionFamily.KICKER);
    assertThat(k.values()).containsExactly(Map.entry("POT", 95), Map.entry("PRE", 40), Map.entry("CON", 40));
    assertThatThrownBy(() -> k.get("TAC")).isInstanceOf(IllegalArgumentException.class);
  }
}
