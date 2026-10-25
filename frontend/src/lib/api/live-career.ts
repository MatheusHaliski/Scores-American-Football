/**
 * Adaptadores das respostas da carreira (bloco 6a) para os tipos do front. Puros (sem rede), testados contra as amostras gravadas
 * pela API em docs/validation/golden/ (careers-list, career-dashboard, career-calendar, roster, roster-player, depth-chart e
 * world-editor-club). `dashboard.live` (partida em andamento) chega com o bloco 6b. A API omite campos nulos (`non_null`), menos no `DashboardDto`, que manda os nulos explícitos: aqui todo
 * anulável ausente vira `null`, e nunca zero.
 *
 * `SHAPES` lista, por objeto, os campos que o front conhece e quais podem faltar; o teste de contrato confere cada golden contra
 * essa lista (campo desconhecido falha, obrigatório ausente falha).
 */
import type {
  CalendarWeek,
  CapSummary,
  CareerDashboard,
  CareerList,
  CareerStatus,
  CareerSummary,
  ClubOption,
  ClubRef,
  CoachStyle,
  CoachSummary,
  DepthChart,
  DepthChartAlert,
  DepthEntry,
  DepthIssue,
  DepthSlot,
  DepthUnit,
  Fixture,
  InjuryEntry,
  LeagueClubs,
  LeagueSummary,
  NextGame,
  PlayerCard,
  PlayerContract,
  PlayerDetail,
  PlayerHighlight,
  ReleaseResult,
  Roster,
  RosterChange,
  RosterCounts,
  RosterMove,
  RosterRules,
  RosterStatus,
  SeasonCalendar,
  StandingRow,
  TeamRecord,
  Unit,
  WorldSummary,
} from "./career-types";
import { COACH_STYLES, DIFFICULTIES, ROSTER_STATUSES, UNITS } from "./career-types";
import type { LiveMatchRef, MatchSessionStatus } from "./match-types";
import type { CardTier } from "@/lib/ratings/tier";
import type { Position } from "@/lib/ratings/overall";
import type { Difficulty } from "./types";

/** Corpo cru da API (JSON). */
export type Raw = Record<string, unknown>;

interface Shape {
  fields: readonly string[];
  nullable: readonly string[];
}
const shape = (fields: string[], nullable: string[] = []): Shape => ({ fields: [...fields].sort(), nullable });

const CAREER_FIELDS = [
  "id", "name", "slot", "status", "difficulty", "seed", "currentWeek", "employmentStatus", "worldId", "sourceWorldId", "sourceWorldName", "leagueId",
  "leagueName", "ruleset", "clubId", "clubName", "clubShortName", "clubCity", "clubPrimaryColor", "clubSecondaryColor", "coachId", "coachName",
  "coachNationality", "coachStyle", "seasonId", "seasonYear", "seasonPhase", "regularSeasonWeeks", "record", "createdAt", "lastPlayedAt", "version",
];
const CAREER_NULLABLE = [
  "slot", "sourceWorldId", "sourceWorldName", "leagueId", "leagueName", "ruleset", "clubId", "clubName", "clubShortName", "clubCity", "clubPrimaryColor",
  "clubSecondaryColor", "coachId", "coachName", "coachNationality", "coachStyle", "seasonId", "seasonYear", "seasonPhase", "lastPlayedAt",
];
const CARD_FIELDS = [
  "id", "name", "jerseyNumber", "position", "secondaryPosition", "unit", "age", "heightCm", "weightKg", "overall", "tier", "family", "macros",
  "rosterStatus", "condition", "morale", "experienceYears", "injuryWeeksOut", "suspensionWeeks", "capHit", "contractEndSeason", "contractType", "depthChart",
];

/** Campos conhecidos de cada objeto das respostas do bloco 6a. */
export const SHAPES = {
  careerList: shape(["maxActiveSlots", "activeCount", "freeSlots", "careers"]),
  career: shape(CAREER_FIELDS, CAREER_NULLABLE),
  record: shape(["wins", "losses", "ties", "summary"]),
  dashboard: shape(
    ["career", "club", "coach", "currentWeek", "nextGame", "byeWeek", "record", "divisionRank", "division", "upcomingDivisionGames", "rosterHighlights", "injuries", "cap", "depthChart", "live", "morale", "analysis", "inbox", "unreadInbox", "scoresPoints"],
    ["club", "coach", "nextGame", "divisionRank", "cap", "depthChart", "live", "morale", "analysis", "inbox", "unreadInbox", "scoresPoints"],
  ),
  clubRef: shape(["id", "name", "shortName", "city", "primaryColor", "secondaryColor", "conference", "division"], ["conference", "division"]),
  coach: shape(["id", "name", "nationality", "style", "overall", "reputationLeague", "careerGames", "careerWins", "careerTitles"], ["style"]),
  nextGame: shape(["fixture", "home", "opponent", "opponentRecord"]),
  fixture: shape(["id", "week", "date", "kickoffSlot", "status", "homeClubId", "awayClubId", "homeScore", "awayScore", "divisional", "conferenceGame", "userGame"], ["homeScore", "awayScore"]),
  standingRow: shape(["rank", "club", "record", "winPct", "pointsFor", "pointsAgainst", "userClub"]),
  highlight: shape(["id", "name", "position", "jerseyNumber", "overall", "tier"]),
  injury: shape(["playerId", "name", "position", "rosterStatus", "weeksOut"]),
  cap: shape(["salaryCap", "capUsed", "deadMoney", "capSpace", "deadMoneyNextSeason"]),
  depthChartAlert: shape(["version", "valid", "errors", "warnings", "codes"]),
  /** `dashboard.live` (bloco 6b): partida ao vivo em andamento */
  liveRef: shape(["matchId", "fixtureId", "week", "status", "homeScore", "awayScore"]),
  calendar: shape(["seasonId", "seasonYear", "phase", "currentWeek", "regularSeasonWeeks", "gamesPerTeam", "userClubId", "clubs", "weeks"]),
  week: shape(["number", "kind", "startsOn", "status", "userBye", "byeClubIds", "fixtures"]),
  roster: shape(["clubId", "clubName", "seasonYear", "currentWeek", "rules", "counts", "cap", "units", "practiceSquad", "injuredReserve", "waivers"]),
  rosterRules: shape(["rosterMax", "gameDayActives", "practiceSquadMax", "irMinWeeks", "irReturnsPerSeason"]),
  counts: shape(["roster", "active", "inactive", "practiceSquad", "injuredReserve", "irReturnsUsed", "waivers"]),
  card: shape(CARD_FIELDS, ["secondaryPosition", "contractEndSeason", "contractType"]),
  playerDetail: shape(
    ["card", "clubId", "clubName", "ownPlayer", "nationality", "college", "birthDate", "potential", "attributes", "overallByPosition", "contract", "deadMoneyIfReleased", "deadMoneyNextSeasonIfReleased", "irPlacedWeek", "irEarliestReturnWeek", "history"],
    ["clubId", "clubName", "nationality", "college", "birthDate", "contract", "irPlacedWeek", "irEarliestReturnWeek"],
  ),
  contract: shape(["startSeason", "endSeason", "years", "baseSalaryByYear", "signingBonus", "guaranteed", "capHitByYear", "deadMoneyIfCutByYear", "type", "franchiseTag", "totalValue"]),
  rosterMove: shape(["id", "week", "kind", "fromStatus", "toStatus", "deadMoney", "at"], ["fromStatus", "toStatus"]),
  rosterChange: shape(["player", "counts", "cap"]),
  release: shape(["playerId", "name", "rosterStatus", "deadMoney", "deadMoneyNextSeason", "capHitRemoved", "counts", "cap"]),
  depthChart: shape(["id", "clubId", "version", "valid", "updatedAt", "gameDayActives", "uniquePlayers", "units", "errors", "warnings"], ["updatedAt"]),
  depthUnit: shape(["unit", "slots"]),
  depthSlot: shape(["slot", "position", "role", "minStarters", "players"], ["position"]),
  depthEntry: shape(["depth", "playerId", "name", "jerseyNumber", "position", "overallAtSlot", "overall", "tier", "outOfPosition", "available", "rosterStatus"]),
  depthIssue: shape(["code", "slot", "depth", "playerId"], ["slot", "depth", "playerId"]),
  /** só os campos que a nova carreira lê de `ClubDto` (o resto do editor é ignorado) */
  clubOption: shape(["id", "name", "shortName", "city", "primaryColor", "secondaryColor", "prestige", "expectedWins", "stadiumName"], ["expectedWins", "stadiumName"]),
} as const satisfies Record<string, Shape>;

/* ───────────────────────── leitores tolerantes ───────────────────────── */

const isObj = (v: unknown): v is Raw => typeof v === "object" && v !== null && !Array.isArray(v);
const obj = (v: unknown): Raw => (isObj(v) ? v : {});
const str = (v: unknown, fallback = ""): string => (typeof v === "string" ? v : v == null ? fallback : String(v));
const nstr = (v: unknown): string | null => (typeof v === "string" && v.length > 0 ? v : null);
const num = (v: unknown, fallback = 0): number => (typeof v === "number" && Number.isFinite(v) ? v : fallback);
const nnum = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
const bool = (v: unknown): boolean => v === true;
const list = <T>(v: unknown, map: (x: Raw) => T): T[] => (Array.isArray(v) ? v.filter(isObj).map(map) : []);
const nums = (v: unknown): number[] => (Array.isArray(v) ? v.filter((x): x is number => typeof x === "number") : []);
const strs = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : []);
const numMap = (v: unknown): Record<string, number> => {
  const out: Record<string, number> = {};
  for (const [k, x] of Object.entries(obj(v))) if (typeof x === "number") out[k] = x;
  return out;
};
const oneOf = <T extends string>(v: unknown, allowed: readonly T[], fallback: T): T => ((allowed as readonly string[]).includes(String(v)) ? (v as T) : fallback);
const maybeOneOf = <T extends string>(v: unknown, allowed: readonly T[]): T | null => ((allowed as readonly string[]).includes(String(v)) ? (v as T) : null);

/* ───────────────────────── carreira ───────────────────────── */

export function recordFromDto(raw: unknown): TeamRecord {
  const r = obj(raw);
  const wins = num(r.wins), losses = num(r.losses), ties = num(r.ties);
  return { wins, losses, ties, summary: nstr(r.summary) ?? (ties > 0 ? `${wins}-${losses}-${ties}` : `${wins}-${losses}`) };
}

export function careerFromDto(raw: unknown): CareerSummary {
  const r = obj(raw);
  return {
    id: str(r.id),
    name: str(r.name),
    slot: nnum(r.slot),
    status: oneOf<CareerStatus>(r.status, ["ACTIVE", "ARCHIVED"], "ACTIVE"),
    difficulty: oneOf<Difficulty>(r.difficulty, DIFFICULTIES, "NORMAL"),
    seed: num(r.seed),
    currentWeek: num(r.currentWeek, 1),
    employmentStatus: str(r.employmentStatus, "EMPLOYED"),
    worldId: str(r.worldId),
    sourceWorldId: nstr(r.sourceWorldId),
    sourceWorldName: nstr(r.sourceWorldName),
    leagueId: nstr(r.leagueId),
    leagueName: nstr(r.leagueName),
    ruleset: nstr(r.ruleset),
    clubId: nstr(r.clubId),
    clubName: nstr(r.clubName),
    clubShortName: nstr(r.clubShortName),
    clubCity: nstr(r.clubCity),
    clubPrimaryColor: nstr(r.clubPrimaryColor),
    clubSecondaryColor: nstr(r.clubSecondaryColor),
    coachId: nstr(r.coachId),
    coachName: nstr(r.coachName),
    coachNationality: nstr(r.coachNationality),
    coachStyle: maybeOneOf<CoachStyle>(r.coachStyle, COACH_STYLES),
    seasonId: nstr(r.seasonId),
    seasonYear: nnum(r.seasonYear),
    seasonPhase: nstr(r.seasonPhase),
    regularSeasonWeeks: num(r.regularSeasonWeeks, 18),
    record: recordFromDto(r.record),
    createdAt: str(r.createdAt),
    lastPlayedAt: nstr(r.lastPlayedAt),
    version: num(r.version),
  };
}

export function careerListFromDto(raw: unknown): CareerList {
  const r = obj(raw);
  return { maxActiveSlots: num(r.maxActiveSlots, 5), activeCount: num(r.activeCount), freeSlots: nums(r.freeSlots), careers: list(r.careers, careerFromDto) };
}

export function clubRefFromDto(raw: unknown): ClubRef {
  const r = obj(raw);
  return {
    id: str(r.id),
    name: str(r.name),
    shortName: str(r.shortName),
    city: str(r.city),
    primaryColor: str(r.primaryColor, "#1B2A4A"),
    secondaryColor: str(r.secondaryColor, "#F2B705"),
    conference: nstr(r.conference),
    division: nstr(r.division),
  };
}

export function fixtureFromDto(raw: unknown): Fixture {
  const r = obj(raw);
  return {
    id: str(r.id),
    week: num(r.week),
    date: str(r.date),
    kickoffSlot: str(r.kickoffSlot),
    status: str(r.status, "SCHEDULED"),
    homeClubId: str(r.homeClubId),
    awayClubId: str(r.awayClubId),
    homeScore: nnum(r.homeScore),
    awayScore: nnum(r.awayScore),
    divisional: bool(r.divisional),
    conferenceGame: bool(r.conferenceGame),
    userGame: bool(r.userGame),
  };
}

function coachFromDto(raw: unknown): CoachSummary {
  const r = obj(raw);
  return {
    id: str(r.id),
    name: str(r.name),
    nationality: str(r.nationality),
    style: maybeOneOf<CoachStyle>(r.style, COACH_STYLES),
    overall: num(r.overall),
    reputationLeague: num(r.reputationLeague),
    careerGames: num(r.careerGames),
    careerWins: num(r.careerWins),
    careerTitles: num(r.careerTitles),
  };
}

export function capFromDto(raw: unknown): CapSummary {
  const r = obj(raw);
  return { salaryCap: num(r.salaryCap), capUsed: num(r.capUsed), deadMoney: num(r.deadMoney), capSpace: num(r.capSpace), deadMoneyNextSeason: num(r.deadMoneyNextSeason) };
}

function nextGameFromDto(raw: unknown): NextGame {
  const r = obj(raw);
  return { fixture: fixtureFromDto(r.fixture), home: bool(r.home), opponent: clubRefFromDto(r.opponent), opponentRecord: recordFromDto(r.opponentRecord) };
}

function standingFromDto(r: Raw): StandingRow {
  return { rank: num(r.rank), club: clubRefFromDto(r.club), record: recordFromDto(r.record), winPct: num(r.winPct), pointsFor: num(r.pointsFor), pointsAgainst: num(r.pointsAgainst), userClub: bool(r.userClub) };
}

function highlightFromDto(r: Raw): PlayerHighlight {
  return { id: str(r.id), name: str(r.name), position: str(r.position) as Position, jerseyNumber: num(r.jerseyNumber), overall: num(r.overall), tier: str(r.tier, "BRONZE") as CardTier };
}

function injuryFromDto(r: Raw): InjuryEntry {
  return { playerId: str(r.playerId), name: str(r.name), position: str(r.position) as Position, rosterStatus: str(r.rosterStatus, "ACTIVE") as RosterStatus, weeksOut: num(r.weeksOut) };
}

function alertFromDto(raw: unknown): DepthChartAlert {
  const r = obj(raw);
  return { version: num(r.version), valid: bool(r.valid), errors: num(r.errors), warnings: num(r.warnings), codes: strs(r.codes) };
}

const LIVE_STATUSES: MatchSessionStatus[] = ["NOT_STARTED", "LIVE", "BREAK", "HALFTIME", "AWAITING_DECISION", "FINISHED"];

/** `dashboard.live` (`LiveMatchRefDto`, bloco 6b) ou null. */
export function liveRefFromDto(raw: unknown): LiveMatchRef | null {
  if (!isObj(raw)) return null;
  return { matchId: str(raw.matchId), fixtureId: str(raw.fixtureId), week: num(raw.week, 1), status: oneOf<MatchSessionStatus>(raw.status, LIVE_STATUSES, "LIVE"), homeScore: num(raw.homeScore), awayScore: num(raw.awayScore) };
}

export function dashboardFromDto(raw: unknown): CareerDashboard {
  const r = obj(raw);
  return {
    career: careerFromDto(r.career),
    club: isObj(r.club) ? clubRefFromDto(r.club) : null,
    coach: isObj(r.coach) ? coachFromDto(r.coach) : null,
    currentWeek: num(r.currentWeek, 1),
    nextGame: isObj(r.nextGame) ? nextGameFromDto(r.nextGame) : null,
    byeWeek: bool(r.byeWeek),
    record: recordFromDto(r.record),
    divisionRank: nnum(r.divisionRank),
    division: list(r.division, standingFromDto),
    upcomingDivisionGames: list(r.upcomingDivisionGames, fixtureFromDto),
    rosterHighlights: list(r.rosterHighlights, highlightFromDto),
    injuries: list(r.injuries, injuryFromDto),
    cap: isObj(r.cap) ? capFromDto(r.cap) : null,
    depthChart: isObj(r.depthChart) ? alertFromDto(r.depthChart) : null,
    live: liveRefFromDto(r.live),
    morale: r.morale ?? null,
    analysis: r.analysis ?? null,
    inbox: Array.isArray(r.inbox) ? r.inbox : null,
    unreadInbox: nnum(r.unreadInbox),
    scoresPoints: nnum(r.scoresPoints),
  };
}

function weekFromDto(r: Raw): CalendarWeek {
  return { number: num(r.number), kind: str(r.kind, "REGULAR"), startsOn: str(r.startsOn), status: str(r.status, "UPCOMING"), userBye: bool(r.userBye), byeClubIds: strs(r.byeClubIds), fixtures: list(r.fixtures, fixtureFromDto) };
}

export function calendarFromDto(raw: unknown): SeasonCalendar {
  const r = obj(raw);
  return {
    seasonId: str(r.seasonId),
    seasonYear: num(r.seasonYear),
    phase: str(r.phase, "REGULAR"),
    currentWeek: num(r.currentWeek, 1),
    regularSeasonWeeks: num(r.regularSeasonWeeks, 18),
    gamesPerTeam: num(r.gamesPerTeam, 17),
    userClubId: str(r.userClubId),
    clubs: list(r.clubs, clubRefFromDto),
    weeks: list(r.weeks, weekFromDto),
  };
}

/* ───────────────────────── elenco ───────────────────────── */

export function countsFromDto(raw: unknown): RosterCounts {
  const r = obj(raw);
  return { roster: num(r.roster), active: num(r.active), inactive: num(r.inactive), practiceSquad: num(r.practiceSquad), injuredReserve: num(r.injuredReserve), irReturnsUsed: num(r.irReturnsUsed), waivers: num(r.waivers) };
}

function rulesFromDto(raw: unknown): RosterRules {
  const r = obj(raw);
  return { rosterMax: num(r.rosterMax, 53), gameDayActives: num(r.gameDayActives, 46), practiceSquadMax: num(r.practiceSquadMax, 16), irMinWeeks: num(r.irMinWeeks, 4), irReturnsPerSeason: num(r.irReturnsPerSeason, 8) };
}

export function cardFromDto(raw: unknown): PlayerCard {
  const r = obj(raw);
  return {
    id: str(r.id),
    name: str(r.name),
    jerseyNumber: num(r.jerseyNumber),
    position: str(r.position) as Position,
    secondaryPosition: (nstr(r.secondaryPosition) as Position | null) ?? null,
    unit: oneOf<Unit>(r.unit, UNITS, "OFFENSE"),
    age: num(r.age),
    heightCm: num(r.heightCm),
    weightKg: num(r.weightKg),
    overall: num(r.overall),
    tier: str(r.tier, "BRONZE") as CardTier,
    family: str(r.family),
    macros: numMap(r.macros),
    rosterStatus: oneOf<RosterStatus>(r.rosterStatus, ROSTER_STATUSES, "ACTIVE"),
    condition: num(r.condition),
    morale: num(r.morale),
    experienceYears: num(r.experienceYears),
    injuryWeeksOut: num(r.injuryWeeksOut),
    suspensionWeeks: num(r.suspensionWeeks),
    capHit: num(r.capHit),
    contractEndSeason: nnum(r.contractEndSeason),
    contractType: nstr(r.contractType),
    depthChart: strs(r.depthChart),
  };
}

export function rosterFromDto(raw: unknown): Roster {
  const r = obj(raw);
  const u = obj(r.units);
  return {
    clubId: str(r.clubId),
    clubName: str(r.clubName),
    seasonYear: num(r.seasonYear),
    currentWeek: num(r.currentWeek, 1),
    rules: rulesFromDto(r.rules),
    counts: countsFromDto(r.counts),
    cap: capFromDto(r.cap),
    units: { OFFENSE: list(u.OFFENSE, cardFromDto), DEFENSE: list(u.DEFENSE, cardFromDto), SPECIAL_TEAMS: list(u.SPECIAL_TEAMS, cardFromDto) },
    practiceSquad: list(r.practiceSquad, cardFromDto),
    injuredReserve: list(r.injuredReserve, cardFromDto),
    waivers: list(r.waivers, cardFromDto),
  };
}

export function contractFromDto(raw: unknown): PlayerContract {
  const r = obj(raw);
  return {
    startSeason: num(r.startSeason),
    endSeason: num(r.endSeason),
    years: num(r.years),
    baseSalaryByYear: nums(r.baseSalaryByYear),
    signingBonus: num(r.signingBonus),
    guaranteed: num(r.guaranteed),
    capHitByYear: nums(r.capHitByYear),
    deadMoneyIfCutByYear: nums(r.deadMoneyIfCutByYear),
    type: str(r.type),
    franchiseTag: bool(r.franchiseTag),
    totalValue: num(r.totalValue),
  };
}

function moveFromDto(r: Raw): RosterMove {
  return { id: str(r.id), week: num(r.week), kind: str(r.kind), fromStatus: nstr(r.fromStatus), toStatus: nstr(r.toStatus), deadMoney: num(r.deadMoney), at: str(r.at) };
}

export function playerDetailFromDto(raw: unknown): PlayerDetail {
  const r = obj(raw);
  const attributes: Record<string, Record<string, number>> = {};
  for (const [g, v] of Object.entries(obj(r.attributes))) attributes[g] = numMap(v);
  // o backend grava `String.valueOf(null)` quando não há data: "null" conta como ausente
  const birth = nstr(r.birthDate);
  return {
    card: cardFromDto(r.card),
    clubId: nstr(r.clubId),
    clubName: nstr(r.clubName),
    ownPlayer: bool(r.ownPlayer),
    nationality: nstr(r.nationality),
    college: nstr(r.college),
    birthDate: birth && birth !== "null" ? birth : null,
    potential: num(r.potential),
    attributes,
    overallByPosition: numMap(r.overallByPosition) as Partial<Record<Position, number>>,
    contract: isObj(r.contract) ? contractFromDto(r.contract) : null,
    deadMoneyIfReleased: num(r.deadMoneyIfReleased),
    deadMoneyNextSeasonIfReleased: num(r.deadMoneyNextSeasonIfReleased),
    irPlacedWeek: nnum(r.irPlacedWeek),
    irEarliestReturnWeek: nnum(r.irEarliestReturnWeek),
    history: list(r.history, moveFromDto),
  };
}

export function rosterChangeFromDto(raw: unknown): RosterChange {
  const r = obj(raw);
  return { player: cardFromDto(r.player), counts: countsFromDto(r.counts), cap: capFromDto(r.cap) };
}

export function releaseFromDto(raw: unknown): ReleaseResult {
  const r = obj(raw);
  return { playerId: str(r.playerId), name: str(r.name), rosterStatus: str(r.rosterStatus, "WAIVED"), deadMoney: num(r.deadMoney), deadMoneyNextSeason: num(r.deadMoneyNextSeason), capHitRemoved: num(r.capHitRemoved), counts: countsFromDto(r.counts), cap: capFromDto(r.cap) };
}

/* ───────────────────────── depth chart ───────────────────────── */

function entryFromDto(r: Raw): DepthEntry {
  return {
    depth: num(r.depth),
    playerId: str(r.playerId),
    name: str(r.name),
    jerseyNumber: num(r.jerseyNumber),
    position: str(r.position) as Position,
    overallAtSlot: num(r.overallAtSlot),
    overall: num(r.overall),
    tier: str(r.tier, "BRONZE") as CardTier,
    outOfPosition: bool(r.outOfPosition),
    available: bool(r.available),
    rosterStatus: oneOf<RosterStatus>(r.rosterStatus, ROSTER_STATUSES, "ACTIVE"),
  };
}

function slotFromDto(r: Raw): DepthSlot {
  return { slot: str(r.slot), position: (nstr(r.position) as Position | null) ?? null, role: bool(r.role), minStarters: num(r.minStarters), players: list(r.players, entryFromDto) };
}

function unitFromDto(r: Raw): DepthUnit {
  return { unit: oneOf<Unit>(r.unit, UNITS, "OFFENSE"), slots: list(r.slots, slotFromDto) };
}

export function issueFromDto(r: Raw): DepthIssue {
  return { code: str(r.code), slot: nstr(r.slot), depth: nnum(r.depth), playerId: nstr(r.playerId) };
}

export function depthChartFromDto(raw: unknown): DepthChart {
  const r = obj(raw);
  return {
    id: str(r.id),
    clubId: str(r.clubId),
    version: num(r.version),
    valid: bool(r.valid),
    updatedAt: nstr(r.updatedAt),
    gameDayActives: num(r.gameDayActives, 46),
    uniquePlayers: num(r.uniquePlayers),
    units: list(r.units, unitFromDto),
    errors: list(r.errors, issueFromDto),
    warnings: list(r.warnings, issueFromDto),
  };
}

/** Erros e avisos que acompanham um 422 do PUT (`details.errors` / `details.warnings`). */
export function issuesFromDetails(details: Record<string, unknown>): { errors: DepthIssue[]; warnings: DepthIssue[] } {
  return { errors: list(details.errors, issueFromDto), warnings: list(details.warnings, issueFromDto) };
}

/* ───────────────────────── mundo (nova carreira) ───────────────────────── */

export function worldFromDto(raw: unknown): WorldSummary {
  const r = obj(raw);
  return { id: str(r.id), name: str(r.name), description: nstr(r.description), kind: str(r.kind, "TEMPLATE"), defaultTemplate: bool(r.defaultTemplate), leagues: num(r.leagues), clubs: num(r.clubs), players: num(r.players) };
}

export function leagueFromDto(raw: unknown): LeagueSummary {
  const r = obj(raw);
  return {
    id: str(r.id),
    worldId: str(r.worldId),
    name: str(r.name),
    shortName: str(r.shortName),
    ruleset: str(r.ruleset, "PRO"),
    teamCount: num(r.teamCount),
    conferenceCount: num(r.conferenceCount),
    divisionsPerConference: num(r.divisionsPerConference),
    salaryCap: num(r.salaryCap),
    prestige: num(r.prestige),
    structureValid: r.structureValid !== false,
  };
}

export function clubOptionFromDto(raw: unknown): ClubOption {
  const r = obj(raw);
  return {
    id: str(r.id),
    name: str(r.name),
    shortName: str(r.shortName),
    city: str(r.city),
    primaryColor: str(r.primaryColor, "#1B2A4A"),
    secondaryColor: str(r.secondaryColor, "#F2B705"),
    prestige: num(r.prestige),
    expectedWins: nnum(r.expectedWins),
    stadiumName: nstr(r.stadiumName),
  };
}

/**
 * Junta a estrutura da liga (`StructureDto`: conferências → divisões → ids) com as franquias (`ClubDto[]`): a nova carreira mostra
 * a franquia agrupada por conferência e divisão. Franquia fora da estrutura vai para `unassigned`.
 */
export function leagueClubsFrom(leagueId: string, structure: unknown, clubs: unknown): LeagueClubs {
  const byId = new Map(list(clubs, clubOptionFromDto).map((c) => [c.id, c]));
  const used = new Set<string>();
  const conferences = list(obj(structure).conferences, (c) => ({
    id: nstr(c.id),
    name: str(c.name),
    shortName: str(c.shortName),
    divisions: list(c.divisions, (d) => ({
      id: nstr(d.id),
      name: str(d.name),
      clubs: strs(d.clubIds)
        .map((id) => byId.get(id))
        .filter((x): x is ClubOption => !!x)
        .map((x) => {
          used.add(x.id);
          return x;
        }),
    })),
  }));
  return { leagueId, conferences, unassigned: [...byId.values()].filter((c) => !used.has(c.id)) };
}
