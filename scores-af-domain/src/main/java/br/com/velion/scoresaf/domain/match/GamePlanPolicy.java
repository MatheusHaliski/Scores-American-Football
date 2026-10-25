package br.com.velion.scoresaf.domain.match;

import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.engine.model.Playbook;
import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Regras do plano de jogo e do playbook (RF61, RF29): o que a API recebe vira o {@link GamePlan} e o {@link Playbook} do motor, ou a
 * lista de problemas (todos de uma vez, para o front marcar cada campo). Plano: {@code passRatio} de 20 a 80 (a faixa que o motor
 * aceita) e os enums do motor; defesa base só 4-3 ou 3-4. Playbook: pesos de 0 a 200 nas situações, conceitos e coberturas
 * conhecidos, sem repetição; toda situação precisa de pelo menos uma corrida (por dentro ou por fora) e um passe, e pelo menos duas
 * coberturas precisam ficar ativas (playbook mínimo, D-58). O que não vier fica em 100 (o PUT troca o playbook inteiro).
 */
public final class GamePlanPolicy {
  private GamePlanPolicy() {}

  public static final String REQUIRED = "REQUIRED";
  public static final String UNKNOWN_VALUE = "UNKNOWN_VALUE";
  public static final String PASS_RATIO_OUT_OF_RANGE = "PASS_RATIO_OUT_OF_RANGE";
  public static final String UNKNOWN_SITUATION = "UNKNOWN_SITUATION";
  public static final String DUPLICATE_SITUATION = "DUPLICATE_SITUATION";
  public static final String UNKNOWN_ITEM = "UNKNOWN_ITEM";
  public static final String DUPLICATE_ITEM = "DUPLICATE_ITEM";
  public static final String WEIGHT_OUT_OF_RANGE = "WEIGHT_OUT_OF_RANGE";
  public static final String NO_RUN_CONCEPT = "NO_RUN_CONCEPT";
  public static final String NO_PASS_CONCEPT = "NO_PASS_CONCEPT";
  public static final String NO_COVERAGE = "NO_COVERAGE";
  /** Coberturas ativas mínimas (a defesa precisa variar). */
  public static final int MIN_ACTIVE_COVERAGES = 2;

  /** Problema num campo ({@code field}: {@code passRatio}, {@code EARLY_DOWN.DEEP_PASS}, {@code coverages.COVER_3}...). */
  public record Issue(String code, String field, String value) {}

  public record PlanResult(GamePlan plan, List<Issue> errors) {
    public boolean valid() { return errors.isEmpty(); }
  }

  /** Peso de um item (conceito ou cobertura). */
  public record WeightInput(String item, Integer weight) {}

  public record SituationInput(String situation, List<WeightInput> concepts) {}

  public record PlaybookResult(Playbook playbook, List<Issue> errors) {
    public boolean valid() { return errors.isEmpty(); }

    /** Só faltam corrida, passe ou coberturas (o resto está certo): o playbook está incompleto, não inválido. */
    public boolean incompleteOnly() {
      return !errors.isEmpty() && errors.stream().allMatch(i -> NO_RUN_CONCEPT.equals(i.code()) || NO_PASS_CONCEPT.equals(i.code()) || NO_COVERAGE.equals(i.code()));
    }
  }

  public static PlanResult plan(Integer passRatio, String aggression, String tempo, String focus, String blitz, String baseDefense) {
    List<Issue> errors = new ArrayList<>();
    if (passRatio == null) errors.add(new Issue(REQUIRED, "passRatio", null));
    else if (passRatio < GamePlan.MIN_PASS_RATIO || passRatio > GamePlan.MAX_PASS_RATIO) {
      errors.add(new Issue(PASS_RATIO_OUT_OF_RANGE, "passRatio", String.valueOf(passRatio)));
    }
    GamePlan.Aggression a = parse(GamePlan.Aggression.class, "fourthDownAggression", aggression, errors);
    GamePlan.Tempo t = parse(GamePlan.Tempo.class, "tempo", tempo, errors);
    GamePlan.DefensiveFocus f = parse(GamePlan.DefensiveFocus.class, "defensiveFocus", focus, errors);
    GamePlan.Emphasis b = parse(GamePlan.Emphasis.class, "blitzFrequency", blitz, errors);
    DefensePackage d = parse(DefensePackage.class, "baseDefense", baseDefense, errors);
    if (d != null && d != DefensePackage.BASE_4_3 && d != DefensePackage.BASE_3_4) {
      errors.add(new Issue(UNKNOWN_VALUE, "baseDefense", baseDefense));
      d = null;
    }
    if (!errors.isEmpty()) return new PlanResult(null, List.copyOf(errors));
    return new PlanResult(new GamePlan(passRatio, a, t, f, b, d), List.of());
  }

  public static PlaybookResult playbook(List<SituationInput> situations, List<WeightInput> coverages) {
    List<Issue> errors = new ArrayList<>();
    Map<Playbook.Situation, Map<PlayConcept, Integer>> concepts = new EnumMap<>(Playbook.Situation.class);
    Set<Playbook.Situation> seen = new LinkedHashSet<>();
    for (SituationInput si : situations == null ? List.<SituationInput>of() : situations) {
      if (si == null) continue;
      Playbook.Situation s = enumOf(Playbook.Situation.class, si.situation());
      if (s == null) { errors.add(new Issue(UNKNOWN_SITUATION, "situations", si.situation())); continue; }
      if (!seen.add(s)) { errors.add(new Issue(DUPLICATE_SITUATION, "situations", s.name())); continue; }
      Map<PlayConcept, Integer> w = new EnumMap<>(PlayConcept.class);
      for (WeightInput wi : si.concepts() == null ? List.<WeightInput>of() : si.concepts()) {
        if (wi == null) continue;
        PlayConcept c = enumOf(PlayConcept.class, wi.item());
        String field = s.name() + "." + (wi.item() == null ? "" : wi.item());
        if (c == null || (!Playbook.RUN_CONCEPTS.contains(c) && !Playbook.PASS_CONCEPTS.contains(c))) { errors.add(new Issue(UNKNOWN_ITEM, field, wi.item())); continue; }
        if (w.containsKey(c)) { errors.add(new Issue(DUPLICATE_ITEM, field, c.name())); continue; }
        if (!inRange(wi.weight())) { errors.add(new Issue(WEIGHT_OUT_OF_RANGE, field, String.valueOf(wi.weight()))); continue; }
        w.put(c, wi.weight());
      }
      concepts.put(s, w);
    }
    Map<Coverage, Integer> cov = new EnumMap<>(Coverage.class);
    for (WeightInput wi : coverages == null ? List.<WeightInput>of() : coverages) {
      if (wi == null) continue;
      Coverage c = enumOf(Coverage.class, wi.item());
      String field = "coverages." + (wi.item() == null ? "" : wi.item());
      if (c == null) { errors.add(new Issue(UNKNOWN_ITEM, field, wi.item())); continue; }
      if (cov.containsKey(c)) { errors.add(new Issue(DUPLICATE_ITEM, field, c.name())); continue; }
      if (!inRange(wi.weight())) { errors.add(new Issue(WEIGHT_OUT_OF_RANGE, field, String.valueOf(wi.weight()))); continue; }
      cov.put(c, wi.weight());
    }
    Playbook book = new Playbook(concepts, cov);
    for (Playbook.Situation s : Playbook.Situation.values()) {
      if (book.weight(s, PlayConcept.INSIDE_RUN) == 0 && book.weight(s, PlayConcept.OUTSIDE_RUN) == 0) errors.add(new Issue(NO_RUN_CONCEPT, s.name(), null));
      if (Playbook.PASS_CONCEPTS.stream().allMatch(c -> book.weight(s, c) == 0)) errors.add(new Issue(NO_PASS_CONCEPT, s.name(), null));
    }
    if (book.coverages().values().stream().filter(w -> w > 0).count() < MIN_ACTIVE_COVERAGES) errors.add(new Issue(NO_COVERAGE, "coverages", null));
    return new PlaybookResult(errors.isEmpty() ? book : null, List.copyOf(errors));
  }

  private static boolean inRange(Integer w) { return w != null && w >= Playbook.MIN_WEIGHT && w <= Playbook.MAX_WEIGHT; }

  private static <E extends Enum<E>> E parse(Class<E> type, String field, String raw, List<Issue> errors) {
    if (raw == null || raw.isBlank()) { errors.add(new Issue(REQUIRED, field, null)); return null; }
    E e = enumOf(type, raw);
    if (e == null) errors.add(new Issue(UNKNOWN_VALUE, field, raw));
    return e;
  }

  private static <E extends Enum<E>> E enumOf(Class<E> type, String raw) {
    if (raw == null) return null;
    try { return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
  }
}
