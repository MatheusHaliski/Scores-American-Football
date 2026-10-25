package br.com.velion.scoresaf.application.usecase.rf34;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.ConflictException;
import br.com.velion.scoresaf.application.common.exception.ForbiddenException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.dto.CareerDtos;
import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.LeagueRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ManagerProfileRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WorldRepository;
import br.com.velion.scoresaf.application.service.DepthChartService;
import br.com.velion.scoresaf.application.service.SeasonScheduler;
import br.com.velion.scoresaf.application.service.WorldGraphStore;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.SaveStatus;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.CoachStyle;
import br.com.velion.scoresaf.domain.shared.Difficulty;
import br.com.velion.scoresaf.domain.shared.EmploymentStatus;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF34 / HU-RF34 — nova carreira de head coach. RNF1, como no basquete: o mundo escolhido (o padrão ou um mundo editado do próprio
 * usuário, RF5) é copiado para um mundo SAVE da carreira na mesma transação que cria o save, a temporada com o calendário e o depth
 * chart padrão — falha em qualquer passo não deixa carreira nem mundo parcial. Daí em diante a carreira tem o próprio catálogo:
 * editar o mundo de origem não a altera e jogar não altera o mundo de origem.
 *
 * <p>Passos: valida nome (3–40, único entre as carreiras do usuário), slot (até 5 ACTIVE), mundo (padrão ou do dono, modelo), liga do
 * mundo, franquia da liga, estilo (OFFENSIVE, DEFENSIVE, DEVELOPER, MOTIVATOR), dificuldade, nome e nacionalidade do head coach;
 * copia o mundo; cria o head coach humano, que assume a franquia; grava o save com a semente (a do pedido, para reproduzir, ou uma
 * aleatória); gera a temporada (calendário de 17 jogos + bye) e o depth chart padrão.
 */
@Service
public class CreateCareerUseCase {
  private static final Pattern NATIONALITY = Pattern.compile("[A-Z]{3}");
  private static final SecureRandom RANDOM = new SecureRandom();

  public record Command(String name, UUID worldId, UUID leagueId, UUID clubId, String coachName, String coachNationality, String coachStyle,
                        String difficulty, Integer slot, Long seed) {}

  private final UserAccountRepository users; private final WorldRepository worlds; private final LeagueRepository leagues;
  private final ClubRepository clubs; private final ManagerProfileRepository managers; private final CareerSaveRepository saves;
  private final WorldGraphStore graph; private final SeasonScheduler scheduler; private final DepthChartService depthCharts; private final AuditService audit;

  public CreateCareerUseCase(UserAccountRepository users, WorldRepository worlds, LeagueRepository leagues, ClubRepository clubs,
                             ManagerProfileRepository managers, CareerSaveRepository saves, WorldGraphStore graph, SeasonScheduler scheduler,
                             DepthChartService depthCharts, AuditService audit) {
    this.users = users; this.worlds = worlds; this.leagues = leagues; this.clubs = clubs; this.managers = managers; this.saves = saves;
    this.graph = graph; this.scheduler = scheduler; this.depthCharts = depthCharts; this.audit = audit;
  }

  @Transactional
  public CareerDtos.CareerDto execute(UUID userId, Command c) {
    UserAccount user = users.findById(userId).orElseThrow(() -> new NotFoundException("Conta", userId));
    String name = careerName(c.name());
    List<CareerSave> mine = saves.findByUserIdOrderByCreatedAtAsc(userId);
    if (mine.stream().anyMatch(s -> s.getName().equalsIgnoreCase(name))) {
      throw new ConflictException("CAREER_NAME_TAKEN", "Você já tem uma carreira com o nome " + name, name);
    }
    int slot = slot(mine, c.slot());

    World source = c.worldId() == null
        ? worlds.findFirstByKindAndOwnerUserIsNullOrderByCreatedAtAsc(WorldKind.TEMPLATE).orElseThrow(() -> new NotFoundException("Mundo padrão não encontrado"))
        : worlds.findById(c.worldId()).orElseThrow(() -> new NotFoundException("Mundo", c.worldId()));
    if (!source.isDefaultTemplate() && (source.getOwnerUser() == null || !source.getOwnerUser().getId().equals(userId))) {
      audit.denied(AuditActions.ACCESS_DENIED, "World", source.getId(), "usuário " + userId + " tentou criar carreira em mundo alheio");
      throw new ForbiddenException("Este mundo pertence a outro usuário");
    }
    if (source.getKind() != WorldKind.TEMPLATE) {
      throw new BusinessRuleException("WORLD_NOT_TEMPLATE", "Só é possível iniciar carreira a partir de um mundo modelo");
    }
    League srcLeague = c.leagueId() == null
        ? leagues.findByWorldIdOrderByTierAscNameAsc(source.getId()).stream().findFirst().orElseThrow(() -> new NotFoundException("Liga do mundo", source.getId()))
        : leagues.findById(c.leagueId()).orElseThrow(() -> new NotFoundException("Liga", c.leagueId()));
    if (!srcLeague.getWorld().getId().equals(source.getId())) {
      throw new BusinessRuleException("LEAGUE_NOT_IN_WORLD", "A liga não pertence ao mundo escolhido");
    }
    if (c.clubId() == null) throw new BusinessRuleException("CLUB_REQUIRED", "Escolha a franquia");
    Club srcClub = clubs.findById(c.clubId()).orElseThrow(() -> new NotFoundException("Franquia", c.clubId()));
    if (srcClub.getLeague() == null || !srcClub.getLeague().getId().equals(srcLeague.getId())) {
      throw new BusinessRuleException("CLUB_NOT_IN_LEAGUE", "A franquia não pertence à liga escolhida");
    }
    CoachStyle style = parse(CoachStyle.class, c.coachStyle(), CoachStyle.OFFENSIVE, "INVALID_COACH_STYLE");
    Difficulty difficulty = parse(Difficulty.class, c.difficulty(), Difficulty.NORMAL, "INVALID_DIFFICULTY");
    String coachName = coachName(c.coachName() == null || c.coachName().isBlank() ? user.getDisplayName() : c.coachName());
    String nationality = c.coachNationality() == null || c.coachNationality().isBlank() ? "BRA" : c.coachNationality().trim().toUpperCase(Locale.ROOT);
    if (!NATIONALITY.matcher(nationality).matches()) {
      throw new BusinessRuleException("COACH_NATIONALITY_INVALID", "Nacionalidade inválida: use o código ISO de 3 letras", nationality);
    }

    WorldGraphStore.Cloned cloned = graph.cloneGraph(source, user, worldName(name), WorldKind.SAVE);
    League league = leagues.findById(cloned.map(srcLeague.getId())).orElseThrow();
    Club club = clubs.findById(cloned.map(srcClub.getId())).orElseThrow();

    ManagerProfile coach = new ManagerProfile();
    coach.setWorld(cloned.world()); coach.setName(coachName); coach.setNationality(nationality); coach.setHuman(true); coach.setUserAccount(user);
    coach.setOverall(50); coach.setReputationLeague(50); coach.setCoachStyle(style);
    if (club.getManager() != null) {
      coach.setPreferredFormation(club.getManager().getPreferredFormation());
      coach.setPreferredDefense(club.getManager().getPreferredDefense());
    }
    managers.save(coach);
    club.setManager(coach);
    club.setUserControlled(true);
    clubs.save(club);

    CareerSave save = new CareerSave();
    save.setUser(user); save.setName(name); save.setWorld(cloned.world()); save.setSourceWorldName(source.getName()); save.setManager(coach);
    save.setLeague(league); save.setClub(club); save.setEmploymentStatus(EmploymentStatus.EMPLOYED); save.setCurrentWeek(1);
    save.setDifficulty(difficulty); save.setStatus(SaveStatus.ACTIVE); save.setSlot(slot);
    save.setSeed(c.seed() != null ? c.seed() : RANDOM.nextLong()); save.setLastPlayedAt(Instant.now());
    saves.save(save);

    Season season = scheduler.createFirstSeason(save, league, cloned.world().getBaseYear());
    save.setSeason(season);
    saves.saveAndFlush(save);
    depthCharts.createDefault(save, club, CareerSupport.rules(save).getGameDayActives());
    audit.ok(AuditActions.CAREER_CREATED, "CareerSave", save.getId(), "franquia " + club.getShortName() + ", mundo de origem " + source.getId());
    return CareerAssembler.career(save, CareerDtos.RecordDto.of(0, 0, 0));
  }

  /** Nome da carreira: 3–40 caracteres, sem marcação HTML. */
  public static String careerName(String raw) {
    String n = raw == null ? "" : raw.trim();
    if (n.length() < 3 || n.length() > 40 || n.contains("<") || n.contains(">")) {
      throw new BusinessRuleException("CAREER_NAME_INVALID", "O nome da carreira deve ter de 3 a 40 caracteres", 3, 40);
    }
    return n;
  }

  static String coachName(String raw) {
    String n = raw == null ? "" : raw.trim();
    if (n.length() < 2 || n.length() > 40 || n.contains("<") || n.contains(">")) {
      throw new BusinessRuleException("COACH_NAME_INVALID", "O nome do head coach deve ter de 2 a 40 caracteres", 2, 40);
    }
    return n;
  }

  private static String worldName(String careerName) { return "Carreira: " + careerName; }

  private static int slot(List<CareerSave> mine, Integer requested) {
    Set<Integer> used = new HashSet<>();
    int active = 0;
    for (CareerSave s : mine) {
      if (s.getStatus() == SaveStatus.ACTIVE) { active++; if (s.getSlot() != null) used.add(s.getSlot()); }
    }
    if (active >= CareerSave.MAX_ACTIVE_SLOTS) {
      throw new BusinessRuleException("CAREER_SLOTS_FULL", "Você já tem " + CareerSave.MAX_ACTIVE_SLOTS + " carreiras ativas: arquive ou exclua uma",
          CareerSave.MAX_ACTIVE_SLOTS);
    }
    if (requested != null) {
      if (used.contains(requested)) throw new ConflictException("CAREER_SLOT_TAKEN", "O slot " + requested + " já está ocupado", requested);
      return requested;
    }
    return nextFreeSlot(used);
  }

  public static int nextFreeSlot(Set<Integer> used) {
    for (int i = 1; i <= CareerSave.MAX_ACTIVE_SLOTS; i++) if (!used.contains(i)) return i;
    throw new BusinessRuleException("CAREER_SLOTS_FULL", "Todos os slots de carreira estão ocupados", CareerSave.MAX_ACTIVE_SLOTS);
  }

  static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback, String code) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BusinessRuleException(code, "Valor inválido: " + raw, raw);
    }
  }
}
