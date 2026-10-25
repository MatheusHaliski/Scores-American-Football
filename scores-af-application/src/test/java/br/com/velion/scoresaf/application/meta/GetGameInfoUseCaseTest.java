package br.com.velion.scoresaf.application.meta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GetGameInfoUseCaseTest {
  @Test
  void devolve_a_versao_configurada() {
    assertThat(new GetGameInfoUseCase("1.2.3").execute().version()).isEqualTo("1.2.3");
  }
}
