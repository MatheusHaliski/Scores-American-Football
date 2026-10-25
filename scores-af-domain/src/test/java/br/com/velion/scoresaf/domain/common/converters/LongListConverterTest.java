package br.com.velion.scoresaf.domain.common.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class LongListConverterTest {
  private final LongListConverter c = new LongListConverter();

  @Test
  void ida_e_volta() {
    assertThat(c.convertToDatabaseColumn(List.of(1L, 25_000_000_00L))).isEqualTo("[1,2500000000]");
    assertThat(c.convertToEntityAttribute("[1, 2500000000]")).containsExactly(1L, 2_500_000_000L);
    assertThat(c.convertToDatabaseColumn(null)).isEqualTo("[]");
    assertThat(c.convertToEntityAttribute("[]")).isEmpty();
    assertThat(c.convertToEntityAttribute(null)).isEmpty();
  }

  @Test
  void texto_malformado_falha() {
    assertThatThrownBy(() -> c.convertToEntityAttribute("1,2")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> c.convertToEntityAttribute("[a]")).isInstanceOf(NumberFormatException.class);
  }
}
