package br.com.velion.scoresaf.application.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReservedNamesTest {
  @Test
  void compara_depois_de_normalizar() {
    assertThat(ReservedNames.isReserved("Admin")).isTrue();
    assertThat(ReservedNames.isReserved(" ÁDMIN ")).isTrue();
    assertThat(ReservedNames.isReserved("a.d.m.i.n")).isTrue();
    assertThat(ReservedNames.isReserved("Suporte")).isTrue();
    assertThat(ReservedNames.isReserved("Velion Tech")).isTrue();
    assertThat(ReservedNames.isReserved("demo_ana")).isTrue();
    assertThat(ReservedNames.isReserved("E2E_bot")).isTrue();
    assertThat(ReservedNames.isReserved("Ana Manager")).isFalse();
    assertThat(ReservedNames.isReserved("Administração Silva")).isFalse();
    assertThat(ReservedNames.isReserved(null)).isFalse();
  }
}
