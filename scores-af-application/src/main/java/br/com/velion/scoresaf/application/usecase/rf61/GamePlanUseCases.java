package br.com.velion.scoresaf.application.usecase.rf61;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.ScoresException;
import br.com.velion.scoresaf.application.dto.GamePlanDtos;
import br.com.velion.scoresaf.application.service.GamePlanService;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.engine.model.GamePlan;
import br.com.velion.scoresaf.domain.engine.model.Playbook;
import br.com.velion.scoresaf.domain.match.GamePlanPolicy;
import br.com.velion.scoresaf.domain.match.GamePlanSetup;
import br.com.velion.scoresaf.domain.match.PlaybookSetup;
import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF61 — plano de jogo e playbook da franquia do usuário: leitura (criados na primeira leitura com o padrão do estilo do head coach e
 * o playbook neutro) e gravação inteira com versão otimista (409 {@code GAME_PLAN_VERSION_CONFLICT} / {@code PLAYBOOK_VERSION_CONFLICT}
 * com a versão atual nos detalhes, como o depth chart, D-53/D-55) e validação com 422 e a lista {@code errors[]}
 * ({@link GamePlanPolicy}). O que for gravado vale a partir do próximo kickoff; o jogo em andamento muda pelo intervalo (RF29).
 */
@Service
public class GamePlanUseCases {
  public record PlanCommand(Long version, Integer passRatio, String fourthDownAggression, String tempo, String defensiveFocus, String blitzFrequency,
                            String baseDefense) {}
  public record PlaybookCommand(Long version, List<GamePlanPolicy.SituationInput> situations, List<GamePlanPolicy.WeightInput> coverages) {}

  public static final GamePlanDtos.GamePlanOptionsDto OPTIONS = new GamePlanDtos.GamePlanOptionsDto(GamePlan.MIN_PASS_RATIO, GamePlan.MAX_PASS_RATIO,
      names(GamePlan.Aggression.values()), names(GamePlan.Tempo.values()), names(GamePlan.DefensiveFocus.values()), names(GamePlan.Emphasis.values()),
      List.of(DefensePackage.BASE_4_3.name(), DefensePackage.BASE_3_4.name()));

  private final CareerSupport support; private final GamePlanService service; private final AuditService audit;

  public GamePlanUseCases(CareerSupport support, GamePlanService service, AuditService audit) {
    this.support = support; this.service = service; this.audit = audit;
  }

  @Transactional
  public GamePlanDtos.GamePlanDto getPlan(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    Club club = support.userClub(save);
    return planDto(service.planOrCreate(save, club), club, save.isActive());
  }

  @Transactional
  public GamePlanDtos.GamePlanDto putPlan(UUID careerId, UUID userId, PlanCommand c) {
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    GamePlanSetup g = service.planOrCreate(save, club);
    checkVersion("GAME_PLAN_VERSION_CONFLICT", "O plano de jogo foi alterado em outra sessão", g.currentVersion(), c.version());
    GamePlan plan = validPlan(c.passRatio(), c.fourthDownAggression(), c.tempo(), c.defensiveFocus(), c.blitzFrequency(), c.baseDefense());
    g = service.savePlan(g, plan);
    audit.ok(AuditActions.GAME_PLAN_UPDATED, "GamePlanSetup", g.getId(), "versão " + g.currentVersion());
    return planDto(g, club, true);
  }

  @Transactional
  public GamePlanDtos.PlaybookDto getPlaybook(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    Club club = support.userClub(save);
    PlaybookSetup p = service.playbookOrCreate(save, club);
    return playbookDto(p, club, service.read(p));
  }

  @Transactional
  public GamePlanDtos.PlaybookDto putPlaybook(UUID careerId, UUID userId, PlaybookCommand c) {
    CareerSave save = support.writable(careerId, userId);
    Club club = support.userClub(save);
    PlaybookSetup p = service.playbookOrCreate(save, club);
    checkVersion("PLAYBOOK_VERSION_CONFLICT", "O playbook foi alterado em outra sessão", p.currentVersion(), c.version());
    Playbook book = validPlaybook(c.situations(), c.coverages());
    p = service.savePlaybook(p, book);
    audit.ok(AuditActions.PLAYBOOK_UPDATED, "PlaybookSetup", p.getId(), "versão " + p.currentVersion() + ", " + GamePlanService.customWeights(book) + " pesos fora do padrão");
    return playbookDto(p, club, service.read(p));
  }

  /** Plano validado ({@link GamePlanPolicy}); inválido ⇒ 422 GAME_PLAN_INVALID com {@code errors[]}. Também usado no intervalo (RF29). */
  public static GamePlan validPlan(Integer passRatio, String aggression, String tempo, String focus, String blitz, String baseDefense) {
    GamePlanPolicy.PlanResult r = GamePlanPolicy.plan(passRatio, aggression, tempo, focus, blitz, baseDefense);
    if (!r.valid()) {
      GamePlanPolicy.Issue first = r.errors().get(0);
      throw new BusinessRuleException("GAME_PLAN_INVALID", "Plano de jogo inválido: " + first.code() + " em " + first.field(), first.field())
          .withDetail("errors", r.errors());
    }
    return r.plan();
  }

  /** Playbook validado; incompleto ⇒ 422 PLAYBOOK_INCOMPLETE, inválido ⇒ 422 PLAYBOOK_INVALID, ambos com {@code errors[]}. */
  public static Playbook validPlaybook(List<GamePlanPolicy.SituationInput> situations, List<GamePlanPolicy.WeightInput> coverages) {
    GamePlanPolicy.PlaybookResult r = GamePlanPolicy.playbook(situations, coverages);
    if (!r.valid()) {
      GamePlanPolicy.Issue first = r.errors().get(0);
      String code = r.incompleteOnly() ? "PLAYBOOK_INCOMPLETE" : "PLAYBOOK_INVALID";
      throw new BusinessRuleException(code, "Playbook inválido: " + first.code() + " em " + first.field(), first.field()).withDetail("errors", r.errors());
    }
    return r.playbook();
  }

  private static void checkVersion(String code, String message, long current, Long expected) {
    if (expected == null || expected != current) {
      ScoresException e = new ConflictException(code, message, expected, current);
      throw e.withDetail("currentVersion", current).withDetail("expectedVersion", expected);
    }
  }

  public static GamePlanDtos.GamePlanDto planDto(GamePlanSetup g, Club club, boolean editable) {
    return new GamePlanDtos.GamePlanDto(g.getId(), club.getId(), g.currentVersion(), g.getPassRatio(), g.getFourthDownAggression().name(), g.getTempo().name(),
        g.getDefensiveFocus().name(), g.getBlitzFrequency().name(), g.getBaseDefense().name(),
        club.getManager() == null ? null : club.getManager().getCoachStyle().name(), g.getRevision() == 0 ? "COACH_STYLE" : "SAVED", editable,
        g.getUpdatedAt(), OPTIONS);
  }

  public static GamePlanDtos.PlaybookDto playbookDto(PlaybookSetup p, Club club, Playbook book) {
    List<GamePlanDtos.SituationDto> situations = new ArrayList<>();
    for (Playbook.Situation s : Playbook.Situation.values()) {
      List<GamePlanDtos.WeightDto> w = new ArrayList<>();
      for (PlayConcept c : Playbook.RUN_CONCEPTS) w.add(new GamePlanDtos.WeightDto(c.name(), book.weight(s, c)));
      for (PlayConcept c : Playbook.PASS_CONCEPTS) w.add(new GamePlanDtos.WeightDto(c.name(), book.weight(s, c)));
      situations.add(new GamePlanDtos.SituationDto(s.name(), w));
    }
    List<GamePlanDtos.WeightDto> coverages = Arrays.stream(Coverage.values()).map(c -> new GamePlanDtos.WeightDto(c.name(), book.coverage(c))).toList();
    return new GamePlanDtos.PlaybookDto(p.getId(), club.getId(), p.currentVersion(), Playbook.MIN_WEIGHT, Playbook.MAX_WEIGHT, Playbook.NEUTRAL,
        Playbook.RUN_CONCEPTS.stream().map(Enum::name).toList(), Playbook.PASS_CONCEPTS.stream().map(Enum::name).toList(), situations, coverages,
        GamePlanService.customWeights(book), p.getUpdatedAt());
  }

  private static List<String> names(Enum<?>[] values) { return Arrays.stream(values).map(Enum::name).toList(); }
}
