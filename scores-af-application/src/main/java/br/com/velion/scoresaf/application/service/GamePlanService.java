package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.port.out.persistence.GamePlanSetupRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlaybookSetupRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlaybookWeightRepository;
import br.com.velion.scoresaf.application.usecase.rf42.MatchTeams;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.engine.model.Playbook;
import br.com.velion.scoresaf.domain.match.GamePlanSetup;
import br.com.velion.scoresaf.domain.match.PlaybookSetup;
import br.com.velion.scoresaf.domain.match.PlaybookWeight;
import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plano de jogo e playbook salvos da franquia do usuário (RF61): leitura (criados na primeira leitura com o padrão do estilo do
 * head coach e o playbook neutro), gravação (o playbook troca todos os pesos: DELETE em lote + INSERT) e o plano completo no formato do
 * motor para o kickoff ({@link #engineFor}). As franquias da IA usam o plano do estilo do head coach delas ({@link MatchTeams#plan}).
 */
@Service
public class GamePlanService {
  private final GamePlanSetupRepository plans; private final PlaybookSetupRepository playbooks; private final PlaybookWeightRepository weights;

  public GamePlanService(GamePlanSetupRepository plans, PlaybookSetupRepository playbooks, PlaybookWeightRepository weights) {
    this.plans = plans; this.playbooks = playbooks; this.weights = weights;
  }

  public Optional<GamePlanSetup> savedPlan(CareerSave save, Club club) { return plans.findByCareerIdAndClubId(save.getId(), club.getId()); }

  public Optional<PlaybookSetup> savedPlaybook(CareerSave save, Club club) { return playbooks.findByCareerIdAndClubId(save.getId(), club.getId()); }

  /**
   * Plano da franquia, criado na primeira leitura com o padrão do estilo do head coach ({@code revision} 0 = nunca gravado pelo
   * usuário), como o depth chart (D-53): assim a versão otimista existe desde o GET e um PUT com versão velha dá 409.
   */
  @Transactional
  public GamePlanSetup planOrCreate(CareerSave save, Club club) {
    return savedPlan(save, club).orElseGet(() -> {
      GamePlanSetup g = new GamePlanSetup();
      g.setCareer(save); g.setClub(club);
      g.apply(MatchTeams.plan(club.getManager()));
      g.setRevision(0);
      return plans.saveAndFlush(g);
    });
  }

  @Transactional
  public GamePlanSetup savePlan(GamePlanSetup g, GamePlan plan) {
    g.apply(plan);
    return plans.saveAndFlush(g);
  }

  /** Playbook da franquia, criado na primeira leitura sem pesos (tudo em 100, o padrão da liga). */
  @Transactional
  public PlaybookSetup playbookOrCreate(CareerSave save, Club club) {
    return savedPlaybook(save, club).orElseGet(() -> {
      PlaybookSetup p = new PlaybookSetup();
      p.setCareer(save); p.setClub(club);
      return playbooks.saveAndFlush(p);
    });
  }

  /** Pesos do playbook gravado (o que não tiver linha fica em 100). */
  public Playbook read(PlaybookSetup p) {
    Map<Playbook.Situation, Map<PlayConcept, Integer>> concepts = new EnumMap<>(Playbook.Situation.class);
    Map<Coverage, Integer> coverages = new EnumMap<>(Coverage.class);
    for (PlaybookWeight w : weights.findByPlaybookId(p.getId())) {
      if (PlaybookWeight.COVERAGE.equals(w.getScope())) {
        coverages.put(Coverage.valueOf(w.getItem()), w.getWeight());
      } else {
        concepts.computeIfAbsent(Playbook.Situation.valueOf(w.getScope()), k -> new EnumMap<>(PlayConcept.class)).put(PlayConcept.valueOf(w.getItem()), w.getWeight());
      }
    }
    return new Playbook(concepts, coverages);
  }

  @Transactional
  public PlaybookSetup savePlaybook(PlaybookSetup p, Playbook book) {
    p.touch();
    PlaybookSetup saved = playbooks.saveAndFlush(p);
    weights.deleteByPlaybookId(saved.getId());
    List<PlaybookWeight> rows = new ArrayList<>();
    book.concepts().forEach((s, m) -> m.forEach((c, w) -> rows.add(PlaybookWeight.of(saved, s.name(), c.name(), w))));
    book.coverages().forEach((c, w) -> rows.add(PlaybookWeight.of(saved, PlaybookWeight.COVERAGE, c.name(), w)));
    weights.saveAll(rows);
    return saved;
  }

  /** Plano completo (tendências + playbook) da franquia do usuário para o kickoff. */
  public GamePlan engineFor(CareerSave save, Club club) {
    GamePlan plan = savedPlan(save, club).map(GamePlanSetup::toEngine).orElseGet(() -> MatchTeams.plan(club.getManager()));
    return plan.withPlaybook(savedPlaybook(save, club).map(this::read).orElse(Playbook.defaults()));
  }

  /** Quantos pesos fogem do padrão (100). */
  public static int customWeights(Playbook book) {
    int n = 0;
    for (Map<PlayConcept, Integer> m : book.concepts().values()) for (int w : m.values()) if (w != Playbook.NEUTRAL) n++;
    for (int w : book.coverages().values()) if (w != Playbook.NEUTRAL) n++;
    return n;
  }
}
