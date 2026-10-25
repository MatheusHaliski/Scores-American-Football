/**
 * Carreira no modo mock (bloco 6a), com as regras do backend: até 5 carreiras ativas por slot, arquivar e reativar, excluir só com
 * o nome exato, nova carreira no mundo padrão (32 franquias, calendário de 18 semanas derivado do golden), central da franquia,
 * elenco de 53 + practice squad + IR com movimentos e dispensa (dead money no teto) e depth chart com versão otimista (409) e
 * validação (422). Estado por usuário em localStorage, com cópia em memória (Node e modo privado).
 */
import { macrosForPosition, FAMILY_OF } from "@/lib/ratings/macros";
import { overall, overallByPosition, type Position } from "@/lib/ratings/overall";
import { cardTier } from "@/lib/ratings/tier";
import { ApiError } from "../problem";
import type {
  CalendarWeek,
  CapSummary,
  CareerDashboard,
  CareerList,
  CareerSummary,
  ClubOption,
  ClubRef,
  CoachStyle,
  CreateCareerRequest,
  DepthChart,
  DepthUnit,
  Fixture,
  LeagueClubs,
  LeagueSummary,
  MovableStatus,
  PlayerCard,
  PlayerDetail,
  ReleaseResult,
  Roster,
  RosterChange,
  RosterCounts,
  RosterMove,
  RosterStatus,
  SaveDepthChartRequest,
  SeasonCalendar,
  SetRoleRequest,
  StandingRow,
  TeamRecord,
  UpdateCareerRequest,
  WorldSummary,
} from "../career-types";
import { CAREER_NAME, COACH_NAME, COACH_STYLES, DIFFICULTIES, MAX_ACTIVE_SLOTS, MAX_DEPTH, MOVABLE_STATUSES, UNITS } from "../career-types";
import type { Difficulty, UserAccount } from "../types";
import { chartLabels, defaultChart, emptyChart, removeFromChart, SLOT_BY_KEY, SLOTS, validate, available, overallAt, outOfPosition, type Chart } from "./depth";
import { capHitOf, deadMoneyOf, generateRoster, UNIT_OF, type MockPlayer } from "./players";
import { uuidFrom } from "./rng";
import { CLUBS, CONFERENCES, FIXTURES, KICKOFF_SLOTS, LEAGUE_NAME, LEAGUE_SHORT, REGULAR_WEEKS, SALARY_CAP, SEASON_START, SEASON_YEAR, WORLD_NAME } from "./world-data";

const RULES = { rosterMax: 53, gameDayActives: 46, practiceSquadMax: 16, irMinWeeks: 4, irReturnsPerSeason: 8 } as const;
const PS_MAX_EXPERIENCE = 3;
const PS_VETERAN_SLOTS = 6;

export const DEFAULT_WORLD_ID = uuidFrom("world", "default");
export const DEFAULT_LEAGUE_ID = uuidFrom("league", LEAGUE_SHORT);
const clubId = (short: string) => uuidFrom("club", short);
const confId = (short: string) => uuidFrom("conference", short);
const divId = (name: string) => uuidFrom("division", name);

interface WorldClub {
  index: number;
  id: string;
  shortName: string;
  name: string;
  city: string;
  primaryColor: string;
  secondaryColor: string;
  conference: string;
  division: string;
  prestige: number;
}

const WORLD_CLUBS: WorldClub[] = CLUBS.map(([shortName, name, city, primaryColor, secondaryColor, , conference, division, prestige], index) => ({
  index,
  id: clubId(shortName),
  shortName,
  name,
  city,
  primaryColor,
  secondaryColor,
  conference,
  division,
  prestige,
}));
const CLUB_BY_ID = new Map(WORLD_CLUBS.map((c) => [c.id, c]));

interface RawFixture {
  week: number;
  home: number;
  away: number;
  day: number;
  slot: number;
}
const RAW_FIXTURES: RawFixture[] = FIXTURES.split(" ").map((s) => {
  const [week, home, away, day, slot] = s.split(".").map(Number);
  return { week, home, away, day, slot };
});

function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
const weekStart = (week: number) => addDays(SEASON_START, (week - 1) * 7);

/* ───────────────────────── estado ───────────────────────── */

interface StoredCareer {
  summary: Omit<CareerSummary, "record">;
  clubIndex: number;
  coachStyle: CoachStyle;
  players: MockPlayer[];
  chart: Chart;
  chartVersion: number;
  chartUpdatedAt: string;
  moves: (RosterMove & { playerId: string; capHitRemoved: number })[];
}

interface UserStore {
  careers: StoredCareer[];
}

const memory = new Map<string, UserStore>();
const key = (userId: string) => `scoresaf.mock.careers.${userId}`;

function load(userId: string): UserStore {
  const cached = memory.get(userId);
  if (cached) return cached;
  let st: UserStore = { careers: [] };
  try {
    const raw = typeof localStorage === "undefined" ? null : localStorage.getItem(key(userId));
    if (raw) st = JSON.parse(raw) as UserStore;
  } catch {
    /* armazenamento indisponível */
  }
  memory.set(userId, st);
  return st;
}

function save(userId: string, st: UserStore): void {
  memory.set(userId, st);
  try {
    localStorage.setItem(key(userId), JSON.stringify(st));
  } catch {
    /* modo privado ou cota: fica só em memória */
  }
}

/* ───────────────────────── leituras derivadas ───────────────────────── */

const ZERO: TeamRecord = { wins: 0, losses: 0, ties: 0, summary: "0-0" };

function clubRef(c: WorldClub): ClubRef {
  return { id: c.id, name: c.name, shortName: c.shortName, city: c.city, primaryColor: c.primaryColor, secondaryColor: c.secondaryColor, conference: c.conference, division: c.division };
}

function fixtures(careerId: string, userClub: WorldClub): Fixture[] {
  return RAW_FIXTURES.map((f, i) => {
    const home = WORLD_CLUBS[f.home];
    const away = WORLD_CLUBS[f.away];
    return {
      id: uuidFrom("fixture", `${careerId}:${i}`),
      week: f.week,
      date: addDays(weekStart(f.week), f.day),
      kickoffSlot: KICKOFF_SLOTS[f.slot],
      status: "SCHEDULED",
      homeClubId: home.id,
      awayClubId: away.id,
      homeScore: null,
      awayScore: null,
      divisional: home.division === away.division,
      conferenceGame: home.conference === away.conference,
      userGame: home.id === userClub.id || away.id === userClub.id,
    };
  });
}

function summaryOf(c: StoredCareer): CareerSummary {
  return { ...c.summary, record: { ...ZERO } };
}

function playersById(c: StoredCareer): Map<string, MockPlayer> {
  return new Map(c.players.map((p) => [p.id, p]));
}

function club(c: StoredCareer): MockPlayer[] {
  return c.players.filter((p) => p.onClub);
}

function deadMoney(c: StoredCareer): number {
  return c.moves.filter((m) => m.kind === "RELEASE").reduce((a, m) => a + m.deadMoney, 0);
}

function capOf(c: StoredCareer): CapSummary {
  const hits = club(c).reduce((a, p) => a + capHitOf(p, SEASON_YEAR), 0);
  const dead = deadMoney(c);
  return { salaryCap: SALARY_CAP, capUsed: hits + dead, deadMoney: dead, capSpace: SALARY_CAP - hits - dead };
}

function countsOf(c: StoredCareer): RosterCounts {
  const ps = club(c);
  const n = (s: RosterStatus) => ps.filter((p) => p.rosterStatus === s).length;
  const irReturnsUsed = c.moves.filter((m) => m.fromStatus === "IR" && (m.toStatus === "ACTIVE" || m.toStatus === "INACTIVE")).length;
  return { roster: n("ACTIVE") + n("INACTIVE"), active: n("ACTIVE"), inactive: n("INACTIVE"), practiceSquad: n("PRACTICE_SQUAD"), injuredReserve: n("IR"), irReturnsUsed };
}

function card(p: MockPlayer, labels: string[]): PlayerCard {
  const ov = overall(p.attributes, p.position);
  return {
    id: p.id,
    name: p.name,
    jerseyNumber: p.jerseyNumber,
    position: p.position,
    secondaryPosition: p.secondaryPosition,
    unit: UNIT_OF[p.position],
    age: p.age,
    heightCm: p.heightCm,
    weightKg: p.weightKg,
    overall: ov,
    tier: cardTier(ov),
    family: FAMILY_OF[p.position],
    macros: macrosForPosition(p.attributes, p.position),
    rosterStatus: p.rosterStatus,
    condition: p.condition,
    morale: p.morale,
    experienceYears: p.experienceYears,
    injuryWeeksOut: p.injuryWeeksOut,
    suspensionWeeks: p.suspensionWeeks,
    capHit: p.onClub ? capHitOf(p, SEASON_YEAR) : 0,
    contractEndSeason: p.contract.endSeason,
    contractType: p.contract.type,
    depthChart: labels,
  };
}

const POSITION_ORDER: Position[] = ["QB", "RB", "FB", "WR", "TE", "LT", "LG", "C", "RG", "RT", "DE", "DT", "OLB", "MLB", "CB", "FS", "SS", "K", "P", "LS"];
const rosterOrder = (a: MockPlayer, b: MockPlayer) =>
  POSITION_ORDER.indexOf(a.position) - POSITION_ORDER.indexOf(b.position) || overall(b.attributes, b.position) - overall(a.attributes, a.position) || a.jerseyNumber - b.jerseyNumber;

function depthChartView(c: StoredCareer): DepthChart {
  const byId = playersById(c);
  const v = validate(c.chart, byId, RULES.gameDayActives);
  const units: DepthUnit[] = UNITS.map((unit) => ({
    unit,
    slots: SLOTS.filter((s) => s.unit === unit).map((s) => ({
      slot: s.slot,
      position: s.position,
      role: !s.position,
      minStarters: s.minStarters,
      players: (c.chart[s.slot] ?? []).flatMap((id, i) => {
        const p = byId.get(id);
        if (!p) return [];
        const natural = overall(p.attributes, p.position);
        return [{ depth: i + 1, playerId: p.id, name: p.name, jerseyNumber: p.jerseyNumber, position: p.position, overallAtSlot: overallAt(p, s), overall: natural, tier: cardTier(natural), outOfPosition: outOfPosition(p, s), available: available(p), rosterStatus: p.rosterStatus }];
      }),
    })),
  }));
  return { id: uuidFrom("depth-chart", c.summary.id), clubId: WORLD_CLUBS[c.clubIndex].id, version: c.chartVersion, valid: v.errors.length === 0, updatedAt: c.chartUpdatedAt, gameDayActives: RULES.gameDayActives, uniquePlayers: v.uniquePlayers, units, errors: v.errors, warnings: v.warnings };
}

/* ───────────────────────── validação da criação ───────────────────────── */

function careerName(raw: string | undefined | null): string {
  const n = (raw ?? "").trim().replace(/\s+/g, " ");
  if (n.length < CAREER_NAME.min || n.length > CAREER_NAME.max || /[<>]/.test(n)) throw ApiError.of(422, "CAREER_NAME_INVALID", { 0: CAREER_NAME.min, 1: CAREER_NAME.max });
  return n;
}

function coachName(raw: string): string {
  const n = raw.trim().replace(/\s+/g, " ");
  if (n.length < COACH_NAME.min || n.length > COACH_NAME.max || /[<>]/.test(n)) throw ApiError.of(422, "COACH_NAME_INVALID", { 0: COACH_NAME.min, 1: COACH_NAME.max });
  return n;
}

function nextFreeSlot(used: Set<number>): number | null {
  for (let s = 1; s <= MAX_ACTIVE_SLOTS; s++) if (!used.has(s)) return s;
  return null;
}

function listOf(st: UserStore): CareerList {
  const active = st.careers.filter((c) => c.summary.status === "ACTIVE").sort((a, b) => (a.summary.slot ?? 99) - (b.summary.slot ?? 99));
  const archived = st.careers.filter((c) => c.summary.status !== "ACTIVE").sort((a, b) => a.summary.createdAt.localeCompare(b.summary.createdAt));
  const used = new Set(active.map((c) => c.summary.slot ?? 0));
  return {
    maxActiveSlots: MAX_ACTIVE_SLOTS,
    activeCount: active.length,
    freeSlots: [1, 2, 3, 4, 5].filter((s) => !used.has(s)),
    careers: [...active, ...archived].map(summaryOf),
  };
}

/* ───────────────────────── fachada ───────────────────────── */

export interface MockCareers {
  worlds(): WorldSummary[];
  leagues(worldId: string): LeagueSummary[];
  leagueClubs(worldId: string, leagueId: string): LeagueClubs;
  list(): CareerList;
  create(req: CreateCareerRequest): CareerSummary;
  get(id: string): CareerSummary;
  update(id: string, req: UpdateCareerRequest): CareerSummary;
  remove(id: string, confirm: string): void;
  dashboard(id: string): CareerDashboard;
  calendar(id: string, week?: number): SeasonCalendar;
  roster(id: string): Roster;
  player(id: string, playerId: string): PlayerDetail;
  changeStatus(id: string, playerId: string, status: MovableStatus): RosterChange;
  release(id: string, playerId: string): ReleaseResult;
  depthChart(id: string): DepthChart;
  saveDepthChart(id: string, req: SaveDepthChartRequest): DepthChart;
  setRole(id: string, playerId: string, req: SetRoleRequest): DepthChart;
}

export function mockCareers(user: UserAccount): MockCareers {
  const st = load(user.id);
  const commit = () => save(user.id, st);

  const find = (id: string): StoredCareer => {
    const c = st.careers.find((x) => x.summary.id === id);
    if (!c) throw ApiError.notFound("career");
    return c;
  };
  const writable = (id: string): StoredCareer => {
    const c = find(id);
    if (c.summary.status !== "ACTIVE") throw ApiError.of(422, "CAREER_ARCHIVED");
    return c;
  };
  const clubPlayer = (c: StoredCareer, playerId: string): MockPlayer => {
    const p = c.players.find((x) => x.id === playerId && x.onClub);
    if (!p) throw ApiError.of(422, "PLAYER_NOT_IN_ROSTER");
    return p;
  };
  const touch = (c: StoredCareer) => {
    c.summary.lastPlayedAt = new Date().toISOString();
  };
  const record = (c: StoredCareer, p: MockPlayer, kind: string, from: RosterStatus, to: RosterStatus, dead: number, hit: number) => {
    c.moves.push({ id: uuidFrom("move", `${c.summary.id}:${c.moves.length}:${p.id}`), week: c.summary.currentWeek, kind, fromStatus: from, toStatus: to, deadMoney: dead, at: new Date().toISOString(), playerId: p.id, capHitRemoved: hit });
  };

  return {
    worlds: () => [{ id: DEFAULT_WORLD_ID, name: WORLD_NAME, description: null, kind: "TEMPLATE", defaultTemplate: true, leagues: 1, clubs: WORLD_CLUBS.length, players: WORLD_CLUBS.length * 69 }],
    leagues(worldId) {
      if (worldId !== DEFAULT_WORLD_ID) throw ApiError.notFound("world");
      return [{ id: DEFAULT_LEAGUE_ID, worldId, name: LEAGUE_NAME, shortName: LEAGUE_SHORT, ruleset: "PRO", teamCount: WORLD_CLUBS.length, conferenceCount: CONFERENCES.length, divisionsPerConference: 4, salaryCap: SALARY_CAP, prestige: 85, structureValid: true }];
    },
    leagueClubs(worldId, leagueId) {
      if (worldId !== DEFAULT_WORLD_ID) throw ApiError.notFound("world");
      if (leagueId !== DEFAULT_LEAGUE_ID) throw ApiError.of(422, "LEAGUE_NOT_IN_WORLD");
      const option = (c: WorldClub): ClubOption => ({ id: c.id, name: c.name, shortName: c.shortName, city: c.city, primaryColor: c.primaryColor, secondaryColor: c.secondaryColor, prestige: c.prestige, expectedWins: Math.round(3 + ((c.prestige - 56) / 28) * 10), stadiumName: `Arena ${c.city}` });
      return {
        leagueId,
        conferences: CONFERENCES.map((conf) => ({
          id: confId(conf.shortName),
          name: conf.name,
          shortName: conf.shortName,
          divisions: conf.divisions.map((d) => ({ id: divId(d), name: d, clubs: WORLD_CLUBS.filter((c) => c.division === d).map(option) })),
        })),
        unassigned: [],
      };
    },
    list: () => listOf(st),
    create(req) {
      const name = careerName(req.name);
      if (st.careers.some((c) => c.summary.name.toLowerCase() === name.toLowerCase())) throw ApiError.conflict("CAREER_NAME_TAKEN", { 0: name });
      if (req.worldId && req.worldId !== DEFAULT_WORLD_ID) throw ApiError.notFound("world");
      if (req.leagueId && req.leagueId !== DEFAULT_LEAGUE_ID) throw ApiError.of(422, "LEAGUE_NOT_IN_WORLD");
      if (!req.clubId) throw ApiError.of(422, "CLUB_REQUIRED");
      const wc = CLUB_BY_ID.get(req.clubId);
      if (!wc) throw ApiError.of(422, "CLUB_NOT_IN_LEAGUE");
      const style = req.coachStyle ?? "OFFENSIVE";
      if (!COACH_STYLES.includes(style)) throw ApiError.of(422, "INVALID_COACH_STYLE", { 0: String(style) });
      const difficulty = (req.difficulty ?? "NORMAL") as Difficulty;
      if (!DIFFICULTIES.includes(difficulty)) throw ApiError.of(422, "INVALID_DIFFICULTY", { 0: String(difficulty) });
      const coach = coachName(req.coachName?.trim() ? req.coachName : user.displayName);
      const nationality = (req.coachNationality?.trim() || "BRA").toUpperCase();
      if (!/^[A-Z]{3}$/.test(nationality)) throw ApiError.of(422, "COACH_NATIONALITY_INVALID", { 0: nationality });
      const active = st.careers.filter((c) => c.summary.status === "ACTIVE");
      if (active.length >= MAX_ACTIVE_SLOTS) throw ApiError.of(422, "CAREER_SLOTS_FULL", { 0: MAX_ACTIVE_SLOTS });
      const used = new Set(active.map((c) => c.summary.slot ?? 0));
      if (req.slot != null && used.has(req.slot)) throw ApiError.conflict("CAREER_SLOT_TAKEN", { 0: req.slot });
      const slot = req.slot ?? nextFreeSlot(used);
      const seed = req.seed ?? Math.floor(Math.random() * 1_000_000_000);
      const id = uuidFrom("career", `${user.id}:${name}:${Date.now()}:${seed}`);
      const now = new Date().toISOString();
      const players = generateRoster(`${id}:${seed}`, (i) => uuidFrom("player", `${id}:${i}`), wc.prestige, SEASON_YEAR, SALARY_CAP);
      const c: StoredCareer = {
        summary: {
          id,
          name,
          slot,
          status: "ACTIVE",
          difficulty,
          seed,
          currentWeek: 1,
          employmentStatus: "EMPLOYED",
          worldId: uuidFrom("world", id),
          sourceWorldId: DEFAULT_WORLD_ID,
          sourceWorldName: WORLD_NAME,
          leagueId: DEFAULT_LEAGUE_ID,
          leagueName: LEAGUE_NAME,
          ruleset: "PRO",
          clubId: wc.id,
          clubName: wc.name,
          clubShortName: wc.shortName,
          clubCity: wc.city,
          clubPrimaryColor: wc.primaryColor,
          clubSecondaryColor: wc.secondaryColor,
          coachId: uuidFrom("coach", id),
          coachName: coach,
          coachNationality: nationality,
          coachStyle: style,
          seasonId: uuidFrom("season", id),
          seasonYear: SEASON_YEAR,
          seasonPhase: "REGULAR",
          regularSeasonWeeks: REGULAR_WEEKS,
          createdAt: now,
          lastPlayedAt: now,
          version: 0,
        },
        clubIndex: wc.index,
        coachStyle: style,
        players,
        chart: emptyChart(),
        chartVersion: 0,
        chartUpdatedAt: now,
        moves: [],
      };
      c.chart = defaultChart(players, RULES.gameDayActives);
      st.careers.push(c);
      commit();
      return summaryOf(c);
    },
    get: (id) => summaryOf(find(id)),
    update(id, req) {
      const c = find(id);
      if (req.version != null && req.version !== c.summary.version) throw new ApiError({ status: 409, code: "CAREER_VERSION_CONFLICT", message: "", details: { currentVersion: c.summary.version, expectedVersion: req.version }, correlationId: null, locale: null, params: { 0: req.version, 1: c.summary.version } });
      const edits = !!req.name?.trim() || !!req.difficulty;
      if (edits && c.summary.status !== "ACTIVE" && req.status !== "ACTIVE") throw ApiError.of(422, "CAREER_ARCHIVED");
      if (req.status && !["ACTIVE", "ARCHIVED"].includes(req.status)) throw ApiError.of(422, "INVALID_CAREER_STATUS", { 0: req.status });
      if (req.status === "ACTIVE" && c.summary.status !== "ACTIVE") {
        const active = st.careers.filter((x) => x.summary.status === "ACTIVE");
        if (active.length >= MAX_ACTIVE_SLOTS) throw ApiError.of(422, "CAREER_SLOTS_FULL", { 0: MAX_ACTIVE_SLOTS });
        c.summary.status = "ACTIVE";
        c.summary.slot = nextFreeSlot(new Set(active.map((x) => x.summary.slot ?? 0)));
      }
      if (req.name?.trim()) {
        const name = careerName(req.name);
        if (st.careers.some((o) => o !== c && o.summary.name.toLowerCase() === name.toLowerCase())) throw ApiError.conflict("CAREER_NAME_TAKEN", { 0: name });
        c.summary.name = name;
      }
      if (req.difficulty) {
        if (!DIFFICULTIES.includes(req.difficulty)) throw ApiError.of(422, "INVALID_DIFFICULTY", { 0: req.difficulty });
        c.summary.difficulty = req.difficulty;
      }
      if (req.status === "ARCHIVED" && c.summary.status === "ACTIVE") {
        c.summary.status = "ARCHIVED";
        c.summary.slot = null;
      }
      c.summary.version += 1;
      commit();
      return summaryOf(c);
    },
    remove(id, confirm) {
      const c = find(id);
      if (!confirm || confirm.trim() !== c.summary.name) throw ApiError.of(422, "CAREER_CONFIRMATION_REQUIRED");
      st.careers = st.careers.filter((x) => x !== c);
      commit();
    },
    dashboard(id) {
      const c = find(id);
      const wc = WORLD_CLUBS[c.clubIndex];
      const week = c.summary.currentWeek;
      const all = fixtures(c.summary.id, wc).filter((f) => f.userGame);
      const next = all.find((f) => f.week >= week) ?? null;
      const division: StandingRow[] = WORLD_CLUBS.filter((x) => x.division === wc.division)
        .sort((a, b) => b.prestige - a.prestige || a.name.localeCompare(b.name))
        .map((x, i) => ({ rank: i + 1, club: clubRef(x), record: { ...ZERO }, winPct: 0, pointsFor: 0, pointsAgainst: 0, userClub: x.id === wc.id }));
      const mine = club(c);
      const v = validate(c.chart, playersById(c), RULES.gameDayActives);
      const opponent = next ? CLUB_BY_ID.get(next.homeClubId === wc.id ? next.awayClubId : next.homeClubId)! : null;
      return {
        career: summaryOf(c),
        club: clubRef(wc),
        coach: { id: c.summary.coachId!, name: c.summary.coachName!, nationality: c.summary.coachNationality!, style: c.coachStyle, overall: 50, reputationLeague: 50, careerGames: 0, careerWins: 0, careerTitles: 0 },
        currentWeek: week,
        nextGame: next && opponent ? { fixture: next, home: next.homeClubId === wc.id, opponent: clubRef(opponent), opponentRecord: { ...ZERO } } : null,
        byeWeek: !all.some((f) => f.week === week),
        record: { ...ZERO },
        divisionRank: division.find((r) => r.userClub)?.rank ?? null,
        division,
        upcomingDivisionGames: all.filter((f) => f.week >= week && f.divisional).slice(0, 3),
        rosterHighlights: mine
          .filter((p) => p.rosterStatus === "ACTIVE" || p.rosterStatus === "INACTIVE")
          .map((p) => ({ p, ov: overall(p.attributes, p.position) }))
          .sort((a, b) => b.ov - a.ov || a.p.jerseyNumber - b.p.jerseyNumber)
          .slice(0, 5)
          .map(({ p, ov }) => ({ id: p.id, name: p.name, position: p.position, jerseyNumber: p.jerseyNumber, overall: ov, tier: cardTier(ov) })),
        injuries: mine.filter((p) => p.injuryWeeksOut > 0).map((p) => ({ playerId: p.id, name: p.name, position: p.position, rosterStatus: p.rosterStatus, weeksOut: p.injuryWeeksOut })),
        cap: capOf(c),
        depthChart: { version: c.chartVersion, valid: v.errors.length === 0, errors: v.errors.length, warnings: v.warnings.length, codes: [...new Set([...v.errors, ...v.warnings].map((i) => i.code))] },
        live: null,
        morale: null,
        analysis: null,
        inbox: null,
        unreadInbox: null,
        scoresPoints: null,
      };
    },
    calendar(id, week) {
      const c = find(id);
      const wc = WORLD_CLUBS[c.clubIndex];
      if (week != null && (!Number.isInteger(week) || week < 1 || week > REGULAR_WEEKS)) throw ApiError.badRequest("INVALID_WEEK", { 0: week, 1: REGULAR_WEEKS });
      const fx = fixtures(c.summary.id, wc);
      const weeks: CalendarWeek[] = [];
      for (let n = 1; n <= REGULAR_WEEKS; n++) {
        if (week != null && n !== week) continue;
        const games = fx.filter((f) => f.week === n);
        const playing = new Set(games.flatMap((f) => [f.homeClubId, f.awayClubId]));
        const byes = WORLD_CLUBS.filter((x) => !playing.has(x.id)).map((x) => x.id);
        weeks.push({ number: n, kind: "REGULAR", startsOn: weekStart(n), status: n < c.summary.currentWeek ? "COMPLETED" : n === c.summary.currentWeek ? "CURRENT" : "UPCOMING", userBye: byes.includes(wc.id), byeClubIds: byes, fixtures: games });
      }
      return { seasonId: c.summary.seasonId!, seasonYear: SEASON_YEAR, phase: "REGULAR", currentWeek: c.summary.currentWeek, regularSeasonWeeks: REGULAR_WEEKS, gamesPerTeam: 17, userClubId: wc.id, clubs: WORLD_CLUBS.map(clubRef), weeks };
    },
    roster(id) {
      const c = find(id);
      const labels = chartLabels(c.chart);
      const mine = club(c).sort(rosterOrder);
      const cardOf = (p: MockPlayer) => card(p, labels.get(p.id) ?? []);
      const inRoster = mine.filter((p) => p.rosterStatus === "ACTIVE" || p.rosterStatus === "INACTIVE");
      return {
        clubId: WORLD_CLUBS[c.clubIndex].id,
        clubName: WORLD_CLUBS[c.clubIndex].name,
        seasonYear: SEASON_YEAR,
        currentWeek: c.summary.currentWeek,
        rules: { ...RULES },
        counts: countsOf(c),
        cap: capOf(c),
        units: { OFFENSE: inRoster.filter((p) => UNIT_OF[p.position] === "OFFENSE").map(cardOf), DEFENSE: inRoster.filter((p) => UNIT_OF[p.position] === "DEFENSE").map(cardOf), SPECIAL_TEAMS: inRoster.filter((p) => UNIT_OF[p.position] === "SPECIAL_TEAMS").map(cardOf) },
        practiceSquad: mine.filter((p) => p.rosterStatus === "PRACTICE_SQUAD").map(cardOf),
        injuredReserve: mine.filter((p) => p.rosterStatus === "IR").map(cardOf),
      };
    },
    player(id, playerId) {
      const c = find(id);
      const p = c.players.find((x) => x.id === playerId);
      if (!p) throw ApiError.notFound("player");
      const wc = WORLD_CLUBS[c.clubIndex];
      const history = c.moves.filter((m) => m.playerId === p.id);
      const irMove = [...history].reverse().find((m) => m.toStatus === "IR");
      const irWeek = p.rosterStatus === "IR" ? (irMove?.week ?? null) : null;
      return {
        card: card(p, p.onClub ? (chartLabels(c.chart).get(p.id) ?? []) : []),
        clubId: p.onClub ? wc.id : null,
        clubName: p.onClub ? wc.name : null,
        ownPlayer: p.onClub,
        nationality: p.nationality,
        college: p.college,
        birthDate: p.birthDate,
        potential: p.potential,
        attributes: p.attributes as unknown as Record<string, Record<string, number>>,
        overallByPosition: overallByPosition(p.attributes),
        contract: { ...p.contract },
        deadMoneyIfReleased: p.onClub ? deadMoneyOf(p, SEASON_YEAR) : 0,
        irPlacedWeek: irWeek,
        irEarliestReturnWeek: irWeek == null ? null : irWeek + RULES.irMinWeeks,
        history: history.map((m) => ({ id: m.id, week: m.week, kind: m.kind, fromStatus: m.fromStatus, toStatus: m.toStatus, deadMoney: m.deadMoney, at: m.at })),
      };
    },
    changeStatus(id, playerId, status) {
      const c = writable(id);
      if (!MOVABLE_STATUSES.includes(status)) throw ApiError.badRequest("INVALID_ROSTER_STATUS", { 0: String(status) });
      const p = clubPlayer(c, playerId);
      const from = p.rosterStatus;
      if (from !== status) {
        const counts = countsOf(c);
        const notAllowed = () => ApiError.of(422, "ROSTER_MOVE_NOT_ALLOWED", { 0: from, 1: status });
        const inRoster = (s: RosterStatus) => s === "ACTIVE" || s === "INACTIVE";
        if (!MOVABLE_STATUSES.includes(from as MovableStatus)) throw notAllowed();
        if (inRoster(status)) {
          if (from === "IR") {
            const irMove = [...c.moves].reverse().find((m) => m.playerId === p.id && m.toStatus === "IR");
            const weeksOnIr = c.summary.currentWeek - (irMove?.week ?? 0);
            if (weeksOnIr < RULES.irMinWeeks) throw ApiError.of(422, "IR_RETURN_TOO_EARLY", { 0: RULES.irMinWeeks, 1: weeksOnIr });
            if (p.injuryWeeksOut > 0) throw ApiError.of(422, "IR_STILL_INJURED", { 0: p.injuryWeeksOut });
            if (counts.irReturnsUsed >= RULES.irReturnsPerSeason) throw ApiError.of(422, "IR_RETURNS_EXHAUSTED", { 0: RULES.irReturnsPerSeason });
          }
          if (!inRoster(from) && counts.roster >= RULES.rosterMax) throw ApiError.of(422, "ROSTER_FULL", { 0: RULES.rosterMax });
        } else if (status === "PRACTICE_SQUAD") {
          if (from === "IR") throw notAllowed();
          if (counts.practiceSquad >= RULES.practiceSquadMax) throw ApiError.of(422, "PRACTICE_SQUAD_FULL", { 0: RULES.practiceSquadMax });
          const vets = club(c).filter((x) => x.rosterStatus === "PRACTICE_SQUAD" && x.experienceYears > PS_MAX_EXPERIENCE).length;
          if (p.experienceYears > PS_MAX_EXPERIENCE && vets >= PS_VETERAN_SLOTS) throw ApiError.of(422, "PRACTICE_SQUAD_INELIGIBLE", { 0: PS_MAX_EXPERIENCE, 1: PS_VETERAN_SLOTS });
        } else if (status === "IR") {
          if (from === "PRACTICE_SQUAD") throw notAllowed();
          if (p.injuryWeeksOut < RULES.irMinWeeks) throw ApiError.of(422, "IR_NOT_ALLOWED", { 0: RULES.irMinWeeks });
        }
        p.rosterStatus = status;
        record(c, p, "STATUS_CHANGE", from, status, 0, 0);
        if (status !== "ACTIVE" && removeFromChart(c.chart, p.id)) c.chartVersion += 1;
        touch(c);
        commit();
      }
      return { player: card(p, chartLabels(c.chart).get(p.id) ?? []), counts: countsOf(c), cap: capOf(c) };
    },
    release(id, playerId) {
      const c = writable(id);
      const p = clubPlayer(c, playerId);
      const dead = deadMoneyOf(p, SEASON_YEAR);
      const hit = capHitOf(p, SEASON_YEAR);
      const from = p.rosterStatus;
      p.rosterStatus = "WAIVED";
      p.onClub = false;
      record(c, p, "RELEASE", from, "WAIVED", dead, hit);
      if (removeFromChart(c.chart, p.id)) c.chartVersion += 1;
      touch(c);
      commit();
      return { playerId: p.id, name: p.name, rosterStatus: "WAIVED", deadMoney: dead, capHitRemoved: hit, counts: countsOf(c), cap: capOf(c) };
    },
    depthChart: (id) => depthChartView(find(id)),
    saveDepthChart(id, req) {
      const c = writable(id);
      checkVersion(c, req.version);
      const chart = emptyChart();
      const seen = new Set<string>();
      for (const s of req.slots ?? []) {
        const k = String(s.slot ?? "").trim().toUpperCase().replace(/-/g, "_");
        const def = SLOT_BY_KEY.get(k === "3RD_DOWN_BACK" || k === "THIRD_DOWN" ? "THIRD_DOWN_BACK" : k === "HOLDER" ? "H" : k === "LONG_SNAPPER" ? "LS" : k);
        if (!def) throw ApiError.badRequest("DEPTH_CHART_UNKNOWN_SLOT", { 0: String(s.slot) });
        if (seen.has(def.slot)) throw ApiError.badRequest("DEPTH_CHART_DUPLICATE_SLOT", { 0: def.slot });
        seen.add(def.slot);
        chart[def.slot] = [...(s.playerIds ?? [])];
      }
      write(c, chart, null);
      return depthChartView(c);
    },
    setRole(id, playerId, req) {
      const c = writable(id);
      const def = SLOT_BY_KEY.get(String(req.role));
      if (!def || def.position) throw ApiError.badRequest("DEPTH_CHART_UNKNOWN_SLOT", { 0: String(req.role) });
      const p = clubPlayer(c, playerId);
      if (req.version != null) checkVersion(c, req.version);
      const depth = req.depth ?? 1;
      if (depth < 1 || depth > MAX_DEPTH) throw ApiError.badRequest("INVALID_DEPTH", { 0: depth, 1: MAX_DEPTH });
      const chart: Chart = Object.fromEntries(Object.entries(c.chart).map(([k, v]) => [k, [...v]]));
      const ids = chart[def.slot].filter((x) => x !== p.id);
      ids.splice(Math.min(depth - 1, ids.length), 0, p.id);
      chart[def.slot] = ids;
      write(c, chart, def.slot);
      return depthChartView(c);
    },
  };

  function checkVersion(c: StoredCareer, expected: number | null | undefined) {
    if (expected == null || expected !== c.chartVersion) {
      throw new ApiError({ status: 409, code: "DEPTH_CHART_VERSION_CONFLICT", message: "", details: { currentVersion: c.chartVersion, expectedVersion: expected ?? null }, correlationId: null, locale: null, params: { 0: expected ?? "—", 1: c.chartVersion } });
    }
  }

  /** Valida e grava; `onlySlot` (papel) só barra erros da própria vaga ou do limite de ativos, como o backend. */
  function write(c: StoredCareer, chart: Chart, onlySlot: string | null) {
    const v = validate(chart, playersById(c), RULES.gameDayActives);
    const blocking = v.errors.filter((i) => onlySlot == null || i.slot === onlySlot || i.code === "DEPTH_CHART_TOO_MANY_ACTIVES");
    if (blocking.length) {
      const first = blocking[0];
      const def = first.slot ? SLOT_BY_KEY.get(first.slot) : null;
      throw new ApiError({ status: 422, code: first.code, message: "", details: { errors: blocking, warnings: v.warnings }, correlationId: null, locale: null, params: { 0: first.slot ?? RULES.gameDayActives, 1: first.depth ?? "", 2: def?.minStarters ?? "" } });
    }
    c.chart = chart;
    c.chartVersion += 1;
    c.chartUpdatedAt = new Date().toISOString();
    touch(c);
    commit();
  }
}
