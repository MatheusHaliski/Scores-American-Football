package br.com.velion.scoresaf.domain.career;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import org.junit.jupiter.api.Test;

/**
 * Envelhecimento e progressão do RF35 (D-101): jovem sobe rumo ao potencial, auge estável, veterano perde mais no físico que na
 * técnica (e ganha experiência), especialista envelhece mais devagar, tudo determinístico e dentro de 0–100.
 */
class PlayerProgressionTest {
  private static final PlayerAttributes BASE = PlayerAttributes.uniform(70);

  @Test
  void jovem_sobe_rumo_ao_potencial_ate_4_por_ano() {
    PlayerProgression.Change c = PlayerProgression.apply(BASE, 22, 70, 85, false);
    assertThat(c.physicalDelta()).isEqualTo(4);
    assertThat(c.attributes().get(AttributeKey.SPEED)).isEqualTo(74);
    assertThat(c.attributes().get(AttributeKey.HANDS)).isEqualTo(74);
    assertThat(PlayerProgression.apply(BASE, 22, 70, 74, false).otherDelta()).isEqualTo(2);
    assertThat(PlayerProgression.apply(BASE, 22, 70, 70, false).otherDelta()).isZero();
  }

  @Test
  void auge_sobe_1_so_com_margem_de_potencial() {
    assertThat(PlayerProgression.apply(BASE, 27, 70, 75, false).otherDelta()).isEqualTo(1);
    assertThat(PlayerProgression.apply(BASE, 27, 70, 71, false).otherDelta()).isZero();
  }

  @Test
  void veterano_perde_mais_no_fisico_e_ganha_experiencia() {
    PlayerProgression.Change c = PlayerProgression.apply(BASE, 32, 70, 70, false);
    assertThat(c.attributes().get(AttributeKey.SPEED)).isEqualTo(68);
    assertThat(c.attributes().get(AttributeKey.DEEP_ACCURACY)).isEqualTo(69);
    assertThat(c.attributes().get(AttributeKey.AWARENESS)).isEqualTo(71);
    PlayerProgression.Change old = PlayerProgression.apply(BASE, 35, 70, 70, false);
    assertThat(old.attributes().get(AttributeKey.SPEED)).isEqualTo(67);
    assertThat(old.attributes().get(AttributeKey.TACKLE)).isEqualTo(68);
    assertThat(PlayerProgression.apply(BASE, 30, 70, 70, false).attributes().get(AttributeKey.SPEED)).isEqualTo(69);
  }

  @Test
  void especialista_envelhece_dois_anos_mais_devagar() {
    assertThat(PlayerProgression.apply(BASE, 32, 70, 70, true).physicalDelta()).isEqualTo(-1);
    assertThat(PlayerProgression.apply(BASE, 32, 70, 70, false).physicalDelta()).isEqualTo(-2);
  }

  @Test
  void deterministico_e_dentro_de_0_a_100_sem_mudar_a_entrada() {
    PlayerAttributes top = PlayerAttributes.uniform(99);
    PlayerProgression.Change a = PlayerProgression.apply(top, 21, 99, 99, false), b = PlayerProgression.apply(top, 21, 99, 99, false);
    assertThat(a.attributes().asMap()).isEqualTo(b.attributes().asMap());
    assertThat(PlayerProgression.apply(PlayerAttributes.uniform(98), 21, 60, 99, false).attributes().get(AttributeKey.SPEED)).isEqualTo(100);
    assertThat(PlayerProgression.apply(PlayerAttributes.uniform(1), 36, 30, 30, false).attributes().get(AttributeKey.SPEED)).isZero();
    assertThat(BASE.get(AttributeKey.SPEED)).isEqualTo(70);
  }
}
