package br.com.velion.scoresaf.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.dto.CareerDtos;
import org.junit.jupiter.api.Test;

/** Bloqueio da dispensa pelo teto (Q-24, D-80): só quando o dead money da temporada faz o comprometido subir e passar do teto. */
class CapSheetTest {
  private static CareerDtos.CapDto cap(long salaryCap, long used) { return new CareerDtos.CapDto(salaryCap, used, 0, salaryCap - used, 0); }

  @Test
  void dead_money_que_estoura_o_teto_recusa_com_teto_comprometido_e_excedente() {
    assertThatThrownBy(() -> CapSheet.requireRoomForRelease(cap(1_000, 900), 50, 200)).isInstanceOfSatisfying(BusinessRuleException.class, e -> {
      assertThat(e.code()).isEqualTo("CAP_EXCEEDED");
      assertThat(e.details()).containsEntry("salaryCap", 1_000L).containsEntry("capUsed", 1_050L).containsEntry("excess", 50L);
      assertThat(e.args()).hasSize(3);
    });
  }

  @Test
  void no_limite_do_teto_ou_sem_aumentar_o_comprometido_passa() {
    assertThatCode(() -> CapSheet.requireRoomForRelease(cap(1_000, 900), 50, 150)).as("exatamente no teto").doesNotThrowAnyException();
    assertThatCode(() -> CapSheet.requireRoomForRelease(cap(1_000, 1_200), 300, 100)).as("acima do teto, mas a dispensa reduz o comprometido")
        .doesNotThrowAnyException();
    assertThatCode(() -> CapSheet.requireRoomForRelease(cap(1_000, 1_200), 100, 100)).as("acima do teto, sem mudar o comprometido")
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> CapSheet.requireRoomForRelease(cap(1_000, 1_200), 100, 101)).as("acima do teto e a dispensa aumenta")
        .isInstanceOf(BusinessRuleException.class);
  }
}
