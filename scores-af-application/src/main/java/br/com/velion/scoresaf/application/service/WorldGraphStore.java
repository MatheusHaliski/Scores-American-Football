package br.com.velion.scoresaf.application.service;

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
import br.com.velion.scoresaf.application.port.out.persistence.WorldRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorld;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.catalog.Division;
import br.com.velion.scoresaf.domain.catalog.HelmetDesign;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.ManagerProfile;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.Stadium;
import br.com.velion.scoresaf.domain.catalog.StadiumSector;
import br.com.velion.scoresaf.domain.catalog.TicketPolicy;
import br.com.velion.scoresaf.domain.catalog.Uniform;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava, lê, copia e apaga o grafo inteiro de um mundo, sempre na ordem das FKs (gravar: mundo → ligas → conferências → divisões →
 * head coaches → estádios → setores → franquias → uniformes → capacetes → jogadores; apagar: o inverso). Usado pela semente do mundo
 * padrão e pelo editor (clonar e excluir mundo, RF5).
 */
@Service
public class WorldGraphStore {
  private final WorldRepository worlds; private final LeagueRepository leagues; private final ConferenceRepository conferences;
  private final DivisionRepository divisions; private final ManagerProfileRepository managers; private final StadiumRepository stadiums;
  private final StadiumSectorRepository sectors; private final ClubRepository clubs; private final UniformRepository uniforms;
  private final HelmetDesignRepository helmets; private final PlayerRepository players;

  public WorldGraphStore(WorldRepository worlds, LeagueRepository leagues, ConferenceRepository conferences, DivisionRepository divisions,
                         ManagerProfileRepository managers, StadiumRepository stadiums, StadiumSectorRepository sectors, ClubRepository clubs,
                         UniformRepository uniforms, HelmetDesignRepository helmets, PlayerRepository players) {
    this.worlds = worlds; this.leagues = leagues; this.conferences = conferences; this.divisions = divisions; this.managers = managers;
    this.stadiums = stadiums; this.sectors = sectors; this.clubs = clubs; this.uniforms = uniforms; this.helmets = helmets; this.players = players;
  }

  @Transactional
  public void persist(DefaultWorld w) {
    worlds.save(w.world());
    leagues.saveAll(w.leagues());
    conferences.saveAll(w.conferences());
    divisions.saveAll(w.divisions());
    managers.saveAll(w.managers());
    stadiums.saveAll(w.stadiums());
    sectors.saveAll(w.sectors());
    clubs.saveAll(w.clubs());
    uniforms.saveAll(w.uniforms());
    helmets.saveAll(w.helmets());
    players.saveAll(w.players());
  }

  @Transactional(readOnly = true)
  public DefaultWorld load(World world) {
    UUID id = world.getId();
    List<League> ls = leagues.findByWorldIdOrderByTierAscNameAsc(id);
    List<Conference> cs = new ArrayList<>();
    List<Division> ds = new ArrayList<>();
    for (League l : ls) {
      cs.addAll(conferences.findByLeagueIdOrderBySortOrderAsc(l.getId()));
      ds.addAll(divisions.findByConferenceLeagueIdOrderByConferenceSortOrderAscSortOrderAsc(l.getId()));
    }
    List<Stadium> ss = stadiums.findByWorldId(id);
    List<Club> clubList = clubs.findByWorldId(id);
    List<UUID> clubIds = clubList.stream().map(Club::getId).toList();
    return new DefaultWorld(world, ls, cs, ds, managers.findByWorldId(id), ss,
        ss.isEmpty() ? List.of() : sectors.findByStadiumIdIn(ss.stream().map(Stadium::getId).toList()), clubList,
        clubIds.isEmpty() ? List.of() : uniforms.findByClubIdIn(clubIds), clubIds.isEmpty() ? List.of() : helmets.findByClubIdIn(clubIds),
        players.findByWorldId(id));
  }

  /** Apaga o mundo e todo o catálogo, na ordem inversa das FKs. */
  @Transactional
  public void delete(World world) {
    DefaultWorld g = load(world);
    g.players().forEach(players::delete);
    g.helmets().forEach(helmets::delete);
    g.uniforms().forEach(uniforms::delete);
    g.clubs().forEach(clubs::delete);
    g.sectors().forEach(sectors::delete);
    g.stadiums().forEach(stadiums::delete);
    g.managers().forEach(managers::delete);
    g.divisions().forEach(divisions::delete);
    g.conferences().forEach(conferences::delete);
    g.leagues().forEach(leagues::delete);
    worlds.delete(world);
  }

  /**
   * Cópia profunda do mundo {@code source} para um mundo TEMPLATE novo de {@code owner} (RF5: o mundo padrão nunca é alterado; o
   * usuário edita a cópia). Ids novos; referências internas remapeadas.
   */
  @Transactional
  public World cloneWorld(World source, UserAccount owner, String name) {
    DefaultWorld src = load(source);
    World w = new World();
    w.setOwnerUser(owner); w.setName(name); w.setDescription(source.getDescription()); w.setSourceWorld(source);
    w.setKind(WorldKind.TEMPLATE); w.setBaseYear(source.getBaseYear()); w.setSeed(source.getSeed());

    Map<UUID, League> leagueMap = new HashMap<>();
    Map<UUID, Conference> confMap = new HashMap<>();
    Map<UUID, Division> divMap = new HashMap<>();
    Map<UUID, ManagerProfile> managerMap = new HashMap<>();
    Map<UUID, Stadium> stadiumMap = new HashMap<>();
    Map<UUID, Club> clubMap = new HashMap<>();
    List<League> ls = new ArrayList<>();
    for (League o : src.leagues()) {
      League n = new League();
      n.setWorld(w); n.setName(o.getName()); n.setShortName(o.getShortName()); n.setCountry(o.getCountry()); n.setTier(o.getTier());
      n.setLogoUrl(o.getLogoUrl()); n.setRuleset(o.getRuleset()); n.setRules(o.getRules().toBuilder().build()); n.setTeamCount(o.getTeamCount());
      n.setConferenceCount(o.getConferenceCount()); n.setDivisionsPerConference(o.getDivisionsPerConference()); n.setSalaryCap(o.getSalaryCap());
      n.setSalaryFloor(o.getSalaryFloor()); n.setPrestige(o.getPrestige()); n.setTvRightsPerSeason(o.getTvRightsPerSeason());
      leagueMap.put(o.getId(), n); ls.add(n);
    }
    List<Conference> cs = new ArrayList<>();
    for (Conference o : src.conferences()) {
      Conference n = new Conference();
      n.setLeague(leagueMap.get(o.getLeague().getId())); n.setName(o.getName()); n.setShortName(o.getShortName()); n.setLogoKey(o.getLogoKey());
      n.setSortOrder(o.getSortOrder());
      confMap.put(o.getId(), n); cs.add(n);
    }
    List<Division> ds = new ArrayList<>();
    for (Division o : src.divisions()) {
      Division n = new Division();
      n.setConference(confMap.get(o.getConference().getId())); n.setName(o.getName()); n.setSortOrder(o.getSortOrder());
      divMap.put(o.getId(), n); ds.add(n);
    }
    List<ManagerProfile> ms = new ArrayList<>();
    for (ManagerProfile o : src.managers()) {
      ManagerProfile n = new ManagerProfile();
      n.setWorld(w); n.setName(o.getName()); n.setNationality(o.getNationality()); n.setHuman(false); n.setOverall(o.getOverall());
      n.setCoachStyle(o.getCoachStyle()); n.setPreferredFormation(o.getPreferredFormation()); n.setPreferredDefense(o.getPreferredDefense());
      n.setReputationLeague(o.getReputationLeague()); n.setCareerTitles(o.getCareerTitles()); n.setCareerGames(o.getCareerGames());
      n.setCareerWins(o.getCareerWins()); n.setCareerPlayoffWins(o.getCareerPlayoffWins()); n.setAvatarUrl(o.getAvatarUrl());
      managerMap.put(o.getId(), n); ms.add(n);
    }
    List<Stadium> ss = new ArrayList<>();
    for (Stadium o : src.stadiums()) {
      Stadium n = new Stadium();
      n.setWorld(w); n.setName(o.getName()); n.setCapacity(o.getCapacity()); n.setRoof(o.getRoof()); n.setSurface(o.getSurface());
      n.setClimateZone(o.getClimateZone()); n.setLevel(o.getLevel()); n.setAtmosphere(o.getAtmosphere()); n.setValue(o.getValue());
      n.setMaintenanceCostPerWeek(o.getMaintenanceCostPerWeek()); n.setModel3dUrl(o.getModel3dUrl()); n.setEndZoneArtKey(o.getEndZoneArtKey());
      TicketPolicy tp = o.getTicketPolicy() == null ? new TicketPolicy() : o.getTicketPolicy();
      n.setTicketPolicy(new TicketPolicy(tp.getMemberDiscountPct(), tp.isDynamicPricing(), tp.getSeasonTicketPct()));
      stadiumMap.put(o.getId(), n); ss.add(n);
    }
    List<StadiumSector> secs = new ArrayList<>();
    for (StadiumSector o : src.sectors()) {
      StadiumSector n = new StadiumSector();
      n.setStadium(stadiumMap.get(o.getStadium().getId())); n.setType(o.getType()); n.setSeats(o.getSeats()); n.setTicketPrice(o.getTicketPrice());
      n.setCondition(o.getCondition()); n.setUpgradeCost(o.getUpgradeCost());
      secs.add(n);
    }
    List<Club> clubList = new ArrayList<>();
    for (Club o : src.clubs()) {
      Club n = new Club();
      n.setWorld(w); n.setLeague(o.getLeague() == null ? null : leagueMap.get(o.getLeague().getId()));
      n.setConference(o.getConference() == null ? null : confMap.get(o.getConference().getId()));
      n.setDivision(o.getDivision() == null ? null : divMap.get(o.getDivision().getId()));
      n.setName(o.getName()); n.setShortName(o.getShortName()); n.setCity(o.getCity()); n.setFoundedYear(o.getFoundedYear()); n.setLogoKey(o.getLogoKey());
      n.setPrimaryColor(o.getPrimaryColor()); n.setSecondaryColor(o.getSecondaryColor()); n.setAccentColor(o.getAccentColor());
      n.setPrestige(o.getPrestige()); n.setReputationBoard(o.getReputationBoard()); n.setReputationFans(o.getReputationFans()); n.setCash(o.getCash());
      n.setScoutingBudget(o.getScoutingBudget()); n.setScoutingLevel(o.getScoutingLevel()); n.setExpectedWins(o.getExpectedWins());
      n.setStadium(o.getStadium() == null ? null : stadiumMap.get(o.getStadium().getId()));
      n.setManager(o.getManager() == null ? null : managerMap.get(o.getManager().getId()));
      n.setUserControlled(false);
      clubMap.put(o.getId(), n); clubList.add(n);
    }
    List<Uniform> us = new ArrayList<>();
    for (Uniform o : src.uniforms()) {
      Uniform n = new Uniform();
      n.setClub(clubMap.get(o.getClub().getId())); n.setSlot(o.getSlot()); n.setJerseyColor(o.getJerseyColor()); n.setPantsColor(o.getPantsColor());
      n.setNumberColor(o.getNumberColor()); n.setNumberOutlineColor(o.getNumberOutlineColor()); n.setPattern(o.getPattern());
      n.setTextureKey(o.getTextureKey()); n.setActive(o.isActive()); n.setRevision(o.getRevision());
      us.add(n);
    }
    List<HelmetDesign> hs = new ArrayList<>();
    for (HelmetDesign o : src.helmets()) {
      HelmetDesign n = new HelmetDesign();
      n.setClub(clubMap.get(o.getClub().getId())); n.setShellColor(o.getShellColor()); n.setStripe(o.getStripe()); n.setStripeColor(o.getStripeColor());
      n.setFacemaskColor(o.getFacemaskColor()); n.setDecalKey(o.getDecalKey()); n.setFinish(o.getFinish());
      hs.add(n);
    }
    List<Player> ps = new ArrayList<>();
    for (Player o : src.players()) {
      Player n = new Player();
      n.setWorld(w); n.setClub(o.getClub() == null ? null : clubMap.get(o.getClub().getId()));
      n.setName(o.getName()); n.setNationality(o.getNationality()); n.setCollege(o.getCollege()); n.setBirthDate(o.getBirthDate()); n.setAge(o.getAge());
      n.setHeightCm(o.getHeightCm()); n.setWeightKg(o.getWeightKg()); n.setPosition(o.getPosition()); n.setSecondaryPosition(o.getSecondaryPosition());
      n.setAttributes(o.getAttributes().copy()); n.setOverall(o.getOverall()); n.setPotential(o.getPotential()); n.setExperienceYears(o.getExperienceYears());
      n.setRosterStatus(o.getRosterStatus()); n.setCondition(o.getCondition()); n.setMorale(o.getMorale()); n.setSuspensionWeeks(o.getSuspensionWeeks());
      n.setContract(o.getContract() == null ? null : o.getContract().toBuilder().baseSalaryByYear(new ArrayList<>(o.getContract().getBaseSalaryByYear())).build());
      n.setJerseyNumber(o.getJerseyNumber()); n.setLegendary(o.isLegendary()); n.setPhotoUrl(o.getPhotoUrl()); n.setModel3dUrl(o.getModel3dUrl());
      ps.add(n);
    }
    persist(new DefaultWorld(w, ls, cs, ds, ms, ss, secs, clubList, us, hs, ps));
    return w;
  }
}
