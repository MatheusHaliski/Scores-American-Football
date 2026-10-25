package br.com.velion.scoresaf.application.seed;

import br.com.velion.scoresaf.domain.catalog.AttributeGroup;
import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Conference;
import br.com.velion.scoresaf.domain.catalog.Division;
import br.com.velion.scoresaf.domain.catalog.HelmetDesign;
import br.com.velion.scoresaf.domain.catalog.HelmetFinish;
import br.com.velion.scoresaf.domain.catalog.HelmetStripe;
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
import br.com.velion.scoresaf.domain.engine.ratings.OverallCalculator;
import br.com.velion.scoresaf.domain.engine.ratings.OverallWeights;
import br.com.velion.scoresaf.domain.game.Ruleset;
import br.com.velion.scoresaf.domain.shared.ClimateZone;
import br.com.velion.scoresaf.domain.shared.CoachStyle;
import br.com.velion.scoresaf.domain.shared.ContractType;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.Formation;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.PositionGroup;
import br.com.velion.scoresaf.domain.shared.RoofType;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.StadiumSectorType;
import br.com.velion.scoresaf.domain.shared.Surface;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Gera o mundo padrão (TEMPLATE, sem dono) de forma <b>determinística</b>: mesma semente ⇒ mesmos ids, nomes, atributos e contratos.
 * Uma liga PRO fictícia (Q-4: nenhum nome real de atleta, clube, liga, cidade-sede ou marca) com 32 franquias em 2 conferências × 4
 * divisões de 4; cada franquia com estádio de 60–80 mil lugares (5 setores), uniformes, capacete, head coach IA, 53 jogadores no
 * elenco ({@link #ROSTER}) e 16 no practice squad ({@link #PRACTICE_SQUAD}). Os atributos seguem o perfil da posição (os subatributos
 * que a posição pesa no overall ficam acima do alvo, os outros abaixo) e o overall final é ajustado ao alvo de cada jogador. Os nomes
 * dos jogadores são sintéticos (prenome comum + sobrenome montado de sílabas) e nunca se repetem no mundo. Java puro, sem Spring.
 */
public final class DefaultWorldFactory {
  public static final long DEFAULT_SEED = 20261025L;
  public static final int BASE_YEAR = 2026;
  /** Data de referência da idade e dos contratos da temporada inicial. */
  public static final LocalDate SEASON_REFERENCE_DATE = LocalDate.of(BASE_YEAR, 9, 1);
  public static final String WORLD_NAME = "Mundo Padrão Scores AF";
  public static final String LEAGUE_NAME = "Liga Velion de Futebol Americano";
  /** Teto e piso salarial da liga padrão (centavos de S$). */
  public static final long SALARY_CAP = 255_000_000_00L;
  public static final long SALARY_FLOOR = 227_000_000_00L;

  /** Elenco de 53 (3 QB, 3 RB, 1 FB, 6 WR, 3 TE, 9 OL, 9 DL, 6 LB, 6 CB, 4 S, K, P, LS). */
  public static final List<Position> ROSTER = expand(Map.ofEntries(
      Map.entry(Position.QB, 3), Map.entry(Position.RB, 3), Map.entry(Position.FB, 1), Map.entry(Position.WR, 6), Map.entry(Position.TE, 3),
      Map.entry(Position.LT, 2), Map.entry(Position.LG, 2), Map.entry(Position.C, 2), Map.entry(Position.RG, 1), Map.entry(Position.RT, 2),
      Map.entry(Position.DE, 5), Map.entry(Position.DT, 4), Map.entry(Position.OLB, 4), Map.entry(Position.MLB, 2), Map.entry(Position.CB, 6),
      Map.entry(Position.FS, 2), Map.entry(Position.SS, 2), Map.entry(Position.K, 1), Map.entry(Position.P, 1), Map.entry(Position.LS, 1)));

  /** Practice squad de 16. */
  public static final List<Position> PRACTICE_SQUAD = expand(Map.ofEntries(
      Map.entry(Position.QB, 1), Map.entry(Position.RB, 1), Map.entry(Position.WR, 2), Map.entry(Position.TE, 1), Map.entry(Position.LT, 1),
      Map.entry(Position.LG, 1), Map.entry(Position.C, 1), Map.entry(Position.DE, 2), Map.entry(Position.DT, 1), Map.entry(Position.OLB, 1),
      Map.entry(Position.MLB, 1), Map.entry(Position.CB, 2), Map.entry(Position.SS, 1)));

  record ClubSpec(String city, String mascot, String shortName, String primary, String secondary, String accent, int prestige, RoofType roof,
                  Surface surface, ClimateZone climate) {
    String name() { return mascot + " de " + city; }
  }

  record DivisionSpec(String name, List<ClubSpec> clubs) {}

  record ConferenceSpec(String name, String shortName, List<DivisionSpec> divisions) {}

  private static ClubSpec c(String city, String mascot, String shortName, String primary, String secondary, String accent, int prestige,
                            RoofType roof, Surface surface, ClimateZone climate) {
    return new ClubSpec(city, mascot, shortName, primary, secondary, accent, prestige, roof, surface, climate);
  }

  // Cidades e mascotes inventados (Q-4). Nenhum mascote traduz o de uma franquia real.
  static final List<ConferenceSpec> CONFERENCES = List.of(
      new ConferenceSpec("Conferência Atlântica", "CAT", List.of(
          new DivisionSpec("Atlântica Norte", List.of(
              c("Porto Alvorada", "Trovões", "PAV", "#1B2A4A", "#F2B705", "#FFFFFF", 82, RoofType.OPEN, Surface.GRASS, ClimateZone.COLD),
              c("Serra Alta", "Lobos-Guará", "SAL", "#7A2E1F", "#E8C39E", "#1A1A1A", 70, RoofType.OPEN, Surface.TURF, ClimateZone.COLD),
              c("Baía Serena", "Arpões", "BSE", "#005B7F", "#9AD1D4", "#F4F4F4", 64, RoofType.RETRACTABLE, Surface.TURF, ClimateZone.TEMPERATE),
              c("Vila Aurora", "Carcarás", "VAU", "#3B1F5C", "#F28C28", "#FFFFFF", 58, RoofType.OPEN, Surface.GRASS, ClimateZone.COLD))),
          new DivisionSpec("Atlântica Leste", List.of(
              c("Pedra Lume", "Tubarões-Martelo", "PLU", "#0E3B43", "#57C4AD", "#E6E6E6", 76, RoofType.OPEN, Surface.GRASS, ClimateZone.TEMPERATE),
              c("Vale Sereno", "Bisões", "VSE", "#4E342E", "#D7CCC8", "#C62828", 68, RoofType.DOME, Surface.TURF, ClimateZone.COLD),
              c("Campo Celeste", "Jaguatiricas", "CCE", "#0D47A1", "#FFCA28", "#FFFFFF", 61, RoofType.OPEN, Surface.GRASS, ClimateZone.TEMPERATE),
              c("Rio Ametista", "Harpias", "RAM", "#5E2B97", "#C0C0C0", "#111111", 73, RoofType.OPEN, Surface.TURF, ClimateZone.TEMPERATE))),
          new DivisionSpec("Atlântica Sul", List.of(
              c("Monte Azulão", "Tempestades", "MAZ", "#263238", "#4FC3F7", "#FFFFFF", 79, RoofType.RETRACTABLE, Surface.TURF, ClimateZone.WARM),
              c("Lagoa Prata", "Furacões", "LPR", "#B71C1C", "#ECEFF1", "#212121", 66, RoofType.OPEN, Surface.GRASS, ClimateZone.HOT),
              c("Cerro Ventania", "Centauros", "CVE", "#1B5E20", "#FFD54F", "#FFFFFF", 60, RoofType.OPEN, Surface.GRASS, ClimateZone.WARM),
              c("Planalto Anil", "Mamutes", "PAN", "#283593", "#FF7043", "#FFFFFF", 71, RoofType.DOME, Surface.TURF, ClimateZone.WARM))),
          new DivisionSpec("Atlântica Oeste", List.of(
              c("Porto Brisa", "Cometas", "PBR", "#00695C", "#FFAB00", "#FFFFFF", 84, RoofType.OPEN, Surface.GRASS, ClimateZone.TEMPERATE),
              c("Serra Cobalto", "Escorpiões", "SCO", "#1A237E", "#B0BEC5", "#D50000", 63, RoofType.OPEN, Surface.TURF, ClimateZone.COLD),
              c("Baía Coral", "Ciclones", "BCO", "#E65100", "#263238", "#FFFFFF", 69, RoofType.OPEN, Surface.GRASS, ClimateZone.HOT),
              c("Vila Âmbar", "Avalanches", "VAM", "#37474F", "#FFB300", "#FFFFFF", 57, RoofType.DOME, Surface.TURF, ClimateZone.COLD))))),
      new ConferenceSpec("Conferência Pacífica", "CPA", List.of(
          new DivisionSpec("Pacífica Norte", List.of(
              c("Ponta Granito", "Sucuris", "PGR", "#33691E", "#CDDC39", "#000000", 74, RoofType.OPEN, Surface.GRASS, ClimateZone.TEMPERATE),
              c("Campos Rubi", "Tamanduás", "CRU", "#880E4F", "#F8BBD0", "#FFFFFF", 62, RoofType.OPEN, Surface.TURF, ClimateZone.TEMPERATE),
              c("Nova Esmeralda", "Javalis", "NES", "#004D40", "#FFFFFF", "#A1887F", 80, RoofType.RETRACTABLE, Surface.TURF, ClimateZone.COLD),
              c("Alto Jacarandá", "Condores", "AJA", "#4A148C", "#FFEB3B", "#FFFFFF", 67, RoofType.OPEN, Surface.GRASS, ClimateZone.COLD))),
          new DivisionSpec("Pacífica Leste", List.of(
              c("São Lucário", "Albatrozes", "SLU", "#01579B", "#FFFFFF", "#FF6F00", 77, RoofType.OPEN, Surface.GRASS, ClimateZone.WARM),
              c("Barra do Sol Poente", "Bigornas", "BSP", "#212121", "#FF5722", "#BDBDBD", 65, RoofType.DOME, Surface.TURF, ClimateZone.HOT),
              c("Ribeira Turquesa", "Faróis", "RTU", "#006064", "#FFD600", "#FFFFFF", 59, RoofType.OPEN, Surface.GRASS, ClimateZone.WARM),
              c("Santa Bruma", "Vulcões", "SBR", "#BF360C", "#212121", "#FFC107", 72, RoofType.OPEN, Surface.TURF, ClimateZone.TEMPERATE))),
          new DivisionSpec("Pacífica Sul", List.of(
              c("Porto Quartzo", "Meteoros", "PQU", "#311B92", "#00E5FF", "#FFFFFF", 83, RoofType.RETRACTABLE, Surface.TURF, ClimateZone.HOT),
              c("Cedro Alto", "Sentinelas", "CAL", "#3E2723", "#8BC34A", "#FFFFFF", 61, RoofType.OPEN, Surface.GRASS, ClimateZone.TEMPERATE),
              c("Terra Ventura", "Pioneiros", "TVE", "#827717", "#FFFFFF", "#1B5E20", 66, RoofType.OPEN, Surface.GRASS, ClimateZone.HOT),
              c("Costa Safira", "Marujos", "CSA", "#0D47A1", "#FFFFFF", "#E53935", 75, RoofType.OPEN, Surface.GRASS, ClimateZone.WARM))),
          new DivisionSpec("Pacífica Oeste", List.of(
              c("Morro Carmim", "Rinocerontes", "MCA", "#8E0000", "#9E9E9E", "#FFFFFF", 70, RoofType.OPEN, Surface.TURF, ClimateZone.WARM),
              c("Vila Topázio", "Tatus-Canastra", "VTO", "#F57F17", "#3E2723", "#FFFFFF", 56, RoofType.OPEN, Surface.GRASS, ClimateZone.HOT),
              c("Areia Branca do Leste", "Cascavéis", "ABL", "#5D4037", "#FFE082", "#000000", 63, RoofType.DOME, Surface.TURF, ClimateZone.HOT),
              c("Lagoa Funda", "Arraias", "LFU", "#0277BD", "#B3E5FC", "#FFFFFF", 78, RoofType.OPEN, Surface.GRASS, ClimateZone.TEMPERATE))))));

  // ---------------------------------------------------------------- nomes sintéticos (Q-4)
  private static final String[] FIRST = {"Lucas", "Gabriel", "Rafael", "Mateus", "Bruno", "Thiago", "Felipe", "Diego", "Caio", "Vinícius",
      "Rodrigo", "Guilherme", "André", "Pedro", "Henrique", "Igor", "Renan", "Danilo", "Eduardo", "Otávio", "Jordan", "Tyler", "Marcus",
      "Malik", "Devin", "Cameron", "Trey", "Isaiah", "Brandon", "Kyle", "Darius", "Jamal", "Elijah", "Corey", "Austin", "Logan", "Mason",
      "Nolan", "Garrett", "Dante", "Mateo", "Santiago", "Joaquín", "Tomás", "Emiliano", "Gonzalo", "Iván", "Andrés", "Noah", "Hugo", "Théo",
      "Kofi", "Emeka", "Tunde", "Ravi", "Kenji", "Aleksander", "Bjorn", "Luca", "Enzo"};
  private static final String[] SURNAME_HEAD = {"Albu", "Bran", "Cor", "Dal", "Esper", "Fal", "Gar", "Hal", "Ivar", "Jor", "Kel", "Lor",
      "Mar", "Nor", "Orl", "Pen", "Quel", "Ros", "Sal", "Tor", "Ulm", "Var", "Wes", "Zel", "Bel", "Cas", "Dor", "Fen", "Gil", "Mon"};
  private static final String[] SURNAME_TAIL = {"dell", "vane", "more", "sen", "ley", "ard", "wick", "ton", "ford", "mont", "quist", "berg",
      "ane", "ino", "eiro", "ales", "ado", "ette", "ore", "sby", "aro", "elli", "anski", "ovic"};
  private static final String[] COLLEGE_KIND = {"Universidade Estadual de", "Universidade Federal de", "Instituto Politécnico de",
      "Universidade Técnica de", "Colégio Superior de"};
  private static final String[] COLLEGE_PLACE = {"Vale Brumoso", "Serra Lavanda", "Campo Ventoso", "Rio Cristalino do Sul", "Lago Âmbar",
      "Pedra Sonora", "Bela Aurora do Norte", "Cruzeiro Alto", "Monte Prateado", "Porto Celeste", "Santa Neblina", "Ribeirão Dourado"};
  private static final String[] NATIONALITY = {"BRA", "BRA", "BRA", "BRA", "BRA", "BRA", "BRA", "USA", "USA", "USA", "USA", "USA", "MEX",
      "MEX", "CAN", "CAN", "ARG", "COL", "GER", "NGA"};

  /** Teto salarial de referência por grupo de posição (S$ inteiros, antes do ajuste ao teto da liga). */
  private static final Map<PositionGroup, Long> TOP_SALARY = new EnumMap<>(Map.ofEntries(
      Map.entry(PositionGroup.QB, 45_000_000L), Map.entry(PositionGroup.RB, 12_000_000L), Map.entry(PositionGroup.FB, 3_500_000L),
      Map.entry(PositionGroup.WR, 25_000_000L), Map.entry(PositionGroup.TE, 14_000_000L), Map.entry(PositionGroup.OL, 20_000_000L),
      Map.entry(PositionGroup.DL, 26_000_000L), Map.entry(PositionGroup.LB, 18_000_000L), Map.entry(PositionGroup.CB, 20_000_000L),
      Map.entry(PositionGroup.S, 15_000_000L), Map.entry(PositionGroup.K, 5_000_000L), Map.entry(PositionGroup.P, 3_500_000L),
      Map.entry(PositionGroup.LS, 1_500_000L)));
  private static final long MIN_SALARY = 900_000L;
  private static final long PRACTICE_SALARY = 216_000L;

  private SplittableRandom rng;
  private Set<String> usedNames;

  public DefaultWorld build() { return build(DEFAULT_SEED); }

  /** Mundo padrão determinístico para a semente dada. */
  public synchronized DefaultWorld build(long seed) {
    rng = new SplittableRandom(seed);
    usedNames = new HashSet<>();

    World world = new World();
    world.setId(uuid());
    world.setName(WORLD_NAME);
    world.setDescription("Catálogo padrão fictício: 1 liga profissional, 32 franquias, 2 conferências × 4 divisões, 53 + 16 jogadores por franquia.");
    world.setKind(WorldKind.TEMPLATE);
    world.setBaseYear(BASE_YEAR);
    world.setSeed(seed);

    League league = new League();
    league.setId(uuid());
    league.setWorld(world);
    league.setName(LEAGUE_NAME);
    league.setShortName("LVFA");
    league.setCountry("INT");
    league.setTier(1);
    league.setRuleset(Ruleset.PRO);
    league.setRules(LeagueRules.pro());
    league.setSalaryCap(SALARY_CAP);
    league.setSalaryFloor(SALARY_FLOOR);
    league.setPrestige(85);
    league.setTvRightsPerSeason(280_000_000_00L);

    List<Conference> conferences = new ArrayList<>();
    List<Division> divisions = new ArrayList<>();
    List<ManagerProfile> managers = new ArrayList<>();
    List<Stadium> stadiums = new ArrayList<>();
    List<StadiumSector> sectors = new ArrayList<>();
    List<Club> clubs = new ArrayList<>();
    List<Uniform> uniforms = new ArrayList<>();
    List<HelmetDesign> helmets = new ArrayList<>();
    List<Player> players = new ArrayList<>();
    List<double[]> strength = new ArrayList<>();

    for (int ci = 0; ci < CONFERENCES.size(); ci++) {
      ConferenceSpec cs = CONFERENCES.get(ci);
      Conference conf = new Conference();
      conf.setId(uuid());
      conf.setLeague(league); conf.setName(cs.name()); conf.setShortName(cs.shortName()); conf.setSortOrder(ci);
      conf.setLogoKey("conference-" + cs.shortName().toLowerCase());
      conferences.add(conf);
      for (int di = 0; di < cs.divisions().size(); di++) {
        DivisionSpec ds = cs.divisions().get(di);
        Division div = new Division();
        div.setId(uuid());
        div.setConference(conf); div.setName(ds.name()); div.setSortOrder(di);
        divisions.add(div);
        for (ClubSpec spec : ds.clubs()) {
          ManagerProfile manager = manager(world, spec);
          managers.add(manager);
          Club club = club(world, league, conf, div, spec, manager);
          Stadium stadium = stadium(world, spec);
          List<StadiumSector> secs = sectors(stadium, spec.prestige());
          stadium.recalculateCapacity(secs);
          club.setStadium(stadium);
          stadiums.add(stadium); sectors.addAll(secs);
          clubs.add(club);
          uniforms.addAll(uniforms(club, spec));
          helmets.add(helmet(club, spec));
          List<Player> squad = squad(world, club, spec);
          fitToCap(squad, league);
          players.addAll(squad);
          strength.add(new double[] {clubs.size() - 1, starterStrength(squad)});
        }
      }
    }
    // vitórias esperadas pelo ranking de força dos titulares (RF41)
    strength.sort(Comparator.comparingDouble((double[] s) -> s[1]).reversed());
    for (int rank = 0; rank < strength.size(); rank++) {
      int expected = (int) Math.round(4 + 9.0 * (strength.size() - 1 - rank) / (strength.size() - 1));
      clubs.get((int) strength.get(rank)[0]).setExpectedWins(expected);
    }
    league.setTeamCount(clubs.size());
    league.setConferenceCount(conferences.size());
    league.setDivisionsPerConference(divisions.size() / conferences.size());
    return new DefaultWorld(world, List.of(league), conferences, divisions, managers, stadiums, sectors, clubs, uniforms, helmets, players);
  }

  // ---------------------------------------------------------------- construção
  private ManagerProfile manager(World world, ClubSpec spec) {
    ManagerProfile m = new ManagerProfile();
    m.setId(uuid());
    m.setWorld(world);
    m.setName(uniqueName());
    m.setNationality(pick(NATIONALITY));
    m.setHuman(false);
    m.setOverall(clamp(40 + (int) Math.round(spec.prestige() * 0.5) + rng.nextInt(-5, 6), 40, 92));
    m.setCoachStyle(CoachStyle.values()[rng.nextInt(CoachStyle.values().length)]);
    m.setPreferredFormation(Formation.values()[rng.nextInt(Formation.GOAL_LINE.ordinal())]);
    m.setPreferredDefense(rng.nextBoolean() ? DefensePackage.BASE_4_3 : DefensePackage.BASE_3_4);
    m.setReputationLeague(clamp(m.getOverall() + rng.nextInt(-8, 9), 20, 99));
    m.setCareerGames(rng.nextInt(17, 400));
    m.setCareerWins((int) Math.round(m.getCareerGames() * (0.30 + m.getOverall() / 400.0)));
    m.setCareerPlayoffWins(spec.prestige() >= 75 ? rng.nextInt(0, 12) : rng.nextInt(0, 4));
    m.setCareerTitles(spec.prestige() >= 80 ? rng.nextInt(0, 3) : 0);
    return m;
  }

  private Club club(World world, League league, Conference conf, Division div, ClubSpec spec, ManagerProfile manager) {
    Club c = new Club();
    c.setId(uuid());
    c.setWorld(world); c.setLeague(league); c.setConference(conf); c.setDivision(div);
    c.setName(spec.name()); c.setShortName(spec.shortName()); c.setCity(spec.city());
    c.setFoundedYear(1950 + rng.nextInt(0, 66));
    c.setLogoKey("club-" + spec.shortName().toLowerCase());
    c.setPrimaryColor(spec.primary()); c.setSecondaryColor(spec.secondary()); c.setAccentColor(spec.accent());
    c.setPrestige(spec.prestige());
    c.setReputationBoard(clamp(spec.prestige() + rng.nextInt(-10, 11), 10, 99));
    c.setReputationFans(clamp(spec.prestige() + rng.nextInt(-12, 13), 10, 99));
    c.setCash(Math.round((60_000_000L + spec.prestige() * 1_500_000L) * (0.8 + rng.nextDouble() * 0.4)) * 100L);
    c.setScoutingBudget((2_000_000L + spec.prestige() * 40_000L) * 100L);
    c.setScoutingLevel(clamp(1 + spec.prestige() / 20, 1, 5));
    c.setManager(manager);
    c.setUserControlled(false);
    return c;
  }

  private Stadium stadium(World world, ClubSpec spec) {
    Stadium s = new Stadium();
    s.setId(uuid());
    s.setWorld(world); // Stadium.club é o lado inverso: quem liga os dois é Club.stadium
    s.setName((rng.nextBoolean() ? "Arena " : "Estádio ") + spec.city());
    s.setRoof(spec.roof()); s.setSurface(spec.surface()); s.setClimateZone(spec.climate());
    s.setLevel(clamp(1 + spec.prestige() / 20, 1, 5));
    s.setAtmosphere(clamp(spec.prestige() + rng.nextInt(-10, 11), 20, 99));
    s.setTicketPolicy(new TicketPolicy(10 + rng.nextInt(0, 3) * 5, spec.prestige() >= 75, 50 + rng.nextInt(0, 26)));
    s.setEndZoneArtKey("endzone-" + spec.shortName().toLowerCase());
    return s;
  }

  private List<StadiumSector> sectors(Stadium stadium, int prestige) {
    int capacity = clamp(Stadium.MIN_CAPACITY + (prestige - 55) * 600 + rng.nextInt(-2_000, 2_001), Stadium.MIN_CAPACITY + 500, Stadium.MAX_CAPACITY - 500);
    double priceFactor = 0.8 + prestige / 250.0;
    int lower = (int) (capacity * 0.38), upper = (int) (capacity * 0.40), club = (int) (capacity * 0.10), suites = (int) (capacity * 0.03);
    int endZone = capacity - lower - upper - club - suites;
    long lvl = stadium.getLevel();
    List<StadiumSector> out = new ArrayList<>();
    out.add(sector(stadium, StadiumSectorType.LOWER_BOWL, lower, price(150, priceFactor), 400_000_00L * lvl));
    out.add(sector(stadium, StadiumSectorType.UPPER_BOWL, upper, price(80, priceFactor), 300_000_00L * lvl));
    out.add(sector(stadium, StadiumSectorType.SIDELINE_CLUB, club, price(420, priceFactor), 600_000_00L * lvl));
    out.add(sector(stadium, StadiumSectorType.END_ZONE, endZone, price(95, priceFactor), 250_000_00L * lvl));
    out.add(sector(stadium, StadiumSectorType.LUXURY_SUITES, suites, price(2_500, priceFactor), 900_000_00L * lvl));
    stadium.setValue(capacity * 9_000_00L);
    stadium.setMaintenanceCostPerWeek(capacity * 30_00L);
    return out;
  }

  private StadiumSector sector(Stadium stadium, StadiumSectorType type, int seats, long price, long upgradeCost) {
    StadiumSector s = new StadiumSector();
    s.setId(uuid());
    s.setStadium(stadium); s.setType(type); s.setSeats(seats); s.setTicketPrice(price);
    s.setCondition(clamp(70 + rng.nextInt(0, 31), 0, 100)); s.setUpgradeCost(upgradeCost);
    return s;
  }

  private static long price(int units, double factor) { return Math.round(units * factor) * 100L; }

  private List<Uniform> uniforms(Club club, ClubSpec spec) {
    List<Uniform> out = new ArrayList<>();
    out.add(uniform(club, UniformSlot.HOME, spec.primary(), spec.secondary(), contrast(spec.primary()), spec.secondary(), UniformPattern.SOLID));
    out.add(uniform(club, UniformSlot.AWAY, "#FFFFFF", spec.primary(), spec.primary(), spec.secondary(), UniformPattern.SLEEVE_STRIPES));
    out.add(uniform(club, UniformSlot.ALTERNATE, spec.secondary(), spec.primary(), contrast(spec.secondary()), spec.primary(),
        UniformPattern.values()[rng.nextInt(UniformPattern.values().length)]));
    return out;
  }

  private Uniform uniform(Club club, UniformSlot slot, String jersey, String pants, String number, String outline, UniformPattern pattern) {
    Uniform u = new Uniform();
    u.setId(uuid());
    u.setClub(club); u.setSlot(slot); u.setJerseyColor(jersey); u.setPantsColor(pants); u.setNumberColor(number);
    u.setNumberOutlineColor(outline); u.setPattern(pattern); u.setActive(true); u.setRevision(1);
    return u;
  }

  private HelmetDesign helmet(Club club, ClubSpec spec) {
    HelmetDesign h = new HelmetDesign();
    h.setId(uuid());
    h.setClub(club);
    h.setShellColor(spec.primary());
    h.setStripe(HelmetStripe.values()[rng.nextInt(HelmetStripe.values().length)]);
    h.setStripeColor(spec.secondary());
    h.setFacemaskColor(pick(new String[] {"#808080", "#FFFFFF", "#111111", spec.secondary()}));
    h.setFinish(HelmetFinish.values()[rng.nextInt(HelmetFinish.values().length)]);
    h.setDecalKey("helmet-" + spec.shortName().toLowerCase());
    return h;
  }

  /** Preto ou branco, o que contrasta mais com a cor (luminância relativa simples). */
  static String contrast(String hex) {
    int r = Integer.parseInt(hex.substring(1, 3), 16), g = Integer.parseInt(hex.substring(3, 5), 16), b = Integer.parseInt(hex.substring(5, 7), 16);
    return (0.299 * r + 0.587 * g + 0.114 * b) > 150 ? "#111111" : "#FFFFFF";
  }

  // ---------------------------------------------------------------- elenco
  private List<Player> squad(World world, Club club, ClubSpec spec) {
    List<Player> out = new ArrayList<>();
    Set<Integer> jerseys = new HashSet<>();
    Map<Position, Integer> depth = new EnumMap<>(Position.class);
    double base = 60 + spec.prestige() * 0.2;
    for (Position pos : ROSTER) {
      int rank = depth.merge(pos, 1, Integer::sum) - 1;
      int offset = rank == 0 ? 5 : rank == 1 ? 0 : rank == 2 ? -5 : -8;
      int star = rank == 0 && rng.nextInt(100) < 12 ? rng.nextInt(3, 10) : 0;
      int target = clamp((int) Math.round(base + offset + star + rng.nextInt(-4, 5)), 45, 97);
      out.add(player(world, club, pos, target, RosterStatus.ACTIVE, jerseys));
    }
    for (Position pos : PRACTICE_SQUAD) {
      int target = clamp((int) Math.round(base - 13 + rng.nextInt(-4, 5)), 40, 75);
      out.add(player(world, club, pos, target, RosterStatus.PRACTICE_SQUAD, jerseys));
    }
    return out;
  }

  private Player player(World world, Club club, Position pos, int target, RosterStatus status, Set<Integer> jerseys) {
    Player p = new Player();
    p.setId(uuid());
    p.setWorld(world); p.setClub(club);
    p.setName(uniqueName());
    p.setNationality(pick(NATIONALITY));
    p.setCollege(pick(COLLEGE_KIND) + " " + pick(COLLEGE_PLACE));
    int age = status == RosterStatus.PRACTICE_SQUAD ? rng.nextInt(21, 26) : age();
    p.setBirthDate(SEASON_REFERENCE_DATE.minusYears(age).minusDays(rng.nextInt(0, 365)));
    p.setPosition(pos);
    p.setSecondaryPosition(rng.nextInt(100) < 30 ? secondary(pos) : null);
    p.setHeightCm(rng.nextInt(pos.minHeightCm() + 3, pos.maxHeightCm() - 2));
    double share = (p.getHeightCm() - pos.minHeightCm()) / (double) (pos.maxHeightCm() - pos.minHeightCm());
    int weight = (int) Math.round(pos.minWeightKg() + 3 + share * (pos.maxWeightKg() - pos.minWeightKg() - 6) + rng.nextInt(-4, 5));
    p.setWeightKg(clamp(weight, pos.minWeightKg() + 1, pos.maxWeightKg() - 1));
    p.setAttributes(attributes(pos, target, age));
    p.setExperienceYears(Math.max(0, age - 22 + rng.nextInt(-1, 2)));
    p.setRosterStatus(status);
    p.setCondition(88 + rng.nextInt(0, 13));
    p.setMorale(50 + rng.nextInt(0, 31));
    p.setJerseyNumber(jersey(pos, jerseys));
    p.recalculate(SEASON_REFERENCE_DATE);
    p.setPotential(potential(age, p.getOverall()));
    p.setContract(contract(pos, p.getOverall(), age, status));
    return p;
  }

  private int age() {
    int[] weights = {6, 9, 10, 10, 10, 9, 9, 8, 7, 6, 5, 4, 3, 2, 1, 1}; // 21..36
    int total = 0; for (int w : weights) total += w;
    int r = rng.nextInt(total);
    for (int i = 0; i < weights.length; i++) { r -= weights[i]; if (r < 0) return 21 + i; }
    return 26;
  }

  private Position secondary(Position pos) {
    return switch (pos) {
      case LT -> Position.RT; case RT -> Position.LT; case LG -> Position.RG; case RG -> Position.LG; case C -> Position.LG;
      case DE -> Position.OLB; case DT -> Position.DE; case OLB -> Position.MLB; case MLB -> Position.OLB;
      case CB -> Position.FS; case FS -> Position.SS; case SS -> Position.FS; case RB -> Position.FB; case FB -> Position.TE;
      case WR -> Position.RB; case TE -> Position.FB; default -> null;
    };
  }

  /**
   * Perfil da posição: o que a posição pesa muito fica acima do alvo, o que pesa pouco perto dele e o que não pesa bem abaixo
   * (físicos e mentais um pouco menos). Depois desloca tudo até o overall bater o alvo (a conta é linear; a saturação em 0/100
   * pode pedir mais de uma volta).
   */
  private PlayerAttributes attributes(Position pos, int target, int age) {
    Map<AttributeKey, Integer> w = OverallWeights.V1.of(pos);
    PlayerAttributes a = new PlayerAttributes();
    for (AttributeKey k : AttributeKey.values()) {
      int weight = w.getOrDefault(k, 0);
      int offset;
      if (weight >= 100) offset = 4;
      else if (weight >= 50) offset = 1;
      else if (weight > 0) offset = -3;
      else if (k.group() == AttributeGroup.PHYSICAL) offset = -8;
      else if (k.group() == AttributeGroup.MENTAL) offset = -6;
      else offset = -24;
      a.with(k, target + offset + rng.nextInt(-5, 6));
    }
    if (age <= 23) { a.with(AttributeKey.AWARENESS, a.getAwareness() - 5); a.with(AttributeKey.SPEED, a.getSpeed() + 2); }
    else if (age >= 32) { a.with(AttributeKey.SPEED, a.getSpeed() - 5); a.with(AttributeKey.AWARENESS, a.getAwareness() + 5); a.with(AttributeKey.LEADERSHIP, a.getLeadership() + 6); }
    for (int i = 0; i < 6; i++) {
      int delta = target - OverallCalculator.overall(a, pos);
      if (delta == 0) break;
      for (AttributeKey k : AttributeKey.values()) a.with(k, a.get(k) + delta);
    }
    return a;
  }

  private int potential(int age, int overall) {
    int bonus = age <= 22 ? rng.nextInt(6, 16) : age <= 25 ? rng.nextInt(3, 9) : age <= 28 ? rng.nextInt(0, 4) : 0;
    return clamp(overall + bonus, overall, 99);
  }

  private int jersey(Position pos, Set<Integer> used) {
    int[][] ranges = switch (pos.group()) {
      case QB, K, P -> new int[][] {{1, 19}};
      case RB -> new int[][] {{20, 49}};
      case FB -> new int[][] {{30, 49}};
      case WR -> new int[][] {{10, 19}, {80, 89}};
      case TE -> new int[][] {{80, 89}, {40, 49}};
      case OL -> new int[][] {{50, 79}};
      case DL -> new int[][] {{90, 99}, {50, 79}};
      case LB -> new int[][] {{40, 59}, {90, 99}};
      case CB -> new int[][] {{20, 39}};
      case S -> new int[][] {{20, 49}};
      case LS -> new int[][] {{40, 59}};
    };
    for (int attempt = 0; attempt < 40; attempt++) {
      int[] r = ranges[rng.nextInt(ranges.length)];
      int n = rng.nextInt(r[0], r[1] + 1);
      if (used.add(n)) return n;
    }
    for (int n = 0; n <= 99; n++) if (used.add(n)) return n;
    throw new IllegalStateException("sem número de camisa livre");
  }

  /** Salário por ano em centavos: mínimo + curva cúbica do overall até o teto de referência do grupo, com aumento anual. */
  private PlayerContract contract(Position pos, int overall, int age, RosterStatus status) {
    if (status == RosterStatus.PRACTICE_SQUAD) {
      return PlayerContract.builder().startSeason(BASE_YEAR).baseSalaryByYear(List.of(PRACTICE_SALARY * 100L)).type(ContractType.PRACTICE).build();
    }
    double x = Math.max(0, (overall - 55) / 40.0);
    long top = TOP_SALARY.get(pos.group());
    long yearOne = MIN_SALARY + Math.round((top - MIN_SALARY) * x * x * x);
    boolean rookie = age <= 24;
    int years = rookie ? 4 : 1 + rng.nextInt(0, age >= 31 ? 2 : 5);
    List<Long> salaries = new ArrayList<>();
    for (int y = 0; y < years; y++) salaries.add(Math.round(yearOne * Math.pow(1.05, y)) * 100L);
    long total = salaries.stream().mapToLong(Long::longValue).sum();
    long bonus = overall >= 75 ? Math.round(total * 0.25 / 100.0) * 100L : overall >= 65 ? Math.round(total * 0.08 / 100.0) * 100L : 0L;
    long guaranteed = overall >= 80 ? salaries.get(0) : 0L;
    ContractType type = rookie ? ContractType.ROOKIE : yearOne <= MIN_SALARY * 11 / 10 ? ContractType.MINIMUM : ContractType.VETERAN;
    return PlayerContract.builder().startSeason(BASE_YEAR).baseSalaryByYear(salaries).signingBonus(bonus).guaranteed(guaranteed).type(type).build();
  }

  /**
   * Teto rígido: se a soma dos cap hits do primeiro ano passar de 95 % do teto, reduz salários e bônus dos contratos não mínimos na
   * mesma proporção; se ficar abaixo do piso, aumenta até um quarto do caminho entre o piso e o limite. O practice squad entra na conta e não é ajustado.
   */
  static void fitToCap(List<Player> squad, League league) {
    int prorate = league.getRules().getBonusProrationMaxYears();
    long fixed = 0, adjustable = 0;
    for (Player p : squad) {
      long hit = p.getContract().capHit(0, prorate);
      if (p.getContract().getType() == ContractType.PRACTICE || p.getContract().getType() == ContractType.MINIMUM) fixed += hit;
      else adjustable += hit;
    }
    long ceiling = league.getSalaryCap() * 95 / 100;
    long floor = league.getSalaryFloor();
    double factor = 1.0;
    if (fixed + adjustable > ceiling && adjustable > 0) factor = (ceiling - fixed) / (double) adjustable;
    else if (fixed + adjustable < floor && adjustable > 0) factor = (floor + (ceiling - floor) / 4 - fixed) / (double) adjustable;
    if (factor == 1.0) return;
    for (Player p : squad) {
      PlayerContract c = p.getContract();
      if (c.getType() == ContractType.PRACTICE || c.getType() == ContractType.MINIMUM) continue;
      List<Long> scaled = new ArrayList<>();
      for (Long s : c.getBaseSalaryByYear()) scaled.add(Math.max(MIN_SALARY * 100L, (long) Math.floor(s * factor / 100.0) * 100L));
      p.setContract(c.toBuilder().baseSalaryByYear(scaled).signingBonus((long) Math.floor(c.getSigningBonus() * factor / 100.0) * 100L)
          .guaranteed(Math.min((long) Math.floor(c.getGuaranteed() * factor / 100.0) * 100L, scaled.get(0))).build());
    }
  }

  /** Média do overall dos 22 melhores do elenco ativo (titulares de ataque e defesa). */
  private static double starterStrength(List<Player> squad) {
    return squad.stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE).map(Player::getOverall).sorted(Comparator.reverseOrder())
        .limit(22).mapToInt(Integer::intValue).average().orElse(0);
  }

  // ---------------------------------------------------------------- utilitários
  private String uniqueName() {
    for (int i = 0; i < 200; i++) {
      String name = pick(FIRST) + " " + pick(SURNAME_HEAD) + pick(SURNAME_TAIL);
      if (usedNames.add(name)) return name;
    }
    throw new IllegalStateException("combinações de nome esgotadas");
  }

  private String pick(String[] arr) { return arr[rng.nextInt(arr.length)]; }

  /** UUID v4 derivado do RNG (determinístico). */
  private UUID uuid() {
    long msb = rng.nextLong(), lsb = rng.nextLong();
    msb = (msb & 0xffffffffffff0fffL) | 0x0000000000004000L;
    lsb = (lsb & 0x3fffffffffffffffL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }

  private static List<Position> expand(Map<Position, Integer> counts) {
    List<Position> out = new ArrayList<>();
    for (Position p : Position.values()) for (int i = 0; i < counts.getOrDefault(p, 0); i++) out.add(p);
    return List.copyOf(out);
  }

  private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
