package br.com.velion.scoresaf.domain.engine.ratings;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import br.com.velion.scoresaf.domain.shared.Position;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Overall por posição com os pesos oficiais OVR v1 (docs/01 §3.4). */
class OverallCalculatorTest {

  @ParameterizedTest
  @EnumSource(Position.class)
  void pesos_de_cada_posicao_somam_1(Position p) {
    Map<AttributeKey, Integer> w = OverallWeights.V1.of(p);
    assertThat(w.values().stream().mapToInt(Integer::intValue).sum()).as("soma dos pesos de %s em milésimos", p).isEqualTo(OverallWeights.SCALE);
    assertThat(w.values()).as("nenhum peso zero ou negativo listado").allMatch(x -> x > 0);
  }

  @Test
  void toda_posicao_tem_pesos_e_a_versao_e_ovr_v1() {
    assertThat(OverallWeights.V1.all()).containsOnlyKeys(Position.values());
    assertThat(OverallCalculator.CURRENT.version()).isEqualTo("OVR v1");
  }

  @ParameterizedTest
  @EnumSource(Position.class)
  void atributos_iguais_dao_o_mesmo_overall_em_qualquer_posicao(Position p) {
    for (int v : new int[] {0, 1, 50, 64, 65, 99, 100}) assertThat(OverallCalculator.overall(PlayerAttributes.uniform(v), p)).isEqualTo(v);
  }

  @ParameterizedTest
  @EnumSource(Position.class)
  void especialista_tem_o_maior_overall_na_posicao_natural(Position natural) {
    PlayerAttributes a = RatingProfiles.specialist(natural, 85, 40);
    int own = OverallCalculator.overall(a, natural);
    assertThat(own).isEqualTo(85);
    for (Position other : Position.values()) {
      int there = OverallCalculator.overall(a, other);
      assertThat(there).as("%s jogando de %s", natural, other).isLessThanOrEqualTo(own);
      if (other.family() != natural.family()) assertThat(there).as("%s jogando de %s (outra família)", natural, other).isLessThan(own);
    }
  }

  @Test
  void o_mesmo_jogador_tem_overall_diferente_em_cada_posicao() {
    PlayerAttributes qb = RatingProfiles.specialist(Position.QB, 90, 45);
    Map<Position, Integer> all = OverallCalculator.overallByPosition(qb);
    assertThat(all.get(Position.QB)).isEqualTo(90);
    assertThat(all.get(Position.DT)).isLessThan(50);
    assertThat(all.values().stream().distinct().count()).as("overalls distintos entre as 20 posições").isGreaterThan(5);
  }

  @Test
  void a_conta_e_inteira_e_arredonda_meio_para_cima() {
    // K: potência 420‰, precisão 450‰, consciência 70‰, calma 60‰ → 61×420 + 60×580 = 60 420 → 60,42 → 60
    assertThat(OverallCalculator.overall(PlayerAttributes.uniform(60).with(AttributeKey.KICK_POWER, 61), Position.K)).isEqualTo(60);
    // P: calma 70‰ → 60×70 + 50×930 = 50 700 → 50,7 → 51
    assertThat(OverallCalculator.overall(PlayerAttributes.uniform(50).with(AttributeKey.POISE, 60), Position.P)).isEqualTo(51);
    // QB: velocidade e agilidade 20‰ cada → 70×20 + 55×20 + 50×960 = 50 500 → exatamente 50,5 → 51
    assertThat(OverallCalculator.overall(PlayerAttributes.uniform(50).with(AttributeKey.SPEED, 70).with(AttributeKey.AGILITY, 55), Position.QB)).isEqualTo(51);
    // 50,49… fica em 50: QB com velocidade 74 → 74×20 + 50×980 = 50 480
    assertThat(OverallCalculator.overall(PlayerAttributes.uniform(50).with(AttributeKey.SPEED, 74), Position.QB)).isEqualTo(50);
    // valor fora de [0, 100] é limitado antes da conta
    PlayerAttributes raw = PlayerAttributes.uniform(100);
    raw.setKickPower(140);
    assertThat(OverallCalculator.overall(raw, Position.K)).isEqualTo(100);
  }

  @Test
  void avaliacao_avisa_fora_de_posicao_e_respeita_a_secundaria() {
    PlayerAttributes a = RatingProfiles.specialist(Position.CB, 80, 50);
    assertThat(OverallCalculator.rate(a, Position.CB, null, Position.CB).outOfPosition()).isFalse();
    assertThat(OverallCalculator.rate(a, Position.CB, null, Position.FS).outOfPosition()).isTrue();
    assertThat(OverallCalculator.rate(a, Position.CB, Position.FS, Position.FS).outOfPosition()).isFalse();
    PositionRating r = OverallCalculator.rate(a, Position.CB);
    assertThat(r.overall()).isEqualTo(80);
    assertThat(r.tier()).isEqualTo(CardTier.GOLD);
    assertThat(r.macros().family()).isEqualTo(Position.CB.family());
    assertThat(r.version()).isEqualTo("OVR v1");
  }

  @Test
  void sem_atributos_ou_sem_posicao_nao_inventa_overall() {
    assertThat(OverallCalculator.overall(null, Position.QB)).isZero();
    assertThat(OverallCalculator.overall(PlayerAttributes.uniform(80), null)).isZero();
  }
}
