package br.com.velion.scoresaf.domain.engine.ratings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Níveis do card nas fronteiras (docs/01 §3.4). */
class CardTierTest {
  @ParameterizedTest
  @CsvSource({"0,BRONZE", "40,BRONZE", "64,BRONZE", "65,SILVER", "74,SILVER", "75,GOLD", "84,GOLD", "85,ICON", "89,ICON", "90,ELITE", "99,ELITE", "100,ELITE"})
  void nivel_pelo_overall(int overall, CardTier expected) {
    assertThat(CardTier.of(overall)).isEqualTo(expected);
  }
}
