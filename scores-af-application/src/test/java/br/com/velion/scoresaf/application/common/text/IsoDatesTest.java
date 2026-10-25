package br.com.velion.scoresaf.application.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class IsoDatesTest {
  @Test
  void sem_data_vira_null_e_nao_a_string_null() {
    assertThat(IsoDates.of(null)).isNull();
  }

  @Test
  void data_vira_iso() {
    assertThat(IsoDates.of(LocalDate.of(1998, 3, 7))).isEqualTo("1998-03-07");
  }
}
