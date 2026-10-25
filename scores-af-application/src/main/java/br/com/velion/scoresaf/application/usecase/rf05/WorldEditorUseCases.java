package br.com.velion.scoresaf.application.usecase.rf05;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.common.tx.AfterCommit;
import br.com.velion.scoresaf.application.dto.EditorDtos;
import br.com.velion.scoresaf.application.dto.RulesDto;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ConferenceRepository;
import br.com.velion.scoresaf.application.port.out.persistence.DivisionRepository;
import br.com.velion.scoresaf.application.port.out.persistence.HelmetDesignRepository;
import br.com.velion.scoresaf.application.port.out.persistence.LeagueRepository;
import br.com.velion.scoresaf.application.port.out.persistence.ManagerProfileRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.StadiumRepository;
import br.com.velion.scoresaf.application.port.out.persistence.StadiumSectorRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UniformRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WorldRepository;
import br.com.velion.scoresaf.application.service.WorldGraphStore;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.catalog.Division;
import br.com.velion.scoresaf.domain.catalog.HelmetDesign;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.catalog.Stadium;
import br.com.velion.scoresaf.domain.catalog.StadiumSector;
import br.com.velion.scoresaf.domain.catalog.TicketPolicy;
import br.com.velion.scoresaf.domain.catalog.Uniform;
import br.com.velion.scoresaf.domain.catalog.UniformPattern;
import br.com.velion.scoresaf.domain.catalog.UniformSlot;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.game.Ruleset;
import br.com.velion.scoresaf.domain.shared.ClimateZone;
import br.com.velion.scoresaf.domain.shared.ContractType;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RoofType;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.StadiumSectorType;
import br.com.velion.scoresaf.domain.shared.Surface;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF5 — editor de mundos (rotas do basquete + estrutura de conferências e divisões). Clona o mundo padrão para um modelo do usuário
 * e edita ligas (regras PRO/COLLEGE, estrutura 8–32 franquias), franquias (cores, estádio) e jogadores (posição, altura e peso por
 * posição, subatributos, contrato, situação no elenco dentro dos limites de {@link LeagueRules}). Acesso pela {@link WorldAccess};
 * cada alteração confirmada vai para o log de auditoria depois do commit (RNF7).
 */
@Service
public class WorldEditorUseCases {
  /** Mundos modelo por usuário: cada cópia tem ~2,2 mil jogadores, então há um teto (D-24). */
  public static final int MAX_WORLDS_PER_USER = 5;
  private static final Set<RosterStatus> ROSTER = EnumSet.of(RosterStatus.ACTIVE, RosterStatus.INACTIVE);
  private static final Set<RosterStatus> PRACTICE = EnumSet.of(RosterStatus.PRACTICE_SQUAD);

  public record LeagueCommand(String name, String shortName, String country, Integer tier, String logoUrl, String ruleset, Long salaryCap,
                              Long salaryFloor, Integer prestige, Long tvRightsPerSeason, RulesDto rules) {}

  public record ClubCommand(String name, String shortName, String city, Integer foundedYear, String logoKey, String primaryColor,
                            String secondaryColor, String accentColor, Integer prestige, Integer reputationBoard, Integer reputationFans, Long cash,
                            Long scoutingBudget, Integer scoutingLevel, String stadiumName) {}

  public record StadiumCommand(String name, String roof, String surface, String climateZone, Integer level, Integer atmosphere,
                               Long maintenanceCostPerWeek, String model3dUrl, String endZoneArtKey, Integer memberDiscountPct,
                               Boolean dynamicPricing, Integer seasonTicketPct, List<EditorDtos.SectorDto> sectors) {}

  public record PlayerCommand(String name, String nationality, String college, String birthDate, Integer heightCm, Integer weightKg,
                              String position, String secondaryPosition, String rosterStatus, UUID clubId, Integer jerseyNumber,
                              Integer experienceYears, Integer potential, Boolean legendary, Map<String, Object> attributes,
                              List<Long> baseSalaryByYear, Long signingBonus, Long guaranteed, Integer contractStartSeason, String contractType) {}

  private final WorldRepository worlds; private final UserAccountRepository users; private final LeagueRepository leagues;
  private final ConferenceRepository conferences; private final DivisionRepository divisions; private final ClubRepository clubs;
  private final StadiumRepository stadiums; private final StadiumSectorRepository sectors; private final UniformRepository uniforms;
  private final HelmetDesignRepository helmets; private final PlayerRepository players; private final ManagerProfileRepository managers;
  private final WorldGraphStore graph; private final WorldAccess access; private final AuditService audit;

  public WorldEditorUseCases(WorldRepository worlds, UserAccountRepository users, LeagueRepository leagues, ConferenceRepository conferences,
                             DivisionRepository divisions, ClubRepository clubs, StadiumRepository stadiums, StadiumSectorRepository sectors,
                             UniformRepository uniforms, HelmetDesignRepository helmets, PlayerRepository players, ManagerProfileRepository managers,
                             WorldGraphStore graph, WorldAccess access, AuditService audit) {
    this.worlds = worlds; this.users = users; this.leagues = leagues; this.conferences = conferences; this.divisions = divisions;
    this.clubs = clubs; this.stadiums = stadiums; this.sectors = sectors; this.uniforms = uniforms; this.helmets = helmets; this.players = players;
    this.managers = managers; this.graph = graph; this.access = access; this.audit = audit;
  }

  // ---------------------------------------------------------------- mundos

  @Transactional(readOnly = true)
  public List<EditorDtos.WorldDto> listWorlds(EditorActor actor) {
    List<EditorDtos.WorldDto> out = new ArrayList<>();
    worlds.findFirstByKindAndOwnerUserIsNullOrderByCreatedAtAsc(WorldKind.TEMPLATE).ifPresent(w -> out.add(worldDto(w, actor)));
    for (World w : worlds.findByOwnerUserIdAndKindOrderByCreatedAtAsc(actor.userId(), WorldKind.TEMPLATE)) out.add(worldDto(w, actor));
    return out;
  }

  @Transactional(readOnly = true)
  public EditorDtos.WorldDto getWorld(UUID worldId, EditorActor actor) { return worldDto(access.readable(worldId, actor), actor); }

  /** Cópia editável de um mundo modelo legível (o padrão ou um do usuário). */
  @Transactional
  public EditorDtos.WorldDto cloneWorld(UUID worldId, EditorActor actor, String name) {
    World src = access.readable(worldId, actor);
    if (src.getKind() != WorldKind.TEMPLATE) throw new BusinessRuleException("WORLD_NOT_TEMPLATE", "Só mundos modelo podem ser clonados");
    if (worlds.findByOwnerUserIdAndKindOrderByCreatedAtAsc(actor.userId(), WorldKind.TEMPLATE).size() >= MAX_WORLDS_PER_USER) {
      throw new BusinessRuleException("WORLD_LIMIT", "Limite de mundos atingido", MAX_WORLDS_PER_USER);
    }
    UserAccount owner = users.findById(actor.userId()).orElseThrow(() -> new NotFoundException("Conta", actor.userId()));
    String n = name == null || name.isBlank() ? src.getName() + " (cópia)" : CatalogInput.name(name, 120);
    World w = graph.cloneWorld(src, owner, n);
    auditOk(AuditActions.WORLD_CLONED, "World", w.getId(), "origem " + src.getId());
    return worldDto(w, actor);
  }

  /** Mundo novo = cópia do mundo padrão. */
  @Transactional
  public EditorDtos.WorldDto createWorld(EditorActor actor, String name) {
    World def = worlds.findFirstByKindAndOwnerUserIsNullOrderByCreatedAtAsc(WorldKind.TEMPLATE)
        .orElseThrow(() -> new NotFoundException("Mundo padrão não encontrado"));
    return cloneWorld(def.getId(), actor, name);
  }

  @Transactional
  public EditorDtos.WorldDto updateWorld(UUID worldId, EditorActor actor, String name, String description) {
    World w = access.editable(worldId, actor);
    if (name != null) w.setName(CatalogInput.name(name, 120));
    if (description != null) w.setDescription(description.length() > 500 ? description.substring(0, 500) : description);
    worlds.save(w);
    auditOk(AuditActions.WORLD_UPDATED, "World", w.getId(), null);
    return worldDto(w, actor);
  }

  @Transactional
  public void deleteWorld(UUID worldId, EditorActor actor) {
    World w = access.editable(worldId, actor);
    graph.delete(w);
    auditOk(AuditActions.WORLD_DELETED, "World", worldId, null);
  }

  // ---------------------------------------------------------------- ligas

  @Transactional(readOnly = true)
  public List<EditorDtos.LeagueDto> listLeagues(UUID worldId, EditorActor actor) {
    access.readable(worldId, actor);
    return leagues.findByWorldIdOrderByTierAscNameAsc(worldId).stream().map(this::leagueDto).toList();
  }

  @Transactional
  public EditorDtos.LeagueDto createLeague(UUID worldId, EditorActor actor, LeagueCommand c) {
    World w = access.editable(worldId, actor);
    if (c == null) throw new InvalidInputException("INVALID_NAME", "Informe o nome da liga");
    League l = new League();
    l.setWorld(w);
    l.setName(CatalogInput.name(c.name(), 120));
    String fallback = c.name().replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT);
    l.setShortName(CatalogInput.shortName(c.shortName() == null ? fallback.substring(0, Math.min(8, fallback.length())) : c.shortName(), 8));
    l.setRuleset(c.ruleset() == null ? Ruleset.PRO : CatalogInput.enumValue(Ruleset.class, c.ruleset(), "INVALID_RULESET"));
    l.setRules(LeagueRules.defaults(l.getRuleset()));
    l.setSalaryCap(255_000_000_00L); l.setSalaryFloor(227_000_000_00L);
    applyLeague(l, c, true);
    leagues.save(l);
    auditOk(AuditActions.CATALOG_CREATED, "League", l.getId(), null);
    return leagueDto(l);
  }

  @Transactional
  public EditorDtos.LeagueDto updateLeague(UUID leagueId, EditorActor actor, LeagueCommand c) {
    League l = league(leagueId);
    access.editable(l.getWorld().getId(), actor);
    if (c != null) {
      if (c.name() != null) l.setName(CatalogInput.name(c.name(), 120));
      if (c.shortName() != null) l.setShortName(CatalogInput.shortName(c.shortName(), 8));
      if (c.ruleset() != null) {
        Ruleset r = CatalogInput.enumValue(Ruleset.class, c.ruleset(), "INVALID_RULESET");
        if (r != l.getRuleset()) { l.setRuleset(r); l.setRules(LeagueRules.defaults(r)); }
      }
      applyLeague(l, c, false);
    }
    leagues.save(l);
    auditOk(AuditActions.CATALOG_UPDATED, "League", l.getId(), null);
    return leagueDto(l);
  }

  @Transactional
  public void deleteLeague(UUID leagueId, EditorActor actor) {
    League l = league(leagueId);
    access.editable(l.getWorld().getId(), actor);
    if (clubs.countByLeagueId(leagueId) > 0) throw new BusinessRuleException("LEAGUE_HAS_CLUBS", "Remova as franquias antes de excluir a liga");
    for (Division d : divisions.findByConferenceLeagueIdOrderByConferenceSortOrderAscSortOrderAsc(leagueId)) divisions.delete(d);
    for (Conference cf : conferences.findByLeagueIdOrderBySortOrderAsc(leagueId)) conferences.delete(cf);
    leagues.delete(l);
    auditOk(AuditActions.CATALOG_DELETED, "League", leagueId, null);
  }

  private void applyLeague(League l, LeagueCommand c, boolean creating) {
    if (c.country() != null) {
      String country = c.country().trim().toUpperCase(java.util.Locale.ROOT);
      if (!country.matches("^[A-Z]{3}$")) throw new InvalidInputException("INVALID_VALUE", "País inválido", "country");
      l.setCountry(country);
    }
    if (c.tier() != null) l.setTier(CatalogInput.range("tier", c.tier(), 1, 5));
    if (c.logoUrl() != null) l.setLogoUrl(c.logoUrl().isBlank() ? null : c.logoUrl().trim());
    if (c.prestige() != null) l.setPrestige(CatalogInput.range("prestige", c.prestige(), 0, 100));
    if (c.tvRightsPerSeason() != null) l.setTvRightsPerSeason(CatalogInput.nonNegative("tvRightsPerSeason", c.tvRightsPerSeason()));
    if (c.salaryCap() != null) l.setSalaryCap(CatalogInput.nonNegative("salaryCap", c.salaryCap()));
    if (c.salaryFloor() != null) l.setSalaryFloor(CatalogInput.nonNegative("salaryFloor", c.salaryFloor()));
    if (l.getSalaryFloor() > l.getSalaryCap()) throw new InvalidInputException("INVALID_VALUE", "Piso acima do teto", "salaryFloor");
    // regras resultantes: as atuais (já trocadas para o padrão do novo ruleset, se mudou) com o patch do comando por cima
    LeagueRules next = l.getRules();
    if (c.rules() != null) {
      try { next = c.rules().applyTo(l.getRules()); }
      catch (IllegalArgumentException e) { throw new InvalidInputException("INVALID_RULES", "Regras inválidas", "overtimeFormat"); }
      List<String> bad = next.violations();
      if (!bad.isEmpty()) throw new InvalidInputException("INVALID_RULES", "Regras inválidas: " + bad, String.join(", ", bad));
    }
    if (!creating) {
      // sempre contra os elencos existentes: a troca de ruleset muda os limites tanto quanto um patch de regras
      for (Club club : clubs.findByLeagueIdOrderByNameAsc(l.getId())) {
        if (players.countByClubIdAndRosterStatusIn(club.getId(), ROSTER) > next.getRosterMax()
            || players.countByClubIdAndRosterStatusIn(club.getId(), PRACTICE) > next.getPracticeSquadMax()) {
          throw new BusinessRuleException("ROSTER_ABOVE_LIMIT", "Há franquias acima do novo limite de elenco", club.getName());
        }
      }
    }
    l.setRules(next);
  }

  // ---------------------------------------------------------------- estrutura (conferências e divisões)

  @Transactional(readOnly = true)
  public EditorDtos.StructureDto getStructure(UUID worldId, UUID leagueId, EditorActor actor) {
    League l = leagueOfWorld(worldId, leagueId);
    access.readable(worldId, actor);
    return structureDto(l);
  }

  /**
   * Substitui conferências e divisões da liga e distribui as franquias (PUT .../structure). Regras: 1 ou 2 conferências; o mesmo
   * número de divisões (1–4) em cada conferência; cada franquia da liga em exatamente uma divisão; de 8 a 32 franquias; divisões
   * equilibradas (tamanhos diferem em no máximo 1, nenhuma vazia).
   */
  @Transactional
  public EditorDtos.StructureDto updateStructure(UUID worldId, UUID leagueId, EditorActor actor, EditorDtos.StructureDto cmd) {
    League l = leagueOfWorld(worldId, leagueId);
    access.editable(worldId, actor);
    List<EditorDtos.ConferenceDto> confs = cmd == null || cmd.conferences() == null ? List.of() : cmd.conferences();
    if (confs.isEmpty() || confs.size() > League.MAX_CONFERENCES) {
      throw new BusinessRuleException("STRUCTURE_CONFERENCES", "A liga precisa de 1 ou 2 conferências", League.MAX_CONFERENCES);
    }
    int divisionsEach = -1;
    for (EditorDtos.ConferenceDto cf : confs) {
      int n = cf.divisions() == null ? 0 : cf.divisions().size();
      if (n < 1 || n > League.MAX_DIVISIONS_PER_CONFERENCE || (divisionsEach >= 0 && n != divisionsEach)) {
        throw new BusinessRuleException("STRUCTURE_DIVISIONS", "Divisões inconsistentes entre as conferências");
      }
      divisionsEach = n;
    }
    // um id de conferência ou de divisão em duas entradas faria as duas mutarem a mesma entidade (estrutura gravada menor que a
    // pedida, franquias com conferência que não bate com a da divisão): recusa antes de mexer em qualquer entidade
    Set<UUID> confIds = new HashSet<>(), divIds = new HashSet<>();
    for (EditorDtos.ConferenceDto cf : confs) {
      if (cf.id() != null && !confIds.add(cf.id())) throw new BusinessRuleException("STRUCTURE_DUPLICATE_ID", "Conferência repetida na estrutura");
      for (EditorDtos.DivisionDto d : cf.divisions()) {
        if (d.id() != null && !divIds.add(d.id())) throw new BusinessRuleException("STRUCTURE_DUPLICATE_ID", "Divisão repetida na estrutura");
      }
    }
    List<Club> leagueClubs = clubs.findByLeagueIdOrderByNameAsc(leagueId);
    Map<UUID, Club> byId = new HashMap<>();
    for (Club c : leagueClubs) byId.put(c.getId(), c);
    Set<UUID> seen = new HashSet<>();
    int min = Integer.MAX_VALUE, max = 0;
    for (EditorDtos.ConferenceDto cf : confs) {
      CatalogInput.name(cf.name(), 80);
      for (EditorDtos.DivisionDto d : cf.divisions()) {
        CatalogInput.name(d.name(), 80);
        List<UUID> ids = d.clubIds() == null ? List.of() : d.clubIds();
        for (UUID id : ids) {
          if (!byId.containsKey(id) || !seen.add(id)) throw new BusinessRuleException("STRUCTURE_CLUBS", "Cada franquia da liga entra em exatamente uma divisão");
        }
        min = Math.min(min, ids.size()); max = Math.max(max, ids.size());
      }
    }
    if (seen.size() != leagueClubs.size()) throw new BusinessRuleException("STRUCTURE_CLUBS", "Cada franquia da liga entra em exatamente uma divisão");
    if (leagueClubs.size() < League.MIN_TEAMS || leagueClubs.size() > League.MAX_TEAMS) {
      throw new BusinessRuleException("LEAGUE_TEAM_COUNT", "A liga precisa ter de 8 a 32 franquias", League.MIN_TEAMS, League.MAX_TEAMS, leagueClubs.size());
    }
    if (min < 1 || max - min > 1) throw new BusinessRuleException("STRUCTURE_DIVISIONS", "Divisões desequilibradas");

    // aplica: reaproveita conferências/divisões pelo id, cria as novas, move as franquias e só então apaga as que sobraram
    Map<UUID, Conference> oldConfs = new HashMap<>();
    for (Conference cf : conferences.findByLeagueIdOrderBySortOrderAsc(leagueId)) oldConfs.put(cf.getId(), cf);
    Map<UUID, Division> oldDivs = new HashMap<>();
    for (Division d : divisions.findByConferenceLeagueIdOrderByConferenceSortOrderAscSortOrderAsc(leagueId)) oldDivs.put(d.getId(), d);
    Set<UUID> keptConfs = new HashSet<>(), keptDivs = new HashSet<>();
    for (int ci = 0; ci < confs.size(); ci++) {
      EditorDtos.ConferenceDto cd = confs.get(ci);
      Conference cf = cd.id() != null && oldConfs.containsKey(cd.id()) ? oldConfs.get(cd.id()) : new Conference();
      cf.setLeague(l); cf.setName(cd.name().trim()); cf.setSortOrder(ci);
      cf.setShortName(cd.shortName() == null || cd.shortName().isBlank() ? abbreviate(cd.name()) : CatalogInput.shortName(cd.shortName(), 8));
      conferences.save(cf);
      keptConfs.add(cf.getId());
      for (int di = 0; di < cd.divisions().size(); di++) {
        EditorDtos.DivisionDto dd = cd.divisions().get(di);
        Division d = dd.id() != null && oldDivs.containsKey(dd.id()) ? oldDivs.get(dd.id()) : new Division();
        d.setConference(cf); d.setName(dd.name().trim()); d.setSortOrder(di);
        divisions.save(d);
        keptDivs.add(d.getId());
        for (UUID id : dd.clubIds()) { Club c = byId.get(id); c.setConference(cf); c.setDivision(d); clubs.save(c); }
      }
    }
    for (Division d : oldDivs.values()) if (!keptDivs.contains(d.getId())) divisions.delete(d);
    for (Conference cf : oldConfs.values()) if (!keptConfs.contains(cf.getId())) conferences.delete(cf);
    l.setConferenceCount(confs.size());
    l.setDivisionsPerConference(divisionsEach);
    l.setTeamCount(leagueClubs.size());
    leagues.save(l);
    auditOk(AuditActions.LEAGUE_STRUCTURE_UPDATED, "League", leagueId, confs.size() + " conferências × " + divisionsEach + " divisões, " + leagueClubs.size() + " franquias");
    return structureDto(l);
  }

  private static String abbreviate(String name) {
    String letters = name.replaceAll("[^A-Za-z]", "").toUpperCase(java.util.Locale.ROOT);
    return letters.length() >= 3 ? letters.substring(0, 3) : (letters + "XXX").substring(0, 3);
  }

  // ---------------------------------------------------------------- franquias

  @Transactional(readOnly = true)
  public List<EditorDtos.ClubDto> listClubs(UUID leagueId, EditorActor actor) {
    League l = league(leagueId);
    access.readable(l.getWorld().getId(), actor);
    return clubs.findByLeagueIdOrderByNameAsc(leagueId).stream().map(this::clubDto).toList();
  }

  @Transactional(readOnly = true)
  public EditorDtos.ClubDto getClub(UUID clubId, EditorActor actor) {
    Club c = club(clubId);
    access.readable(c.getWorld().getId(), actor);
    return clubDto(c);
  }

  /**
   * Cria a franquia com estádio (5 setores, 65 mil lugares), uniformes, capacete e head coach IA. Liga cheia (32) recusa; se a liga
   * já tem estrutura, a franquia entra na divisão com menos franquias (a estrutura continua equilibrada).
   */
  @Transactional
  public EditorDtos.ClubDto createClub(UUID leagueId, EditorActor actor, ClubCommand c) {
    League l = league(leagueId);
    World w = access.editable(l.getWorld().getId(), actor);
    long count = clubs.countByLeagueId(leagueId);
    if (count >= League.MAX_TEAMS) throw new BusinessRuleException("LEAGUE_FULL", "A liga já tem o máximo de franquias", League.MAX_TEAMS);
    if (c == null) throw new InvalidInputException("INVALID_NAME", "Informe nome e sigla");
    Club club = new Club();
    club.setWorld(w); club.setLeague(l);
    club.setName(CatalogInput.name(c.name(), 120));
    club.setShortName(CatalogInput.shortName(c.shortName(), 4));
    club.setCity(c.city() == null || c.city().isBlank() ? club.getName() : CatalogInput.name(c.city(), 80));
    club.setFoundedYear(w.getBaseYear());
    applyClub(club, c);
    List<Division> divs = divisions.findByConferenceLeagueIdOrderByConferenceSortOrderAscSortOrderAsc(leagueId);
    if (!divs.isEmpty()) {
      Division smallest = divs.stream().min(Comparator.comparingLong(d -> clubs.countByDivisionId(d.getId()))).orElseThrow();
      club.setDivision(smallest); club.setConference(smallest.getConference());
    }
    Stadium s = new Stadium();
    s.setWorld(w);
    s.setName(c.stadiumName() == null || c.stadiumName().isBlank() ? "Arena " + club.getCity() : CatalogInput.name(c.stadiumName(), 120));
    s.setTicketPolicy(new TicketPolicy());
    List<StadiumSector> secs = List.of(sector(s, StadiumSectorType.LOWER_BOWL, 24_700, 150_00L), sector(s, StadiumSectorType.UPPER_BOWL, 26_000, 80_00L),
        sector(s, StadiumSectorType.SIDELINE_CLUB, 6_500, 420_00L), sector(s, StadiumSectorType.END_ZONE, 5_850, 95_00L),
        sector(s, StadiumSectorType.LUXURY_SUITES, 1_950, 2_500_00L));
    s.recalculateCapacity(secs);
    s.setValue(s.getCapacity() * 9_000_00L); s.setMaintenanceCostPerWeek(s.getCapacity() * 30_00L);
    stadiums.save(s); sectors.saveAll(secs);
    club.setStadium(s);
    ManagerProfile m = new ManagerProfile();
    m.setWorld(w); m.setName("Head coach " + club.getShortName()); m.setNationality("BRA"); m.setHuman(false);
    managers.save(m);
    club.setManager(m);
    clubs.save(club);
    uniforms.saveAll(defaultUniforms(club));
    HelmetDesign h = new HelmetDesign();
    h.setClub(club); h.setShellColor(club.getPrimaryColor()); h.setStripeColor(club.getSecondaryColor());
    helmets.save(h);
    l.setTeamCount((int) count + 1);
    leagues.save(l);
    auditOk(AuditActions.CATALOG_CREATED, "Club", club.getId(), null);
    return clubDto(club);
  }

  @Transactional
  public EditorDtos.ClubDto updateClub(UUID clubId, EditorActor actor, ClubCommand c) {
    Club club = club(clubId);
    access.editable(club.getWorld().getId(), actor);
    if (c != null) {
      if (c.name() != null) club.setName(CatalogInput.name(c.name(), 120));
      if (c.shortName() != null) club.setShortName(CatalogInput.shortName(c.shortName(), 4));
      if (c.city() != null) club.setCity(CatalogInput.name(c.city(), 80));
      applyClub(club, c);
      if (c.stadiumName() != null && club.getStadium() != null) club.getStadium().setName(CatalogInput.name(c.stadiumName(), 120));
    }
    clubs.save(club);
    auditOk(AuditActions.CATALOG_UPDATED, "Club", club.getId(), null);
    return clubDto(club);
  }

  /** Exclui a franquia: os jogadores viram agentes livres; estádio, uniformes e capacete saem junto; o head coach fica no mundo. */
  @Transactional
  public void deleteClub(UUID clubId, EditorActor actor) {
    Club club = club(clubId);
    access.editable(club.getWorld().getId(), actor);
    for (Player p : players.findByClubIdOrderByOverallDesc(clubId)) { p.setClub(null); p.setRosterStatus(RosterStatus.FREE_AGENT); players.save(p); }
    for (Uniform u : uniforms.findByClubId(clubId)) uniforms.delete(u);
    helmets.findByClubId(clubId).ifPresent(helmets::delete);
    Stadium s = club.getStadium();
    League l = club.getLeague();
    clubs.delete(club);
    if (s != null) { for (StadiumSector sec : sectors.findByStadiumId(s.getId())) sectors.delete(sec); stadiums.delete(s); }
    if (l != null) { l.setTeamCount((int) clubs.countByLeagueId(l.getId())); leagues.save(l); }
    auditOk(AuditActions.CATALOG_DELETED, "Club", clubId, null);
  }

  private static void applyClub(Club club, ClubCommand c) {
    if (c.foundedYear() != null) club.setFoundedYear(CatalogInput.range("foundedYear", c.foundedYear(), 1850, 2100));
    if (c.logoKey() != null) club.setLogoKey(c.logoKey().isBlank() ? null : c.logoKey().trim());
    if (c.primaryColor() != null) club.setPrimaryColor(CatalogInput.color(c.primaryColor()));
    if (c.secondaryColor() != null) club.setSecondaryColor(CatalogInput.color(c.secondaryColor()));
    if (c.accentColor() != null) club.setAccentColor(CatalogInput.color(c.accentColor()));
    if (c.prestige() != null) club.setPrestige(CatalogInput.range("prestige", c.prestige(), 0, 100));
    if (c.reputationBoard() != null) club.setReputationBoard(CatalogInput.range("reputationBoard", c.reputationBoard(), 0, 100));
    if (c.reputationFans() != null) club.setReputationFans(CatalogInput.range("reputationFans", c.reputationFans(), 0, 100));
    if (c.cash() != null) club.setCash(c.cash());
    if (c.scoutingBudget() != null) club.setScoutingBudget(CatalogInput.nonNegative("scoutingBudget", c.scoutingBudget()));
    if (c.scoutingLevel() != null) club.setScoutingLevel(CatalogInput.range("scoutingLevel", c.scoutingLevel(), 1, 5));
  }

  private static List<Uniform> defaultUniforms(Club club) {
    List<Uniform> out = new ArrayList<>();
    for (UniformSlot slot : UniformSlot.values()) {
      Uniform u = new Uniform();
      u.setClub(club); u.setSlot(slot);
      u.setJerseyColor(slot == UniformSlot.HOME ? club.getPrimaryColor() : slot == UniformSlot.AWAY ? "#FFFFFF" : club.getSecondaryColor());
      u.setPantsColor(slot == UniformSlot.HOME ? club.getSecondaryColor() : club.getPrimaryColor());
      u.setNumberColor(slot == UniformSlot.AWAY ? club.getPrimaryColor() : "#FFFFFF");
      u.setNumberOutlineColor(club.getAccentColor());
      u.setPattern(slot == UniformSlot.AWAY ? UniformPattern.SLEEVE_STRIPES : UniformPattern.SOLID);
      out.add(u);
    }
    return out;
  }

  private static StadiumSector sector(Stadium s, StadiumSectorType t, int seats, long price) {
    StadiumSector x = new StadiumSector();
    x.setStadium(s); x.setType(t); x.setSeats(seats); x.setTicketPrice(price); x.setCondition(100); x.setUpgradeCost(seats * 2_000_00L);
    return x;
  }

  // ---------------------------------------------------------------- estádio

  @Transactional(readOnly = true)
  public EditorDtos.StadiumDto getStadium(UUID clubId, EditorActor actor) {
    Club c = club(clubId);
    access.readable(c.getWorld().getId(), actor);
    if (c.getStadium() == null) throw new NotFoundException("Estádio da franquia", clubId);
    return stadiumDto(c.getStadium());
  }

  /** Edita o estádio; no fim a capacidade (soma dos setores) precisa ficar entre 60 e 80 mil, senão nada é gravado. */
  @Transactional
  public EditorDtos.StadiumDto updateStadium(UUID clubId, EditorActor actor, StadiumCommand cmd) {
    Club c = club(clubId);
    access.editable(c.getWorld().getId(), actor);
    Stadium s = c.getStadium();
    if (s == null) throw new NotFoundException("Estádio da franquia", clubId);
    if (cmd != null) {
      if (cmd.name() != null) s.setName(CatalogInput.name(cmd.name(), 120));
      if (cmd.roof() != null) s.setRoof(CatalogInput.enumValue(RoofType.class, cmd.roof(), "INVALID_VALUE"));
      if (cmd.surface() != null) s.setSurface(CatalogInput.enumValue(Surface.class, cmd.surface(), "INVALID_VALUE"));
      if (cmd.climateZone() != null) s.setClimateZone(CatalogInput.enumValue(ClimateZone.class, cmd.climateZone(), "INVALID_VALUE"));
      if (cmd.level() != null) s.setLevel(CatalogInput.range("level", cmd.level(), 1, 5));
      if (cmd.atmosphere() != null) s.setAtmosphere(CatalogInput.range("atmosphere", cmd.atmosphere(), 0, 100));
      if (cmd.maintenanceCostPerWeek() != null) s.setMaintenanceCostPerWeek(CatalogInput.nonNegative("maintenanceCostPerWeek", cmd.maintenanceCostPerWeek()));
      if (cmd.model3dUrl() != null) s.setModel3dUrl(cmd.model3dUrl().isBlank() ? null : cmd.model3dUrl().trim());
      if (cmd.endZoneArtKey() != null) s.setEndZoneArtKey(cmd.endZoneArtKey().isBlank() ? null : cmd.endZoneArtKey().trim());
      TicketPolicy tp = s.getTicketPolicy() == null ? new TicketPolicy() : s.getTicketPolicy();
      if (cmd.memberDiscountPct() != null) tp.setMemberDiscountPct(CatalogInput.range("memberDiscountPct", cmd.memberDiscountPct(), 0, 50));
      if (cmd.dynamicPricing() != null) tp.setDynamicPricing(cmd.dynamicPricing());
      if (cmd.seasonTicketPct() != null) tp.setSeasonTicketPct(CatalogInput.range("seasonTicketPct", cmd.seasonTicketPct(), 0, 90));
      s.setTicketPolicy(tp);
      List<StadiumSector> existing = new ArrayList<>(sectors.findByStadiumId(s.getId()));
      if (cmd.sectors() != null) {
        for (EditorDtos.SectorDto d : cmd.sectors()) {
          StadiumSectorType type = CatalogInput.enumValue(StadiumSectorType.class, d.type(), "INVALID_SECTOR");
          StadiumSector sec = existing.stream().filter(x -> x.getType() == type).findFirst().orElseGet(() -> {
            StadiumSector n = new StadiumSector(); n.setStadium(s); n.setType(type); existing.add(n); return n;
          });
          if (d.seats() != null) sec.setSeats(CatalogInput.range("seats", d.seats(), 0, Stadium.MAX_CAPACITY));
          if (d.ticketPrice() != null) sec.setTicketPrice(CatalogInput.nonNegative("ticketPrice", d.ticketPrice()));
          if (d.condition() != null) sec.setCondition(CatalogInput.range("condition", d.condition(), 0, 100));
          if (d.upgradeCost() != null) sec.setUpgradeCost(CatalogInput.nonNegative("upgradeCost", d.upgradeCost()));
        }
      }
      s.recalculateCapacity(existing);
      if (!Stadium.acceptsCapacity(s.getCapacity())) {
        throw new InvalidInputException("INVALID_STADIUM_CAPACITY", "Capacidade fora da faixa", Stadium.MIN_CAPACITY, Stadium.MAX_CAPACITY);
      }
      sectors.saveAll(existing);
    }
    stadiums.save(s);
    auditOk(AuditActions.CATALOG_UPDATED, "Stadium", s.getId(), null);
    return stadiumDto(s);
  }

  // ---------------------------------------------------------------- jogadores

  @Transactional(readOnly = true)
  public List<EditorDtos.PlayerSummaryDto> listPlayers(UUID clubId, EditorActor actor) {
    Club c = club(clubId);
    access.readable(c.getWorld().getId(), actor);
    return players.findByClubIdOrderByOverallDesc(clubId).stream().map(EditorAssembler::summary).toList();
  }

  @Transactional(readOnly = true)
  public EditorDtos.PlayerEditorDto getPlayer(UUID playerId, EditorActor actor) {
    Player p = player(playerId);
    access.readable(p.getWorld().getId(), actor);
    return EditorAssembler.player(p, rulesOf(p.getClub()));
  }

  @Transactional
  public EditorDtos.PlayerEditorDto createPlayer(UUID clubId, EditorActor actor, PlayerCommand c) {
    Club club = club(clubId);
    World w = access.editable(club.getWorld().getId(), actor);
    if (c == null) throw new InvalidInputException("INVALID_NAME", "Informe o nome do jogador");
    Position pos = c.position() == null ? null : Position.parse(c.position());
    if (pos == null) throw new InvalidInputException("INVALID_POSITION", "Posição inválida", String.valueOf(c.position()));
    Player p = new Player();
    p.setWorld(w); p.setClub(club); p.setPosition(pos); p.setNationality("BRA");
    p.setBirthDate(LocalDate.of(w.getBaseYear() - 24, 1, 1));
    p.setHeightCm((pos.minHeightCm() + pos.maxHeightCm()) / 2); p.setWeightKg((pos.minWeightKg() + pos.maxWeightKg()) / 2);
    p.setAttributes(PlayerAttributes.uniform(55)); p.setRosterStatus(RosterStatus.ACTIVE); p.setJerseyNumber(-1);
    p.setContract(PlayerContract.builder().startSeason(w.getBaseYear()).baseSalaryByYear(new ArrayList<>(List.of(900_000_00L))).type(ContractType.MINIMUM).build());
    applyPlayer(p, c, w, true);
    players.save(p);
    auditOk(AuditActions.CATALOG_CREATED, "Player", p.getId(), null);
    return EditorAssembler.player(p, rulesOf(p.getClub()));
  }

  @Transactional
  public EditorDtos.PlayerEditorDto updatePlayer(UUID playerId, EditorActor actor, PlayerCommand c) {
    Player p = player(playerId);
    World w = access.editable(p.getWorld().getId(), actor);
    if (c != null) applyPlayer(p, c, w, false);
    players.save(p);
    auditOk(AuditActions.CATALOG_UPDATED, "Player", p.getId(), null);
    return EditorAssembler.player(p, rulesOf(p.getClub()));
  }

  @Transactional
  public void deletePlayer(UUID playerId, EditorActor actor) {
    Player p = player(playerId);
    access.editable(p.getWorld().getId(), actor);
    players.delete(p);
    auditOk(AuditActions.CATALOG_DELETED, "Player", playerId, null);
  }

  /** Aplica o comando e confere posição, altura/peso da posição, subatributos, contrato, número e limites de elenco. */
  private void applyPlayer(Player p, PlayerCommand c, World w, boolean creating) {
    if (creating || c.name() != null) p.setName(CatalogInput.name(c.name(), 120));
    if (c.nationality() != null) {
      String nat = c.nationality().trim().toUpperCase(java.util.Locale.ROOT);
      if (!nat.matches("^[A-Z]{3}$")) throw new InvalidInputException("INVALID_VALUE", "Nacionalidade inválida", "nationality");
      p.setNationality(nat);
    }
    if (c.college() != null) p.setCollege(c.college().isBlank() ? null : c.college().trim());
    if (c.position() != null) {
      Position pos = Position.parse(c.position());
      if (pos == null) throw new InvalidInputException("INVALID_POSITION", "Posição inválida", c.position());
      p.setPosition(pos);
    }
    if (c.secondaryPosition() != null) {
      if (c.secondaryPosition().isBlank()) p.setSecondaryPosition(null);
      else {
        Position sec = Position.parse(c.secondaryPosition());
        if (sec == null) throw new InvalidInputException("INVALID_POSITION", "Posição inválida", c.secondaryPosition());
        p.setSecondaryPosition(sec == p.getPosition() ? null : sec);
      }
    }
    if (c.heightCm() != null) p.setHeightCm(c.heightCm());
    if (c.weightKg() != null) p.setWeightKg(c.weightKg());
    Position pos = p.getPosition();
    if (!pos.acceptsHeight(p.getHeightCm())) {
      throw new InvalidInputException("INVALID_HEIGHT", "Altura fora da faixa da posição", pos.name(), pos.minHeightCm(), pos.maxHeightCm());
    }
    if (!pos.acceptsWeight(p.getWeightKg())) {
      throw new InvalidInputException("INVALID_WEIGHT", "Peso fora da faixa da posição", pos.name(), pos.minWeightKg(), pos.maxWeightKg());
    }
    LocalDate reference = LocalDate.of(w.getBaseYear(), 9, 1);
    if (c.birthDate() != null) {
      LocalDate birth;
      try { birth = LocalDate.parse(c.birthDate().trim()); }
      catch (DateTimeParseException e) { throw new InvalidInputException("INVALID_BIRTH_DATE", "Data de nascimento inválida"); }
      int age = Period.between(birth, reference).getYears();
      if (age < 18 || age > 45) throw new InvalidInputException("INVALID_BIRTH_DATE", "Data de nascimento inválida");
      p.setBirthDate(birth);
    }
    if (c.experienceYears() != null) p.setExperienceYears(CatalogInput.range("experienceYears", c.experienceYears(), 0, 25));
    if (c.legendary() != null) p.setLegendary(c.legendary());
    if (c.attributes() != null) p.setAttributes(attributes(p.getAttributes(), c.attributes()));
    applyContract(p, c, w);

    // clube e situação no elenco
    Club target = p.getClub();
    if (c.clubId() != null) {
      target = club(c.clubId());
      if (!target.getWorld().getId().equals(w.getId())) throw new NotFoundException("Franquia", c.clubId());
    }
    RosterStatus status = p.getRosterStatus();
    if (c.rosterStatus() != null) status = CatalogInput.enumValue(RosterStatus.class, c.rosterStatus(), "INVALID_ROSTER_STATUS");
    if (!status.requiresClub()) {
      if (creating) throw new InvalidInputException("INVALID_ROSTER_STATUS", "Situação inválida para jogador de franquia", status.name());
      target = null;
    } else if (target == null) {
      throw new BusinessRuleException("PLAYER_WITHOUT_CLUB", "Escolha a franquia do jogador");
    }
    if (target != null) checkRosterLimits(p, target, status, creating);
    p.setClub(target);
    p.setRosterStatus(status);

    if (c.jerseyNumber() != null) p.setJerseyNumber(c.jerseyNumber());
    if (target != null && p.getJerseyNumber() < 0) p.setJerseyNumber(firstFreeNumber(target, p));
    if (p.getJerseyNumber() < 0 || p.getJerseyNumber() > 99) throw new InvalidInputException("INVALID_JERSEY_NUMBER", "Número inválido", p.getJerseyNumber());
    if (target != null && players.existsByClubIdAndJerseyNumberAndIdNot(target.getId(), p.getJerseyNumber(), p.getId())) {
      throw new BusinessRuleException("JERSEY_TAKEN", "Número já usado na franquia", p.getJerseyNumber());
    }
    p.recalculate(reference);
    if (c.potential() != null) p.setPotential(Math.max(p.getOverall(), CatalogInput.range("potential", c.potential(), 0, 99)));
  }

  /** Elenco principal (ACTIVE/INACTIVE) até {@code rosterMax}; practice squad até {@code practiceSquadMax} (regras da liga). */
  private void checkRosterLimits(Player p, Club target, RosterStatus status, boolean creating) {
    LeagueRules rules = rulesOf(target);
    boolean sameClub = !creating && p.getClub() != null && p.getClub().getId().equals(target.getId());
    if (status.countsTowardRoster()) {
      long n = players.countByClubIdAndRosterStatusIn(target.getId(), ROSTER) - (sameClub && p.getRosterStatus().countsTowardRoster() ? 1 : 0);
      if (n >= rules.getRosterMax()) throw new BusinessRuleException("ROSTER_FULL", "Elenco completo", rules.getRosterMax());
    } else if (status == RosterStatus.PRACTICE_SQUAD) {
      long n = players.countByClubIdAndRosterStatusIn(target.getId(), PRACTICE) - (sameClub && p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD ? 1 : 0);
      if (n >= rules.getPracticeSquadMax()) throw new BusinessRuleException("PRACTICE_SQUAD_FULL", "Practice squad completo", rules.getPracticeSquadMax());
    }
  }

  private int firstFreeNumber(Club club, Player p) {
    Set<Integer> used = new HashSet<>();
    for (Player o : players.findByClubIdOrderByOverallDesc(club.getId())) if (!o.getId().equals(p.getId())) used.add(o.getJerseyNumber());
    for (int n = 1; n <= 99; n++) if (!used.contains(n)) return n;
    if (!used.contains(0)) return 0;
    throw new BusinessRuleException("JERSEY_TAKEN", "Número já usado na franquia", 0);
  }

  /** Subatributos agrupados ({@code {"passing": {"throwPower": 90}}}) ou planos ({@code {"throwPower": 90}}), inteiros de 0 a 100. */
  static PlayerAttributes attributes(PlayerAttributes current, Map<String, Object> raw) {
    Map<String, Object> flat = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : raw.entrySet()) {
      if (e.getValue() instanceof Map<?, ?> group) for (Map.Entry<?, ?> g : group.entrySet()) flat.put(String.valueOf(g.getKey()), g.getValue());
      else flat.put(e.getKey(), e.getValue());
    }
    PlayerAttributes next = current == null ? new PlayerAttributes() : current.copy();
    for (Map.Entry<String, Object> e : flat.entrySet()) {
      AttributeKey key = AttributeKey.parse(e.getKey());
      if (key == null || !(e.getValue() instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue()) || n.intValue() < 0 || n.intValue() > 100) {
        throw new InvalidInputException("INVALID_ATTRIBUTE", "Subatributo inválido: " + e.getKey(), e.getKey());
      }
      next.with(key, n.intValue());
    }
    return next;
  }

  private static void applyContract(Player p, PlayerCommand c, World w) {
    if (c.baseSalaryByYear() == null && c.signingBonus() == null && c.guaranteed() == null && c.contractStartSeason() == null && c.contractType() == null) return;
    PlayerContract cur = p.getContract() == null ? PlayerContract.builder().startSeason(w.getBaseYear()).type(ContractType.VETERAN).build() : p.getContract();
    List<Long> salaries = c.baseSalaryByYear() == null ? new ArrayList<>(cur.getBaseSalaryByYear()) : new ArrayList<>(c.baseSalaryByYear());
    if (salaries.isEmpty() || salaries.size() > 7 || salaries.stream().anyMatch(x -> x == null || x < 0)) {
      throw new InvalidInputException("INVALID_CONTRACT", "Contrato inválido", "baseSalaryByYear");
    }
    long bonus = c.signingBonus() == null ? cur.getSigningBonus() : c.signingBonus();
    long guaranteed = c.guaranteed() == null ? cur.getGuaranteed() : c.guaranteed();
    if (bonus < 0) throw new InvalidInputException("INVALID_CONTRACT", "Contrato inválido", "signingBonus");
    if (guaranteed < 0 || guaranteed > salaries.stream().mapToLong(Long::longValue).sum()) {
      throw new InvalidInputException("INVALID_CONTRACT", "Contrato inválido", "guaranteed");
    }
    int start = c.contractStartSeason() == null ? cur.getStartSeason() : CatalogInput.range("contractStartSeason", c.contractStartSeason(), w.getBaseYear() - 6, w.getBaseYear() + 1);
    ContractType type = c.contractType() == null ? cur.getType() : CatalogInput.enumValue(ContractType.class, c.contractType(), "INVALID_CONTRACT");
    p.setContract(cur.toBuilder().baseSalaryByYear(salaries).signingBonus(bonus).guaranteed(guaranteed).startSeason(start).type(type).build());
  }

  // ---------------------------------------------------------------- apoio

  private void auditOk(String action, String resource, UUID id, String details) {
    AfterCommit.run("audit " + action, () -> audit.ok(action, resource, id, details));
  }

  private League league(UUID id) { return leagues.findById(id).orElseThrow(() -> new NotFoundException("Liga", id)); }

  private League leagueOfWorld(UUID worldId, UUID leagueId) {
    League l = league(leagueId);
    if (!l.getWorld().getId().equals(worldId)) throw new NotFoundException("Liga", leagueId);
    return l;
  }

  private Club club(UUID id) { return clubs.findById(id).orElseThrow(() -> new NotFoundException("Franquia", id)); }

  private Player player(UUID id) { return players.findById(id).orElseThrow(() -> new NotFoundException("Jogador", id)); }

  private static LeagueRules rulesOf(Club club) {
    return club == null || club.getLeague() == null || club.getLeague().getRules() == null ? LeagueRules.pro() : club.getLeague().getRules();
  }

  private EditorDtos.WorldDto worldDto(World w, EditorActor actor) {
    boolean editable = !w.isDefaultTemplate() && w.getKind() == WorldKind.TEMPLATE && WorldAccess.isOwner(w, actor);
    return new EditorDtos.WorldDto(w.getId(), w.getName(), w.getDescription(), w.getKind().name(), w.isDefaultTemplate(), editable,
        w.getSourceWorld() == null ? null : w.getSourceWorld().getId(), w.getBaseYear(), w.getSeed(), (int) leagues.countByWorldId(w.getId()),
        (int) clubs.countByWorldId(w.getId()), (int) players.countByWorldId(w.getId()));
  }

  private EditorDtos.LeagueDto leagueDto(League l) {
    List<Club> cs = clubs.findByLeagueIdOrderByNameAsc(l.getId());
    return EditorAssembler.league(l, cs.size(), structureValid(cs, divisions.findByConferenceLeagueIdOrderByConferenceSortOrderAscSortOrderAsc(l.getId())));
  }

  /**
   * Estrutura válida: 8–32 franquias, todas numa divisão da liga, nenhuma divisão vazia e tamanhos diferindo em no máximo 1. As
   * contagens partem das divisões reais da liga (uma divisão sem franquias conta como tamanho zero).
   */
  static boolean structureValid(List<Club> cs, List<Division> leagueDivisions) {
    if (cs.size() < League.MIN_TEAMS || cs.size() > League.MAX_TEAMS || leagueDivisions.isEmpty()) return false;
    Map<UUID, Integer> sizes = new HashMap<>();
    for (Division d : leagueDivisions) sizes.put(d.getId(), 0);
    for (Club c : cs) {
      if (c.getDivision() == null || !sizes.containsKey(c.getDivision().getId())) return false;
      sizes.merge(c.getDivision().getId(), 1, Integer::sum);
    }
    if (sizes.containsValue(0)) return false;
    int min = sizes.values().stream().mapToInt(Integer::intValue).min().orElse(0);
    int max = sizes.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    return max - min <= 1;
  }

  private EditorDtos.StructureDto structureDto(League l) {
    List<Club> cs = clubs.findByLeagueIdOrderByNameAsc(l.getId());
    List<EditorDtos.ConferenceDto> out = new ArrayList<>();
    for (Conference cf : conferences.findByLeagueIdOrderBySortOrderAsc(l.getId())) {
      List<EditorDtos.DivisionDto> ds = new ArrayList<>();
      for (Division d : divisions.findByConferenceIdOrderBySortOrderAsc(cf.getId())) {
        ds.add(new EditorDtos.DivisionDto(d.getId(), d.getName(),
            cs.stream().filter(c -> c.getDivision() != null && c.getDivision().getId().equals(d.getId())).map(Club::getId).toList()));
      }
      out.add(new EditorDtos.ConferenceDto(cf.getId(), cf.getName(), cf.getShortName(), ds));
    }
    return new EditorDtos.StructureDto(l.getId(), out);
  }

  private EditorDtos.ClubDto clubDto(Club c) {
    LeagueRules rules = rulesOf(c);
    return EditorAssembler.club(c, uniforms.findByClubId(c.getId()), helmets.findByClubId(c.getId()).orElse(null),
        (int) players.countByClubIdAndRosterStatusIn(c.getId(), ROSTER), (int) players.countByClubIdAndRosterStatusIn(c.getId(), PRACTICE), rules);
  }

  private EditorDtos.StadiumDto stadiumDto(Stadium s) { return EditorAssembler.stadium(s, sectors.findByStadiumId(s.getId())); }
}
