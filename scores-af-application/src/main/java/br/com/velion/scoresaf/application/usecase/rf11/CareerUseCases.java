package br.com.velion.scoresaf.application.usecase.rf11;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.FixtureRepository;
import br.com.velion.scoresaf.application.service.CareerDeletion;
import br.com.velion.scoresaf.application.usecase.rf34.CreateCareerUseCase;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.SaveStatus;
import br.com.velion.scoresaf.domain.shared.Difficulty;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF11 / HU-RF11 — central de carreiras salvas: listar (slots ocupados e livres), detalhar, renomear, mudar dificuldade, arquivar e
 * reativar (PATCH, com versão otimista opcional), excluir (DELETE com o nome digitado como confirmação). Diferente do basquete, o
 * DELETE apaga de verdade (save, mundo SAVE e calendário, D-44); arquivar é o PATCH {@code status=ARCHIVED}, que libera o slot.
 */
@Service
public class CareerUseCases {
  public record PatchCommand(String name, String difficulty, String status, Long version) {}

  private final CareerSaveRepository saves; private final FixtureRepository fixtures; private final CareerSupport support;
  private final CareerDeletion deletion; private final AuditService audit;

  public CareerUseCases(CareerSaveRepository saves, FixtureRepository fixtures, CareerSupport support, CareerDeletion deletion, AuditService audit) {
    this.saves = saves; this.fixtures = fixtures; this.support = support; this.deletion = deletion; this.audit = audit;
  }

  @Transactional(readOnly = true)
  public CareerDtos.CareerListDto list(UUID userId) {
    List<CareerSave> all = new ArrayList<>(saves.findByUserIdOrderByCreatedAtAsc(userId));
    all.sort(Comparator.comparingInt((CareerSave s) -> s.getStatus() == SaveStatus.ACTIVE ? 0 : 1)
        .thenComparingInt(s -> s.getSlot() == null ? Integer.MAX_VALUE : s.getSlot()).thenComparing(CareerSave::getCreatedAt));
    Set<Integer> used = new HashSet<>();
    int active = 0;
    for (CareerSave s : all) if (s.isActive()) { active++; if (s.getSlot() != null) used.add(s.getSlot()); }
    List<Integer> free = new ArrayList<>();
    for (int i = 1; i <= CareerSave.MAX_ACTIVE_SLOTS; i++) if (!used.contains(i)) free.add(i);
    return new CareerDtos.CareerListDto(CareerSave.MAX_ACTIVE_SLOTS, active, free, all.stream().map(this::dto).toList());
  }

  @Transactional(readOnly = true)
  public CareerDtos.CareerDto get(UUID careerId, UUID userId) { return dto(support.owned(careerId, userId)); }

  @Transactional
  public CareerDtos.CareerDto patch(UUID careerId, UUID userId, PatchCommand c) {
    CareerSave s = support.owned(careerId, userId);
    long current = s.getVersion() == null ? 0 : s.getVersion();
    if (c.version() != null && c.version() != current) {
      throw new ConflictException("CAREER_VERSION_CONFLICT", "A carreira foi alterada em outra sessão", c.version(), current)
          .withDetail("currentVersion", current).withDetail("expectedVersion", c.version());
    }
    SaveStatus target = c.status() == null || c.status().isBlank() ? null : parseStatus(c.status());
    boolean edits = (c.name() != null && !c.name().isBlank()) || (c.difficulty() != null && !c.difficulty().isBlank());
    if (edits && !s.isActive() && target != SaveStatus.ACTIVE) {
      throw new BusinessRuleException("CAREER_ARCHIVED", "Carreira arquivada: reative-a para fazer alterações");
    }
    if (target == SaveStatus.ACTIVE && !s.isActive()) reactivate(s, userId);
    if (c.name() != null && !c.name().isBlank()) {
      String name = CreateCareerUseCase.careerName(c.name());
      boolean taken = saves.findByUserIdOrderByCreatedAtAsc(userId).stream().anyMatch(o -> !o.getId().equals(s.getId()) && o.getName().equalsIgnoreCase(name));
      if (taken) throw new ConflictException("CAREER_NAME_TAKEN", "Você já tem uma carreira com o nome " + name, name);
      s.setName(name);
    }
    if (c.difficulty() != null && !c.difficulty().isBlank()) {
      try { s.setDifficulty(Difficulty.valueOf(c.difficulty().trim().toUpperCase(Locale.ROOT))); }
      catch (IllegalArgumentException e) { throw new BusinessRuleException("INVALID_DIFFICULTY", "Dificuldade inválida: " + c.difficulty(), c.difficulty()); }
    }
    if (target == SaveStatus.ARCHIVED && s.isActive()) { s.setStatus(SaveStatus.ARCHIVED); s.setSlot(null); }
    saves.saveAndFlush(s);
    audit.ok(AuditActions.CAREER_UPDATED, "CareerSave", s.getId(), "status " + s.getStatus());
    return dto(s);
  }

  private void reactivate(CareerSave s, UUID userId) {
    Set<Integer> used = new HashSet<>();
    int active = 0;
    for (CareerSave o : saves.findByUserIdOrderByCreatedAtAsc(userId)) if (o.isActive()) { active++; if (o.getSlot() != null) used.add(o.getSlot()); }
    if (active >= CareerSave.MAX_ACTIVE_SLOTS) {
      throw new BusinessRuleException("CAREER_SLOTS_FULL", "Você já tem " + CareerSave.MAX_ACTIVE_SLOTS + " carreiras ativas: arquive ou exclua uma",
          CareerSave.MAX_ACTIVE_SLOTS);
    }
    s.setStatus(SaveStatus.ACTIVE);
    s.setSlot(CreateCareerUseCase.nextFreeSlot(used));
  }

  /** Exclusão definitiva; {@code confirm} precisa ser o nome da carreira, exatamente (RF11). */
  @Transactional
  public void delete(UUID careerId, UUID userId, String confirm) {
    CareerSave s = support.owned(careerId, userId);
    if (confirm == null || !confirm.trim().equals(s.getName())) {
      throw new BusinessRuleException("CAREER_CONFIRMATION_REQUIRED", "Digite o nome da carreira para confirmar a exclusão");
    }
    UUID id = s.getId();
    deletion.delete(s);
    audit.ok(AuditActions.CAREER_DELETED, "CareerSave", id);
  }

  private static SaveStatus parseStatus(String raw) {
    try { return SaveStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
    catch (IllegalArgumentException e) { throw new BusinessRuleException("INVALID_CAREER_STATUS", "Status inválido: " + raw, raw); }
  }

  CareerDtos.CareerDto dto(CareerSave s) {
    CareerDtos.RecordDto record = CareerDtos.RecordDto.of(0, 0, 0);
    if (s.getSeason() != null && s.getClub() != null) {
      record = CareerAssembler.tally(CareerAssembler.tallies(fixtures.findBySeasonId(s.getSeason().getId())), s.getClub().getId()).record();
    }
    return CareerAssembler.career(s, record);
  }
}
