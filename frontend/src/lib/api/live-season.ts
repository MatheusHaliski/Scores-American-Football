/**
 * Adaptadores das respostas do bloco 6c (classificação, playoffs e chave, notícias, resumo, avanço e troféus da temporada, moral e
 * analista) para os tipos do front. Puros (sem rede), testados contra as amostras gravadas pela API em docs/validation/golden/
 * (standings, playoffs, playoffs-bracket, week-news, season-summary, season-advance, trophies, morale, analysis e os problemas 422/409
 * do avanço). A API omite campos nulos (`non_null`): todo anulável ausente vira `null`, nunca zero.
 *
 * `SEASON_SHAPES` lista, por objeto, os campos que o front conhece e quais podem faltar; o teste de contrato confere cada golden contra
 * essa lista (campo desconhecido falha, obrigatório ausente falha). Os `params` das notícias, os `summaryParams` do analista, os
 * `statLine` dos prêmios, os `factors` da moral e o mapa `leaders` são mapas livres (as chaves dependem do tipo) e ficam fora da
 * conferência de campos; o front lê só as chaves que conhece.
 */
import { careerFromDto, clubRefFromDto, fixtureFromDto, recordFromDto, type Raw } from "./live-career";
import type {
  Analysis,
  AnalysisSummary,
  AnalystMatchup,
  Award,
  Bracket,
  BracketGame,
  BracketRound,
  BracketTeam,
  Campaign,
  ConferenceSeeds,
  ConferenceTable,
  DepthChange,
  DivisionTable,
  ExpiringContract,
  SeasonLeader,
  MoraleSummary,
  NewsItem,
  Offseason,
  OffseasonStep,
  Outlook,
  ParamValue,
  PlanAdvice,
  PlayerMorale,
  PlayoffSeed,
  PlayoffSituation,
  SeasonAdvanceResult,
  SeasonHistory,
  SeasonSummary,
  SquadMorale,
  Standings,
  StandingsRow,
  Trophies,
  Trophy,
  UnitRating,
  UserPlayoff,
  WeekNews,
} from "./season-types";

interface Shape {
  fields: readonly string[];
  nullable: readonly string[];
}
const shape = (fields: string[], nullable: string[] = []): Shape => ({ fields: [...fields].sort(), nullable });

const ROW_FIELDS = [
  "rank", "club", "record", "winPct", "pointsFor", "pointsAgainst", "pointDiff", "streak", "lastFive", "home", "away", "division", "conference", "seed", "clinch",
  "eliminated", "tiebreaker", "userClub",
];
const AWARD_FIELDS = ["type", "playerId", "name", "position", "club", "score", "statLine"];
const EXPIRING_FIELDS = ["playerId", "name", "position", "age", "overall", "endSeason", "capHit"];
const UNIT_FIELDS = ["unit", "rating", "leagueAverage", "diff"];

/** Campos conhecidos de cada objeto das respostas do bloco 6c. */
export const SEASON_SHAPES = {
  standings: shape(["seasonId", "seasonYear", "phase", "throughWeek", "regularSeasonWeeks", "official", "seedsPerConference", "byeSeeds", "userClubId", "divisionTiebreakers", "wildCardTiebreakers", "conferences"], ["userClubId"]),
  conferenceTable: shape(["id", "name", "shortName", "divisions", "rows"]),
  divisionTable: shape(["id", "name", "rows"]),
  standingsRow: shape(ROW_FIELDS, ["seed", "clinch", "tiebreaker"]),
  playoffs: shape(["seasonId", "status", "projection", "currentRound", "seedsPerConference", "byeSeeds", "neutralSiteFinal", "rounds", "conferences", "user", "champion", "runnerUp"], ["currentRound", "user", "champion", "runnerUp"]),
  conferenceSeeds: shape(["id", "name", "shortName", "seeds"]),
  seed: shape(["seed", "club", "record", "divisionChampion", "bye", "status", "tiebreaker"], ["tiebreaker"]),
  userPlayoff: shape(["clubId", "seed", "status", "round", "nextGame"], ["seed", "round", "nextGame"]),
  bracket: shape(["seasonId", "status", "userClubId", "rounds", "champion"], ["userClubId", "champion"]),
  bracketRound: shape(["index", "kind", "week", "status", "games", "byes"], ["week"]),
  bracketGame: shape(
    ["fixtureId", "conference", "home", "away", "homeScore", "awayScore", "overtime", "winnerClubId", "neutralSite", "status", "date", "kickoffSlot", "userGame"],
    ["fixtureId", "conference", "homeScore", "awayScore", "winnerClubId", "date", "kickoffSlot"],
  ),
  bracketTeam: shape(["club", "seed"]),
  weekNews: shape(["seasonId", "week", "weekKind", "completed", "items"], ["weekKind"]),
  news: shape(["id", "week", "kind", "tone", "headline", "body", "textKey", "params", "clubId", "playerId", "fixtureId"], ["clubId", "playerId", "fixtureId"]),
  summary: shape(
    ["seasonId", "number", "year", "phase", "completed", "champion", "runnerUp", "userClubId", "campaign", "provisionalAwards", "awards", "leaders", "offseason"],
    ["champion", "runnerUp", "userClubId", "campaign", "offseason"],
  ),
  campaign: shape(["record", "pointsFor", "pointsAgainst", "divisionRank", "conferenceRank", "seed", "playoffResult"], ["divisionRank", "conferenceRank", "seed", "playoffResult"]),
  award: shape(AWARD_FIELDS, ["playerId", "position", "club"]),
  leader: shape(["playerId", "name", "position", "club", "value", "games"], ["club"]),
  offseason: shape(["canAdvance", "steps", "expiringContracts"]),
  step: shape(["phase", "status", "handledBy"]),
  expiring: shape(EXPIRING_FIELDS),
  advance: shape(["previousSeasonId", "seasonId", "seasonNumber", "seasonYear", "phase", "currentWeek", "steps", "expiredContracts", "aiRenewals", "userExpiredContracts", "improved", "declined", "deadMoneyCarried", "career"]),
  trophies: shape(["userClubId", "championships", "conferenceTitles", "divisionTitles", "playoffAppearances", "trophies", "seasons"], ["userClubId"]),
  trophy: shape(["seasonId", "seasonNumber", "seasonYear", "kind", "club", "conference", "division", "userClub"], ["club", "conference", "division"]),
  seasonHistory: shape(["seasonId", "number", "year", "completed", "champion", "runnerUp", "mvp", "userRecord", "userResult"], ["champion", "runnerUp", "mvp", "userRecord", "userResult"]),
  squadMorale: shape(["seasonId", "throughWeek", "average", "label", "trend", "lastDelta", "distribution", "tradeRequests", "teamStreak", "record", "players"], ["teamStreak"]),
  playerMorale: shape(
    ["playerId", "name", "position", "jerseyNumber", "rosterStatus", "morale", "label", "trend", "lastDelta", "role", "expectedSnapShare", "lastSnapShare", "seasonSnapShare", "contractFinalYear", "contractYearsLeft", "tradeRequest", "performanceModifier", "factors"],
    ["expectedSnapShare", "lastSnapShare", "seasonSnapShare"],
  ),
  moraleSummary: shape(["average", "label", "trend", "low", "tradeRequests"]),
  analysis: shape(
    ["id", "seasonId", "week", "source", "generatedAt", "summaryCode", "summary", "summaryParams", "strength", "leagueStrength", "units", "strengths", "weaknesses", "upcoming", "depthChart", "gamePlan"],
  ),
  unit: shape(UNIT_FIELDS),
  outlook: shape(["fixtureId", "week", "opponent", "home", "neutralSite", "strength", "opponentStrength", "winProbability", "verdict", "biggestThreat", "matchups"], ["biggestThreat"]),
  analystMatchup: shape(["key", "ours", "theirs", "edge", "advantage"]),
  depthChange: shape(["slot", "inPlayerId", "inName", "inOverall", "outPlayerId", "outName", "outOverall", "gain"]),
  planAdvice: shape(["field", "current", "recommended", "reason"]),
  analysisSummary: shape(["id", "week", "generatedAt", "summaryCode", "summary", "nextWinProbability"], ["nextWinProbability"]),
  /** `PlayoffWeekDto` do calendário (D-105) */
  playoffWeek: shape(["number", "kind", "startsOn", "status"]),
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
const strs = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : []);
const numMap = (v: unknown): Record<string, number> => {
  const out: Record<string, number> = {};
  for (const [k, x] of Object.entries(obj(v))) if (typeof x === "number" && Number.isFinite(x)) out[k] = x;
  return out;
};
/** Mapa livre de parâmetros: só texto, número e booleano (o resto é descartado, nunca vira "[object Object]"). */
const paramMap = (v: unknown): Record<string, ParamValue> => {
  const out: Record<string, ParamValue> = {};
  for (const [k, x] of Object.entries(obj(v))) if (typeof x === "string" || typeof x === "boolean" || (typeof x === "number" && Number.isFinite(x))) out[k] = x;
  return out;
};
const clubOrNull = (v: unknown) => (isObj(v) ? clubRefFromDto(v) : null);

/* ───────────────────────── RF16 — classificação ───────────────────────── */

export function standingsRowFromDto(raw: unknown): StandingsRow {
  const r = obj(raw);
  return {
    rank: num(r.rank),
    club: clubRefFromDto(r.club),
    record: recordFromDto(r.record),
    winPct: num(r.winPct),
    pointsFor: num(r.pointsFor),
    pointsAgainst: num(r.pointsAgainst),
    pointDiff: num(r.pointDiff, num(r.pointsFor) - num(r.pointsAgainst)),
    streak: str(r.streak, "—"),
    lastFive: strs(r.lastFive),
    home: recordFromDto(r.home),
    away: recordFromDto(r.away),
    division: recordFromDto(r.division),
    conference: recordFromDto(r.conference),
    seed: nnum(r.seed),
    clinch: nstr(r.clinch),
    eliminated: bool(r.eliminated),
    tiebreaker: nstr(r.tiebreaker),
    userClub: bool(r.userClub),
  };
}

function divisionTableFromDto(r: Raw): DivisionTable {
  return { id: str(r.id), name: str(r.name), rows: list(r.rows, standingsRowFromDto) };
}

function conferenceTableFromDto(r: Raw): ConferenceTable {
  return { id: str(r.id), name: str(r.name), shortName: str(r.shortName), divisions: list(r.divisions, divisionTableFromDto), rows: list(r.rows, standingsRowFromDto) };
}

export function standingsFromDto(raw: unknown): Standings {
  const r = obj(raw);
  return {
    seasonId: str(r.seasonId),
    seasonYear: num(r.seasonYear),
    phase: str(r.phase, "REGULAR"),
    throughWeek: num(r.throughWeek),
    regularSeasonWeeks: num(r.regularSeasonWeeks, 18),
    official: bool(r.official),
    seedsPerConference: num(r.seedsPerConference, 7),
    byeSeeds: num(r.byeSeeds, 1),
    userClubId: nstr(r.userClubId),
    divisionTiebreakers: strs(r.divisionTiebreakers),
    wildCardTiebreakers: strs(r.wildCardTiebreakers),
    conferences: list(r.conferences, conferenceTableFromDto),
  };
}

/* ───────────────────────── RF66 — playoffs e chave ───────────────────────── */

function seedFromDto(r: Raw): PlayoffSeed {
  return { seed: num(r.seed), club: clubRefFromDto(r.club), record: recordFromDto(r.record), divisionChampion: bool(r.divisionChampion), bye: bool(r.bye), status: str(r.status, "ALIVE"), tiebreaker: nstr(r.tiebreaker) };
}

function conferenceSeedsFromDto(r: Raw): ConferenceSeeds {
  return { id: str(r.id), name: str(r.name), shortName: str(r.shortName), seeds: list(r.seeds, seedFromDto) };
}

/** `UserPlayoffDto` (playoffs e central) ou null. */
export function userPlayoffFromDto(raw: unknown): UserPlayoff | null {
  if (!isObj(raw)) return null;
  return { clubId: str(raw.clubId), seed: nnum(raw.seed), status: str(raw.status, "PROJECTED_OUT"), round: nstr(raw.round), nextGame: isObj(raw.nextGame) ? fixtureFromDto(raw.nextGame) : null };
}

export function playoffsFromDto(raw: unknown): PlayoffSituation {
  const r = obj(raw);
  return {
    seasonId: str(r.seasonId),
    status: str(r.status, "NOT_STARTED"),
    projection: bool(r.projection),
    currentRound: nstr(r.currentRound),
    seedsPerConference: num(r.seedsPerConference, 7),
    byeSeeds: num(r.byeSeeds, 1),
    neutralSiteFinal: bool(r.neutralSiteFinal),
    rounds: strs(r.rounds),
    conferences: list(r.conferences, conferenceSeedsFromDto),
    user: userPlayoffFromDto(r.user),
    champion: clubOrNull(r.champion),
    runnerUp: clubOrNull(r.runnerUp),
  };
}

function bracketTeamFromDto(raw: unknown): BracketTeam {
  const r = obj(raw);
  return { club: clubRefFromDto(r.club), seed: num(r.seed) };
}

function bracketGameFromDto(r: Raw): BracketGame {
  return {
    fixtureId: nstr(r.fixtureId),
    conference: nstr(r.conference),
    home: bracketTeamFromDto(r.home),
    away: bracketTeamFromDto(r.away),
    homeScore: nnum(r.homeScore),
    awayScore: nnum(r.awayScore),
    overtime: bool(r.overtime),
    winnerClubId: nstr(r.winnerClubId),
    neutralSite: bool(r.neutralSite),
    status: str(r.status, "SCHEDULED"),
    date: nstr(r.date),
    kickoffSlot: nstr(r.kickoffSlot),
    userGame: bool(r.userGame),
  };
}

function bracketRoundFromDto(r: Raw): BracketRound {
  return { index: num(r.index), kind: str(r.kind), week: nnum(r.week), status: str(r.status, "PENDING"), games: list(r.games, bracketGameFromDto), byes: list(r.byes, bracketTeamFromDto) };
}

export function bracketFromDto(raw: unknown): Bracket {
  const r = obj(raw);
  return { seasonId: str(r.seasonId), status: str(r.status, "NOT_STARTED"), userClubId: nstr(r.userClubId), rounds: list(r.rounds, bracketRoundFromDto), champion: clubOrNull(r.champion) };
}

/* ───────────────────────── RF21 — notícias ───────────────────────── */

export function newsFromDto(raw: unknown): NewsItem {
  const r = obj(raw);
  return {
    id: str(r.id),
    week: num(r.week),
    kind: str(r.kind),
    tone: str(r.tone, "NEUTRAL"),
    headline: str(r.headline),
    body: str(r.body),
    textKey: str(r.textKey),
    params: paramMap(r.params),
    clubId: nstr(r.clubId),
    playerId: nstr(r.playerId),
    fixtureId: nstr(r.fixtureId),
  };
}

export function weekNewsFromDto(raw: unknown): WeekNews {
  const r = obj(raw);
  return { seasonId: str(r.seasonId), week: num(r.week), weekKind: nstr(r.weekKind), completed: bool(r.completed), items: list(r.items, newsFromDto) };
}

/* ───────────────────────── RF35 — resumo, avanço e troféus ───────────────────────── */

export function awardFromDto(raw: unknown): Award {
  const r = obj(raw);
  return { type: str(r.type), playerId: nstr(r.playerId), name: str(r.name), position: nstr(r.position), club: clubOrNull(r.club), score: num(r.score), statLine: numMap(r.statLine) };
}

function leaderFromDto(r: Raw): SeasonLeader {
  return { playerId: str(r.playerId), name: str(r.name), position: str(r.position), club: clubOrNull(r.club), value: num(r.value), games: num(r.games) };
}

function campaignFromDto(raw: unknown): Campaign | null {
  if (!isObj(raw)) return null;
  return {
    record: recordFromDto(raw.record),
    pointsFor: num(raw.pointsFor),
    pointsAgainst: num(raw.pointsAgainst),
    divisionRank: nnum(raw.divisionRank),
    conferenceRank: nnum(raw.conferenceRank),
    seed: nnum(raw.seed),
    playoffResult: nstr(raw.playoffResult),
  };
}

function expiringFromDto(r: Raw): ExpiringContract {
  return { playerId: str(r.playerId), name: str(r.name), position: str(r.position), age: num(r.age), overall: num(r.overall), endSeason: num(r.endSeason), capHit: num(r.capHit) };
}

function stepFromDto(r: Raw): OffseasonStep {
  return { phase: str(r.phase), status: str(r.status, "PENDING"), handledBy: str(r.handledBy) };
}

function offseasonFromDto(raw: unknown): Offseason | null {
  if (!isObj(raw)) return null;
  return { canAdvance: bool(raw.canAdvance), steps: list(raw.steps, stepFromDto), expiringContracts: list(raw.expiringContracts, expiringFromDto) };
}

export function seasonSummaryFromDto(raw: unknown): SeasonSummary {
  const r = obj(raw);
  const leaders: Record<string, SeasonLeader[]> = {};
  for (const [k, v] of Object.entries(obj(r.leaders))) leaders[k] = list(v, leaderFromDto);
  return {
    seasonId: str(r.seasonId),
    number: num(r.number, 1),
    year: num(r.year),
    phase: str(r.phase, "REGULAR"),
    completed: bool(r.completed),
    champion: clubOrNull(r.champion),
    runnerUp: clubOrNull(r.runnerUp),
    userClubId: nstr(r.userClubId),
    campaign: campaignFromDto(r.campaign),
    provisionalAwards: bool(r.provisionalAwards),
    awards: list(r.awards, awardFromDto),
    leaders,
    offseason: offseasonFromDto(r.offseason),
  };
}

export function seasonAdvanceFromDto(raw: unknown): SeasonAdvanceResult {
  const r = obj(raw);
  return {
    previousSeasonId: str(r.previousSeasonId),
    seasonId: str(r.seasonId),
    seasonNumber: num(r.seasonNumber, 1),
    seasonYear: num(r.seasonYear),
    phase: str(r.phase, "REGULAR"),
    currentWeek: num(r.currentWeek, 1),
    steps: list(r.steps, stepFromDto),
    expiredContracts: num(r.expiredContracts),
    aiRenewals: num(r.aiRenewals),
    userExpiredContracts: list(r.userExpiredContracts, expiringFromDto),
    improved: num(r.improved),
    declined: num(r.declined),
    deadMoneyCarried: num(r.deadMoneyCarried),
    career: careerFromDto(r.career),
  };
}

function trophyFromDto(r: Raw): Trophy {
  return {
    seasonId: str(r.seasonId),
    seasonNumber: num(r.seasonNumber, 1),
    seasonYear: num(r.seasonYear),
    kind: str(r.kind),
    club: clubOrNull(r.club),
    conference: nstr(r.conference),
    division: nstr(r.division),
    userClub: bool(r.userClub),
  };
}

function historyFromDto(r: Raw): SeasonHistory {
  return {
    seasonId: str(r.seasonId),
    number: num(r.number, 1),
    year: num(r.year),
    completed: bool(r.completed),
    champion: clubOrNull(r.champion),
    runnerUp: clubOrNull(r.runnerUp),
    mvp: isObj(r.mvp) ? awardFromDto(r.mvp) : null,
    userRecord: isObj(r.userRecord) ? recordFromDto(r.userRecord) : null,
    userResult: nstr(r.userResult),
  };
}

export function trophiesFromDto(raw: unknown): Trophies {
  const r = obj(raw);
  return {
    userClubId: nstr(r.userClubId),
    championships: num(r.championships),
    conferenceTitles: num(r.conferenceTitles),
    divisionTitles: num(r.divisionTitles),
    playoffAppearances: num(r.playoffAppearances),
    trophies: list(r.trophies, trophyFromDto),
    seasons: list(r.seasons, historyFromDto),
  };
}

/* ───────────────────────── RF41 — moral ───────────────────────── */

function playerMoraleFromDto(r: Raw): PlayerMorale {
  return {
    playerId: str(r.playerId),
    name: str(r.name),
    position: str(r.position),
    jerseyNumber: num(r.jerseyNumber),
    rosterStatus: str(r.rosterStatus, "ACTIVE"),
    morale: num(r.morale),
    label: str(r.label, "NEUTRAL"),
    trend: str(r.trend, "STABLE"),
    lastDelta: num(r.lastDelta),
    role: str(r.role, "RESERVE"),
    expectedSnapShare: nnum(r.expectedSnapShare),
    lastSnapShare: nnum(r.lastSnapShare),
    seasonSnapShare: nnum(r.seasonSnapShare),
    contractFinalYear: bool(r.contractFinalYear),
    contractYearsLeft: num(r.contractYearsLeft),
    tradeRequest: bool(r.tradeRequest),
    performanceModifier: num(r.performanceModifier),
    factors: numMap(r.factors),
  };
}

export function squadMoraleFromDto(raw: unknown): SquadMorale {
  const r = obj(raw);
  return {
    seasonId: str(r.seasonId),
    throughWeek: num(r.throughWeek),
    average: num(r.average),
    label: str(r.label, "NEUTRAL"),
    trend: str(r.trend, "STABLE"),
    lastDelta: num(r.lastDelta),
    distribution: numMap(r.distribution),
    tradeRequests: num(r.tradeRequests),
    teamStreak: nstr(r.teamStreak),
    record: recordFromDto(r.record),
    players: list(r.players, playerMoraleFromDto),
  };
}

/** `dashboard.morale` (`MoraleSummaryDto`) ou null. */
export function moraleSummaryFromDto(raw: unknown): MoraleSummary | null {
  if (!isObj(raw)) return null;
  return { average: num(raw.average), label: str(raw.label, "NEUTRAL"), trend: str(raw.trend, "STABLE"), low: num(raw.low), tradeRequests: num(raw.tradeRequests) };
}

/* ───────────────────────── analista ───────────────────────── */

function unitFromDto(r: Raw): UnitRating {
  return { unit: str(r.unit), rating: num(r.rating), leagueAverage: num(r.leagueAverage), diff: num(r.diff) };
}

function analystMatchupFromDto(r: Raw): AnalystMatchup {
  return { key: str(r.key), ours: num(r.ours), theirs: num(r.theirs), edge: num(r.edge), advantage: str(r.advantage, "EVEN") };
}

function outlookFromDto(r: Raw): Outlook {
  return {
    fixtureId: str(r.fixtureId),
    week: num(r.week),
    opponent: clubRefFromDto(r.opponent),
    home: bool(r.home),
    neutralSite: bool(r.neutralSite),
    strength: num(r.strength),
    opponentStrength: num(r.opponentStrength),
    winProbability: num(r.winProbability),
    verdict: str(r.verdict, "EVEN"),
    biggestThreat: nstr(r.biggestThreat),
    matchups: list(r.matchups, analystMatchupFromDto),
  };
}

function depthChangeFromDto(r: Raw): DepthChange {
  return { slot: str(r.slot), inPlayerId: str(r.inPlayerId), inName: str(r.inName), inOverall: num(r.inOverall), outPlayerId: str(r.outPlayerId), outName: str(r.outName), outOverall: num(r.outOverall), gain: num(r.gain) };
}

function planAdviceFromDto(r: Raw): PlanAdvice {
  return { field: str(r.field), current: str(r.current), recommended: str(r.recommended), reason: str(r.reason) };
}

export function analysisFromDto(raw: unknown): Analysis {
  const r = obj(raw);
  return {
    id: str(r.id),
    seasonId: str(r.seasonId),
    week: num(r.week),
    source: str(r.source, "RULES"),
    generatedAt: str(r.generatedAt),
    summaryCode: str(r.summaryCode),
    summary: str(r.summary),
    summaryParams: paramMap(r.summaryParams),
    strength: num(r.strength),
    leagueStrength: num(r.leagueStrength),
    units: list(r.units, unitFromDto),
    strengths: list(r.strengths, unitFromDto),
    weaknesses: list(r.weaknesses, unitFromDto),
    upcoming: list(r.upcoming, outlookFromDto),
    depthChart: list(r.depthChart, depthChangeFromDto),
    gamePlan: list(r.gamePlan, planAdviceFromDto),
  };
}

/** `dashboard.analysis` (`AnalysisSummaryDto`) ou null antes do primeiro relatório. */
export function analysisSummaryFromDto(raw: unknown): AnalysisSummary | null {
  if (!isObj(raw)) return null;
  return { id: str(raw.id), week: num(raw.week), generatedAt: str(raw.generatedAt), summaryCode: str(raw.summaryCode), summary: str(raw.summary), nextWinProbability: nnum(raw.nextWinProbability) };
}
