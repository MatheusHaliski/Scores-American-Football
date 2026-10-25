package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Playbook da franquia (RF61, D-58): o peso de cada conceito de jogada por situação e o peso de cada cobertura. É o que a chamada
 * da IA ({@code PlayCaller}) usa junto com o {@link GamePlan}: o plano decide corrida × passe; o playbook decide qual corrida e qual
 * passe. Peso em porcentagem do padrão da liga, 0–200: 100 mantém a frequência que o motor já usa, 0 desativa o conceito naquela
 * situação, 200 dobra. Com tudo em 100 ({@link #defaults()}) o motor dá exatamente o mesmo jogo de antes (mesmos sorteios).
 *
 * @param concepts peso por situação e conceito (sempre completo: o construtor preenche o que faltar com 100)
 * @param coverages peso por cobertura nas chamadas sem blitz (sempre completo)
 */
public record Playbook(Map<Situation, Map<PlayConcept, Integer>> concepts, Map<Coverage, Integer> coverages) {

  /** Peso neutro (frequência padrão do motor). */
  public static final int NEUTRAL = 100;
  public static final int MIN_WEIGHT = 0;
  public static final int MAX_WEIGHT = 200;

  /** Conceitos de corrida que o playbook pondera (a escolha corrida × passe é do plano de jogo). */
  public static final List<PlayConcept> RUN_CONCEPTS = List.of(PlayConcept.INSIDE_RUN, PlayConcept.OUTSIDE_RUN, PlayConcept.QB_SNEAK);
  /** Conceitos de passe que o playbook pondera. */
  public static final List<PlayConcept> PASS_CONCEPTS = List.of(PlayConcept.SCREEN, PlayConcept.SHORT_PASS, PlayConcept.MEDIUM_PASS,
      PlayConcept.DEEP_PASS, PlayConcept.PLAY_ACTION);

  /**
   * Situação da chamada, na ordem de precedência inversa: o motor usa a primeira que valer de baixo para cima (2 minutos, red zone,
   * 3ª e longa, 3ª descida, descidas iniciais).
   */
  public enum Situation {
    /** 1ª e 2ª descidas. */
    EARLY_DOWN,
    /** 3ª ou 4ª descida com menos de 8 jardas. */
    THIRD_DOWN,
    /** 3ª ou 4ª descida com 8 jardas ou mais. */
    THIRD_AND_LONG,
    /** Da linha de 20 do adversário para dentro. */
    RED_ZONE,
    /** Ataque acelerado do fim de cada tempo ("two-minute drill"). */
    TWO_MINUTE
  }

  public Playbook {
    Map<Situation, Map<PlayConcept, Integer>> c = new EnumMap<>(Situation.class);
    for (Situation s : Situation.values()) {
      Map<PlayConcept, Integer> given = concepts == null ? null : concepts.get(s);
      Map<PlayConcept, Integer> w = new EnumMap<>(PlayConcept.class);
      for (PlayConcept p : RUN_CONCEPTS) w.put(p, clamp(given == null ? null : given.get(p)));
      for (PlayConcept p : PASS_CONCEPTS) w.put(p, clamp(given == null ? null : given.get(p)));
      c.put(s, Collections.unmodifiableMap(w));
    }
    concepts = Collections.unmodifiableMap(c);
    Map<Coverage, Integer> cv = new EnumMap<>(Coverage.class);
    for (Coverage k : Coverage.values()) cv.put(k, clamp(coverages == null ? null : coverages.get(k)));
    coverages = Collections.unmodifiableMap(cv);
  }

  public static Playbook defaults() { return new Playbook(null, null); }

  public int weight(Situation s, PlayConcept c) { return concepts.get(s).getOrDefault(c, NEUTRAL); }

  public int coverage(Coverage c) { return coverages.get(c); }

  /** Multiplicador do conceito na situação (1.0 = padrão). */
  public double factor(Situation s, PlayConcept c) { return weight(s, c) / (double) NEUTRAL; }

  public double coverageFactor(Coverage c) { return coverage(c) / (double) NEUTRAL; }

  /** Todos os conceitos da lista em 100 na situação: o motor usa o caminho original, sem nenhuma conta a mais. */
  public boolean neutral(Situation s, List<PlayConcept> group) {
    for (PlayConcept c : group) if (weight(s, c) != NEUTRAL) return false;
    return true;
  }

  public boolean neutralCoverages() {
    for (int w : coverages.values()) if (w != NEUTRAL) return false;
    return true;
  }

  private static int clamp(Integer v) { return v == null ? NEUTRAL : Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, v)); }
}
