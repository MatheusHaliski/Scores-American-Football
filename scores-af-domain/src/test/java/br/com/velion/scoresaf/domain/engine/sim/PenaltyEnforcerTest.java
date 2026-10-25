package br.com.velion.scoresaf.domain.engine.sim;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.engine.sim.PenaltyEnforcer.DownState;
import br.com.velion.scoresaf.domain.engine.sim.PenaltyEnforcer.Enforcement;
import br.com.velion.scoresaf.domain.shared.PenaltyCode;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Cada falta do catálogo (docs/01 §3.1) aplica as jardas, a primeira descida automática e a perda de descida certas. Situação base:
 * 2ª e 6 na própria 40 (linha a conquistar na 46).
 */
class PenaltyEnforcerTest {
  private static final DownState BASE = new DownState(2, 6, 40);

  /** Esperado: jarda nova, descida, distância, primeira descida. */
  record Expected(boolean byDefense, int spot, int yd, int down, int distance, boolean firstDown) {}

  private static final Map<PenaltyCode, Expected> CATALOG = new EnumMap<>(Map.ofEntries(
      Map.entry(PenaltyCode.FALSE_START, new Expected(false, 0, 35, 2, 11, false)),
      Map.entry(PenaltyCode.OFFSIDE, new Expected(true, 0, 45, 2, 1, false)),
      Map.entry(PenaltyCode.ENCROACHMENT, new Expected(true, 0, 45, 2, 1, false)),
      Map.entry(PenaltyCode.HOLDING_OFFENSE, new Expected(false, 0, 30, 2, 16, false)),
      Map.entry(PenaltyCode.HOLDING_DEFENSE, new Expected(true, 0, 45, 1, 10, true)),
      Map.entry(PenaltyCode.PASS_INTERFERENCE_OFFENSE, new Expected(false, 0, 30, 2, 16, false)),
      Map.entry(PenaltyCode.PASS_INTERFERENCE_DEFENSE, new Expected(true, 72, 72, 1, 10, true)),
      Map.entry(PenaltyCode.ROUGHING_THE_PASSER, new Expected(true, 0, 55, 1, 10, true)),
      Map.entry(PenaltyCode.UNNECESSARY_ROUGHNESS, new Expected(true, 0, 55, 1, 10, true)),
      Map.entry(PenaltyCode.DELAY_OF_GAME, new Expected(false, 0, 35, 2, 11, false)),
      Map.entry(PenaltyCode.ILLEGAL_FORMATION, new Expected(false, 0, 35, 2, 11, false)),
      Map.entry(PenaltyCode.FACEMASK, new Expected(true, 0, 55, 1, 10, true)),
      // grounding com o QB 7 jardas atrás da linha: 10 jardas a partir do ponto anterior e perda de descida → 3ª e 16
      Map.entry(PenaltyCode.INTENTIONAL_GROUNDING, new Expected(false, 33, 30, 3, 16, false))));

  @Test
  void cada_falta_do_catalogo_aplica_jardas_primeira_descida_e_perda_de_descida() {
    assertThat(CATALOG.keySet()).as("o teste cobre o catálogo inteiro").containsExactlyInAnyOrder(PenaltyCode.values());
    CATALOG.forEach((code, exp) -> {
      Enforcement e = PenaltyEnforcer.enforce(BASE, code, exp.byDefense(), exp.spot());
      assertThat(e.next()).as(code.name()).isEqualTo(new DownState(exp.down(), exp.distance(), exp.yd()));
      assertThat(e.firstDown()).as(code + " primeira descida").isEqualTo(exp.firstDown());
      assertThat(e.yards()).as(code + " jardas").isEqualTo(exp.yd() - BASE.yd());
      assertThat(e.firstDown()).as(code + " 1ª descida automática do catálogo").isEqualTo(exp.byDefense() && code.automaticFirstDown());
      assertThat(e.next().down() > BASE.down()).as(code + " perda de descida").isEqualTo(code.lossOfDown());
    });
  }

  @Test
  void falta_da_defesa_sem_primeira_descida_automatica_da_primeira_descida_quando_alcanca_a_linha() {
    Enforcement e = PenaltyEnforcer.enforce(new DownState(3, 4, 40), PenaltyCode.OFFSIDE, true, 0);
    assertThat(e.firstDown()).isTrue();
    assertThat(e.next()).isEqualTo(new DownState(1, 10, 45));
  }

  @Test
  void faltas_de_lado_either_cometidas_pelo_ataque_nao_dao_primeira_descida_e_repetem_a_descida() {
    for (PenaltyCode c : new PenaltyCode[] {PenaltyCode.UNNECESSARY_ROUGHNESS, PenaltyCode.FACEMASK}) {
      Enforcement e = PenaltyEnforcer.enforce(BASE, c, false, 0);
      assertThat(e.next()).as(c.name()).isEqualTo(new DownState(2, 21, 25));
      assertThat(e.firstDown()).isFalse();
    }
  }

  @Test
  void metade_da_distancia_ate_o_gol() {
    // holding do ataque na própria 8: só 4 jardas
    assertThat(PenaltyEnforcer.enforce(new DownState(1, 10, 8), PenaltyCode.HOLDING_OFFENSE, false, 0).next()).isEqualTo(new DownState(1, 14, 4));
    // roughing na 10 do adversário: 5 jardas, primeira e gol
    assertThat(PenaltyEnforcer.enforce(new DownState(3, 7, 90), PenaltyCode.ROUGHING_THE_PASSER, true, 0).next()).isEqualTo(new DownState(1, 5, 95));
  }

  @Test
  void interferencia_defensiva_na_end_zone_poe_a_bola_na_linha_de_1_e_tem_minimo_de_1_jarda() {
    assertThat(PenaltyEnforcer.enforce(new DownState(2, 8, 80), PenaltyCode.PASS_INTERFERENCE_DEFENSE, true, 105).next()).isEqualTo(new DownState(1, 1, 99));
    assertThat(PenaltyEnforcer.enforce(new DownState(2, 8, 40), PenaltyCode.PASS_INTERFERENCE_DEFENSE, true, 38).next()).isEqualTo(new DownState(1, 10, 41));
  }

  @Test
  void grounding_na_4a_descida_entrega_a_bola_e_na_end_zone_e_safety() {
    assertThat(PenaltyEnforcer.enforce(new DownState(4, 3, 50), PenaltyCode.INTENTIONAL_GROUNDING, false, 43).turnoverOnDowns()).isTrue();
    assertThat(PenaltyEnforcer.enforce(new DownState(2, 10, 5), PenaltyCode.INTENTIONAL_GROUNDING, false, -2).safety()).isTrue();
    // QB 18 jardas atrás: perde as 18, não só 10
    assertThat(PenaltyEnforcer.enforce(new DownState(2, 10, 60), PenaltyCode.INTENTIONAL_GROUNDING, false, 42).next()).isEqualTo(new DownState(3, 28, 42));
  }
}
