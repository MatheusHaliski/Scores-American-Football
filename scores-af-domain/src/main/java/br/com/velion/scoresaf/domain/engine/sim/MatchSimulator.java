package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.AdvanceMode;
import br.com.velion.scoresaf.domain.engine.model.MatchDecision;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.MatchSetup;
import br.com.velion.scoresaf.domain.engine.model.MatchSnapshot;
import br.com.velion.scoresaf.domain.engine.model.PendingDecision;
import java.util.List;
import java.util.function.Function;

/**
 * Motor de partida por descida (RF42) — contrato. Determinístico: mesma {@link MatchSetup#seed()} + mesmas decisões ⇒ mesmos
 * eventos, placar e estatísticas, com ou sem paradas no meio. Implementação padrão: {@link DefaultMatchSimulator}.
 */
public interface MatchSimulator {
  /** Estado inicial (antes do cara ou coroa). */
  MatchSnapshot start(MatchSetup setup);

  /**
   * Aplica as decisões (chamada de jogada, decisão automática por prazo vencido, tempo técnico) e avança até onde o modo pede.
   * Todo modo para numa decisão pendente ({@link MatchSnapshot#awaitingDecision()}) e no fim do jogo.
   *
   * @throws IllegalArgumentException decisão que não corresponde à decisão aberta ou opção não oferecida
   * @throws IllegalStateException tempo técnico sem tempos restantes ou fora de bola morta, partida encerrada
   */
  MatchSnapshot advance(MatchSnapshot snapshot, AdvanceMode mode, List<MatchDecision> decisions);

  /**
   * Aplica as decisões sem avançar (ex.: tempo técnico pedido no painel, RF29): mesma validação de {@link #advance}.
   *
   * @throws IllegalArgumentException decisão que não corresponde à decisão aberta
   * @throws IllegalStateException tempo técnico sem tempos restantes ou fora de bola morta, partida encerrada
   */
  MatchSnapshot apply(MatchSnapshot snapshot, List<MatchDecision> decisions);

  /**
   * Ajuste do plano de jogo (com o playbook) de uma franquia no meio da partida (RF29): devolve o snapshot com o plano trocado no
   * setup, a versão +1 e o ajuste no log. Vale a partir do próximo snap; os sorteios não mudam de lugar (D-29), então quem retoma
   * do snapshot ajustado joga o mesmo jogo.
   *
   * @throws IllegalStateException partida encerrada ou esperando uma decisão
   * @throws IllegalArgumentException franquia fora da partida
   */
  MatchSnapshot adjust(MatchSnapshot snapshot, java.util.UUID clubId, br.com.velion.scoresaf.domain.engine.model.GamePlan plan);

  /** Resultado consolidado. Exige partida encerrada. */
  MatchResult finish(MatchSnapshot snapshot);

  /** Jogo inteiro; cada decisão aberta é respondida por {@code policy}. */
  default MatchResult simulate(MatchSetup setup, Function<PendingDecision, MatchDecision> policy) {
    MatchSnapshot s = advance(start(setup), AdvanceMode.FULL, List.of());
    while (!s.finished()) {
      List<MatchDecision> d = s.awaitingDecision() ? List.of(policy.apply(s.pendingDecision())) : List.of();
      s = advance(s, AdvanceMode.FULL, d);
    }
    return finish(s);
  }

  /** Jogo inteiro sem intervenção humana: toda decisão aberta vence o prazo e a IA decide. */
  default MatchResult simulate(MatchSetup setup) { return simulate(setup, p -> new MatchDecision.Auto(p.id())); }
}
