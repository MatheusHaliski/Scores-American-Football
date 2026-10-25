/**
 * Adaptadores das respostas do bloco 6b (plano de jogo, playbook, preparação, semana, partida ao vivo, play-by-play e relatório)
 * para os tipos do front. Puros (sem rede), testados contra as amostras gravadas pela API em docs/validation/golden/ (game-plan,
 * playbook, problem-409-game-plan, preparation, week, match-state, match-events, match-report e live-match). A API omite campos
 * nulos (`non_null`): todo anulável ausente vira `null`, nunca zero.
 *
 * `MATCH_SHAPES` lista, por objeto, os campos que o front conhece e quais podem faltar; o teste de contrato confere cada golden
 * contra essa lista (campo desconhecido falha, obrigatório ausente falha). Os `params` dos lances são um mapa livre do motor (as
 * chaves dependem do `textKey`) e ficam fora da conferência de campos.
 */
import type { CardTier, DepthIssue, Position } from "./career-types";
import { clubRefFromDto, fixtureFromDto, issueFromDto, recordFromDto, type Raw } from "./live-career";
import type {
  AdvanceMode,
  BaseDefense,
  BlitzFrequency,
  CallSource,
  DecisionRecord,
  DefenseLine,
  DefensiveFocus,
  Drive,
  FourthDownAggression,
  GamePlan,
  GamePlanOptions,
  KickingLine,
  Leader,
  LiveLine,
  LiveMatch,
  MatchEvents,
  MatchReport,
  MatchSessionStatus,
  MatchState,
  Matchup,
  PassingLine,
  Penalty,
  PendingDecision,
  PlanIssue,
  PlayerBoxScore,
  PlayerRef,
  PlayEvent,
  PlayOption,
  Playbook,
  PlaybookSituation,
  Preparation,
  PuntingLine,
  ReceivingLine,
  ReportSide,
  ReturnLine,
  RushingLine,
  TeamBoxScore,
  TeamLine,
  Tempo,
  WeekBoard,
  WeekGame,
  WeightEntry,
} from "./match-types";
import { ADVANCE_MODES, BASE_DEFENSES, BLITZ_FREQUENCIES, DEFENSIVE_FOCUSES, FOURTH_DOWN_AGGRESSIONS, MATCH_SESSION_STATUSES, TEMPOS } from "./match-types";

interface Shape {
  fields: readonly string[];
  nullable: readonly string[];
}
const shape = (fields: string[], nullable: string[] = []): Shape => ({ fields: [...fields].sort(), nullable });

const PLAN_FIELDS = ["id", "clubId", "version", "passRatio", "fourthDownAggression", "tempo", "defensiveFocus", "blitzFrequency", "baseDefense", "coachStyle", "source", "editable", "updatedAt", "options"];
const TEAM_BOX_FIELDS = [
  "clubId", "points", "pointsByQuarter", "firstDowns", "firstDownsRushing", "firstDownsPassing", "firstDownsPenalty", "thirdDownConversions", "thirdDownAttempts",
  "fourthDownConversions", "fourthDownAttempts", "redZoneTouchdowns", "redZoneTrips", "offensivePlays", "totalYards", "yardsPerPlay", "rushAttempts", "rushYards",
  "passCompletions", "passAttempts", "passYards", "sacksAllowed", "sackYardsLost", "interceptionsThrown", "fumbles", "fumblesLost", "turnovers", "penalties",
  "penaltyYards", "timeOfPossessionSeconds", "drives", "punts", "fieldGoalsMade", "fieldGoalAttempts", "defensiveSacks", "timeoutsUsed",
];
const EVENT_FIELDS = [
  "seq", "drive", "quarter", "clockSeconds", "possessionClubId", "down", "distance", "ballOnAbsYd", "type", "concept", "formation", "defensePackage", "coverage", "blitz",
  "yards", "result", "firstDown", "turnover", "points", "scoringClubId", "penalty", "playerIds", "textKey", "params", "homeScore", "awayScore", "clockStopped",
  "driveEnded", "callSource",
];
const EVENT_NULLABLE = ["possessionClubId", "type", "concept", "formation", "defensePackage", "coverage", "result", "scoringClubId", "penalty", "driveEnded", "callSource"];
const PLAYER_REF_FIELDS = ["id", "name", "position", "jerseyNumber", "overall", "tier"];

/** Campos conhecidos de cada objeto das respostas do bloco 6b. */
export const MATCH_SHAPES = {
  gamePlan: shape(PLAN_FIELDS, ["coachStyle", "updatedAt"]),
  gamePlanOptions: shape(["minPassRatio", "maxPassRatio", "fourthDownAggression", "tempo", "defensiveFocus", "blitzFrequency", "baseDefense"]),
  playbook: shape(["id", "clubId", "version", "minWeight", "maxWeight", "neutralWeight", "runConcepts", "passConcepts", "situations", "coverages", "customWeights", "updatedAt"], ["updatedAt"]),
  playbookSituation: shape(["situation", "concepts"]),
  weight: shape(["item", "weight"]),
  preparation: shape(
    ["fixture", "home", "club", "opponent", "record", "opponentRecord", "weather", "units", "keyMatchups", "keyPlayers", "opponentKeyPlayers", "readiness", "gamePlan", "playbookCustomWeights", "playCallingLevel", "matchId"],
    ["matchId"],
  ),
  weather: shape(["condition", "stadium", "roof", "surface"], ["stadium", "roof", "surface"]),
  unitComparison: shape(["unit", "ours", "theirs", "edge"]),
  matchup: shape(["key", "side", "ours", "theirs", "edge", "advantage", "ourPlayers", "theirPlayers"]),
  playerRef: shape(PLAYER_REF_FIELDS),
  readiness: shape(["ready", "depthChartVersion", "errors", "warnings"]),
  weekBoard: shape(
    ["seasonId", "week", "regularSeasonWeeks", "currentWeek", "kind", "status", "startsOn", "completed", "userBye", "userFixtureId", "matchId", "byeClubIds", "games"],
    ["kind", "status", "startsOn", "userFixtureId", "matchId"],
  ),
  weekGame: shape(["fixture", "home", "away", "live"], ["live"]),
  liveLine: shape(["matchId", "status", "quarter", "clockSeconds", "possessionClubId", "down", "distance", "ballOnAbsYd", "homeScore", "awayScore"], ["possessionClubId"]),
  matchState: shape(
    ["matchId", "careerId", "fixtureId", "week", "status", "userClubId", "userHome", "playCallingLevel", "adjustable", "advanceModes", "decisionDeadlineAt", "decisionTimeoutSeconds", "live"],
    ["decisionDeadlineAt"],
  ),
  liveMatch: shape(
    ["fixtureId", "version", "engineVersion", "rulesVersion", "ruleset", "weather", "status", "quarter", "clockSeconds", "playClockSeconds", "possessionClubId", "down", "distance", "ballOnAbsYd", "home", "away", "decision", "lastEvents", "lastDrive", "driveCount"],
    ["possessionClubId", "decision", "lastDrive"],
  ),
  teamLine: shape(["clubId", "name", "shortName", "score", "quarters", "timeouts", "stats", "players"]),
  teamBox: shape(TEAM_BOX_FIELDS),
  playerBox: shape(
    ["playerId", "clubId", "name", "position", "jerseyNumber", "snaps", "passing", "rushing", "receiving", "defense", "kicking", "punting", "returns"],
    ["passing", "rushing", "receiving", "defense", "kicking", "punting", "returns"],
  ),
  passing: shape(["completions", "attempts", "yards", "touchdowns", "interceptions", "sacked", "sackYards", "rating", "longest"]),
  rushing: shape(["attempts", "yards", "average", "touchdowns", "fumbles", "fumblesLost", "longest"]),
  receiving: shape(["targets", "receptions", "yards", "yardsAfterCatch", "touchdowns", "drops", "longest"]),
  defense: shape(["soloTackles", "assistedTackles", "tacklesForLoss", "sacks", "qbHits", "interceptions", "passesDefended", "forcedFumbles", "fumbleRecoveries", "touchdowns"], ["assistedTackles", "qbHits"]),
  kicking: shape(["fgMade", "fgAttempts", "fgMade0to39", "fgAttempts0to39", "fgMade40to49", "fgAttempts40to49", "fgMade50plus", "fgAttempts50plus", "longest", "patMade", "patAttempts"]),
  punting: shape(["punts", "yards", "average", "inside20", "touchbacks", "longest"]),
  returns: shape(["kickReturns", "kickReturnYards", "puntReturns", "puntReturnYards", "touchdowns", "longest"]),
  decision: shape(["id", "situation", "side", "clubId", "quarter", "clockSeconds", "down", "distance", "ballOnAbsYd", "homeScore", "awayScore", "options", "autoOptionCode", "openedAtSeq"]),
  playOption: shape(["code", "side", "concept", "formation", "defensePackage", "coverage", "blitz", "labelKey"], ["concept", "formation", "defensePackage", "coverage"]),
  event: shape(EVENT_FIELDS, EVENT_NULLABLE),
  penalty: shape(["code", "againstClubId", "playerId", "yards", "accepted", "automaticFirstDown", "lossOfDown", "preSnap"], ["playerId"]),
  drive: shape(["seq", "clubId", "quarter", "startClockSeconds", "startAbsYd", "endAbsYd", "plays", "yards", "timeOfPossessionSeconds", "reachedRedZone", "result"], ["result"]),
  matchEvents: shape(["matchId", "after", "lastSeq", "finished", "events"]),
  report: shape(
    ["fixtureId", "matchId", "week", "date", "kickoffSlot", "userGame", "seed", "engineVersion", "rulesVersion", "weather", "home", "away", "overtime", "winnerClubId", "playCount", "leaders", "drives", "scoringPlays", "decisions"],
    ["matchId", "date", "kickoffSlot", "weather", "winnerClubId"],
  ),
  reportSide: shape(["club", "score", "quarters", "stats", "players"]),
  leader: shape(["category", "clubId", "playerId", "name", "position", "value"]),
  decisionRecord: shape(["decisionId", "situation", "clubId", "optionCode", "auto", "atSeq"], ["situation", "clubId"]),
  planIssue: shape(["code", "field", "value"], ["value"]),
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
const oneOf = <T extends string>(v: unknown, allowed: readonly T[], fallback: T): T => ((allowed as readonly string[]).includes(String(v)) ? (v as T) : fallback);
const someOf = <T extends string>(v: unknown, allowed: readonly T[]): T[] => strs(v).filter((x): x is T => (allowed as readonly string[]).includes(x));
const optional = <T>(v: unknown, map: (x: Raw) => T): T | null => (isObj(v) ? map(v) : null);
const strMap = (v: unknown): Record<string, string> => {
  const out: Record<string, string> = {};
  for (const [k, x] of Object.entries(obj(v))) if (x != null) out[k] = String(x);
  return out;
};

/* ───────────────────────── plano de jogo e playbook ───────────────────────── */

function optionsFromDto(raw: unknown): GamePlanOptions {
  const r = obj(raw);
  const pick = <T extends string>(v: unknown, all: T[]) => {
    const got = someOf(v, all);
    return got.length ? got : [...all];
  };
  return {
    minPassRatio: num(r.minPassRatio, 20),
    maxPassRatio: num(r.maxPassRatio, 80),
    fourthDownAggression: pick(r.fourthDownAggression, FOURTH_DOWN_AGGRESSIONS),
    tempo: pick(r.tempo, TEMPOS),
    defensiveFocus: pick(r.defensiveFocus, DEFENSIVE_FOCUSES),
    blitzFrequency: pick(r.blitzFrequency, BLITZ_FREQUENCIES),
    baseDefense: pick(r.baseDefense, BASE_DEFENSES),
  };
}

export function gamePlanFromDto(raw: unknown): GamePlan {
  const r = obj(raw);
  return {
    id: str(r.id),
    clubId: str(r.clubId),
    version: num(r.version),
    passRatio: num(r.passRatio, 50),
    fourthDownAggression: oneOf<FourthDownAggression>(r.fourthDownAggression, FOURTH_DOWN_AGGRESSIONS, "NORMAL"),
    tempo: oneOf<Tempo>(r.tempo, TEMPOS, "NORMAL"),
    defensiveFocus: oneOf<DefensiveFocus>(r.defensiveFocus, DEFENSIVE_FOCUSES, "BALANCED"),
    blitzFrequency: oneOf<BlitzFrequency>(r.blitzFrequency, BLITZ_FREQUENCIES, "NORMAL"),
    baseDefense: oneOf<BaseDefense>(r.baseDefense, BASE_DEFENSES, "BASE_4_3"),
    coachStyle: nstr(r.coachStyle),
    source: r.source === "SAVED" ? "SAVED" : "COACH_STYLE",
    editable: r.editable !== false,
    updatedAt: nstr(r.updatedAt),
    options: optionsFromDto(r.options),
  };
}

const weightFromDto = (r: Raw): WeightEntry => ({ item: str(r.item), weight: num(r.weight, 100) });
const situationFromDto = (r: Raw): PlaybookSituation => ({ situation: str(r.situation), concepts: list(r.concepts, weightFromDto) });

export function playbookFromDto(raw: unknown): Playbook {
  const r = obj(raw);
  return {
    id: str(r.id),
    clubId: str(r.clubId),
    version: num(r.version),
    minWeight: num(r.minWeight, 0),
    maxWeight: num(r.maxWeight, 200),
    neutralWeight: num(r.neutralWeight, 100),
    runConcepts: strs(r.runConcepts),
    passConcepts: strs(r.passConcepts),
    situations: list(r.situations, situationFromDto),
    coverages: list(r.coverages, weightFromDto),
    customWeights: num(r.customWeights),
    updatedAt: nstr(r.updatedAt),
  };
}

/** Itens de `details.errors` do 422 do plano ou do playbook. */
export function planIssuesFromDetails(details: Record<string, unknown>): PlanIssue[] {
  return list(details.errors, (r) => ({ code: str(r.code), field: str(r.field), value: nstr(r.value) }));
}

/* ───────────────────────── preparação ───────────────────────── */

export function playerRefFromDto(r: Raw): PlayerRef {
  return { id: str(r.id), name: str(r.name), position: str(r.position) as Position, jerseyNumber: num(r.jerseyNumber), overall: num(r.overall), tier: str(r.tier, "BRONZE") as CardTier };
}

function matchupFromDto(r: Raw): Matchup {
  return {
    key: str(r.key),
    side: str(r.side),
    ours: num(r.ours),
    theirs: num(r.theirs),
    edge: num(r.edge),
    advantage: str(r.advantage, "EVEN"),
    ourPlayers: list(r.ourPlayers, playerRefFromDto),
    theirPlayers: list(r.theirPlayers, playerRefFromDto),
  };
}

export function preparationFromDto(raw: unknown): Preparation {
  const r = obj(raw);
  const rd = obj(r.readiness);
  const w = obj(r.weather);
  return {
    fixture: fixtureFromDto(r.fixture),
    home: bool(r.home),
    club: clubRefFromDto(r.club),
    opponent: clubRefFromDto(r.opponent),
    record: recordFromDto(r.record),
    opponentRecord: recordFromDto(r.opponentRecord),
    weather: { condition: str(w.condition, "CLEAR"), stadium: nstr(w.stadium), roof: nstr(w.roof), surface: nstr(w.surface) },
    units: list(r.units, (u) => ({ unit: str(u.unit), ours: num(u.ours), theirs: num(u.theirs), edge: num(u.edge) })),
    keyMatchups: list(r.keyMatchups, matchupFromDto),
    keyPlayers: list(r.keyPlayers, playerRefFromDto),
    opponentKeyPlayers: list(r.opponentKeyPlayers, playerRefFromDto),
    readiness: { ready: bool(rd.ready), depthChartVersion: num(rd.depthChartVersion), errors: list(rd.errors, issueFromDto) as DepthIssue[], warnings: list(rd.warnings, issueFromDto) as DepthIssue[] },
    gamePlan: gamePlanFromDto(r.gamePlan),
    playbookCustomWeights: num(r.playbookCustomWeights),
    playCallingLevel: str(r.playCallingLevel, "KEY_MOMENTS"),
    matchId: nstr(r.matchId),
  };
}

/* ───────────────────────── semana ───────────────────────── */

const status = (v: unknown): MatchSessionStatus => oneOf<MatchSessionStatus>(v, MATCH_SESSION_STATUSES, "LIVE");

function liveLineFromDto(r: Raw): LiveLine {
  return {
    matchId: str(r.matchId),
    status: status(r.status),
    quarter: num(r.quarter),
    clockSeconds: num(r.clockSeconds),
    possessionClubId: nstr(r.possessionClubId),
    down: num(r.down, 1),
    distance: num(r.distance, 10),
    ballOnAbsYd: num(r.ballOnAbsYd, 50),
    homeScore: num(r.homeScore),
    awayScore: num(r.awayScore),
  };
}

function weekGameFromDto(r: Raw): WeekGame {
  return { fixture: fixtureFromDto(r.fixture), home: clubRefFromDto(r.home), away: clubRefFromDto(r.away), live: optional(r.live, liveLineFromDto) };
}

export function weekBoardFromDto(raw: unknown): WeekBoard {
  const r = obj(raw);
  return {
    seasonId: str(r.seasonId),
    week: num(r.week, 1),
    regularSeasonWeeks: num(r.regularSeasonWeeks, 18),
    currentWeek: num(r.currentWeek, 1),
    kind: nstr(r.kind),
    status: nstr(r.status),
    startsOn: nstr(r.startsOn),
    completed: bool(r.completed),
    userBye: bool(r.userBye),
    userFixtureId: nstr(r.userFixtureId),
    matchId: nstr(r.matchId),
    byeClubIds: strs(r.byeClubIds),
    games: list(r.games, weekGameFromDto),
  };
}

/* ───────────────────────── partida ao vivo ───────────────────────── */

export function teamBoxFromDto(raw: unknown): TeamBoxScore {
  const r = obj(raw);
  const out = { clubId: str(r.clubId), pointsByQuarter: nums(r.pointsByQuarter) } as Record<string, unknown>;
  for (const k of TEAM_BOX_FIELDS) if (k !== "clubId" && k !== "pointsByQuarter") out[k] = num(r[k]);
  return out as unknown as TeamBoxScore;
}

const numbers = <T>(r: Raw, keys: readonly string[]): T => Object.fromEntries(keys.map((k) => [k, num(r[k])])) as T;

function defenseFromDto(r: Raw): DefenseLine {
  return { ...numbers<DefenseLine>(r, MATCH_SHAPES.defense.fields.filter((k) => k !== "assistedTackles" && k !== "qbHits")), assistedTackles: nnum(r.assistedTackles), qbHits: nnum(r.qbHits) };
}

export function playerBoxFromDto(r: Raw): PlayerBoxScore {
  return {
    playerId: str(r.playerId),
    clubId: str(r.clubId),
    name: str(r.name),
    position: str(r.position) as Position,
    jerseyNumber: num(r.jerseyNumber),
    snaps: num(r.snaps),
    passing: optional(r.passing, (x) => numbers<PassingLine>(x, MATCH_SHAPES.passing.fields)),
    rushing: optional(r.rushing, (x) => numbers<RushingLine>(x, MATCH_SHAPES.rushing.fields)),
    receiving: optional(r.receiving, (x) => numbers<ReceivingLine>(x, MATCH_SHAPES.receiving.fields)),
    defense: optional(r.defense, defenseFromDto),
    kicking: optional(r.kicking, (x) => numbers<KickingLine>(x, MATCH_SHAPES.kicking.fields)),
    punting: optional(r.punting, (x) => numbers<PuntingLine>(x, MATCH_SHAPES.punting.fields)),
    returns: optional(r.returns, (x) => numbers<ReturnLine>(x, MATCH_SHAPES.returns.fields)),
  };
}

function teamLineFromDto(raw: unknown): TeamLine {
  const r = obj(raw);
  return {
    clubId: str(r.clubId),
    name: str(r.name),
    shortName: str(r.shortName),
    score: num(r.score),
    quarters: nums(r.quarters),
    timeouts: num(r.timeouts),
    stats: teamBoxFromDto(r.stats),
    players: list(r.players, playerBoxFromDto),
  };
}

function penaltyFromDto(r: Raw): Penalty {
  return {
    code: str(r.code),
    againstClubId: str(r.againstClubId),
    playerId: nstr(r.playerId),
    yards: num(r.yards),
    accepted: bool(r.accepted),
    automaticFirstDown: bool(r.automaticFirstDown),
    lossOfDown: bool(r.lossOfDown),
    preSnap: bool(r.preSnap),
  };
}

const CALL_SOURCES: CallSource[] = ["AI", "USER", "AUTO"];

export function eventFromDto(r: Raw): PlayEvent {
  return {
    seq: num(r.seq),
    drive: num(r.drive),
    quarter: num(r.quarter),
    clockSeconds: num(r.clockSeconds),
    possessionClubId: nstr(r.possessionClubId),
    down: num(r.down),
    distance: num(r.distance),
    ballOnAbsYd: num(r.ballOnAbsYd),
    type: nstr(r.type),
    concept: nstr(r.concept),
    formation: nstr(r.formation),
    defensePackage: nstr(r.defensePackage),
    coverage: nstr(r.coverage),
    blitz: bool(r.blitz),
    yards: num(r.yards),
    result: nstr(r.result),
    firstDown: bool(r.firstDown),
    turnover: bool(r.turnover),
    points: num(r.points),
    scoringClubId: nstr(r.scoringClubId),
    penalty: optional(r.penalty, penaltyFromDto),
    playerIds: strs(r.playerIds),
    textKey: str(r.textKey),
    params: strMap(r.params),
    homeScore: num(r.homeScore),
    awayScore: num(r.awayScore),
    clockStopped: bool(r.clockStopped),
    driveEnded: nstr(r.driveEnded),
    callSource: (CALL_SOURCES as string[]).includes(String(r.callSource)) ? (r.callSource as CallSource) : null,
  };
}

export function driveFromDto(r: Raw): Drive {
  return {
    seq: num(r.seq),
    clubId: str(r.clubId),
    quarter: num(r.quarter),
    startClockSeconds: num(r.startClockSeconds),
    startAbsYd: num(r.startAbsYd),
    endAbsYd: num(r.endAbsYd),
    plays: num(r.plays),
    yards: num(r.yards),
    timeOfPossessionSeconds: num(r.timeOfPossessionSeconds),
    reachedRedZone: bool(r.reachedRedZone),
    result: nstr(r.result),
  };
}

function optionFromDto(r: Raw): PlayOption {
  return {
    code: str(r.code),
    side: str(r.side, "OFFENSE"),
    concept: nstr(r.concept),
    formation: nstr(r.formation),
    defensePackage: nstr(r.defensePackage),
    coverage: nstr(r.coverage),
    blitz: bool(r.blitz),
    labelKey: str(r.labelKey, `match:play.${str(r.code)}`),
  };
}

function decisionFromDto(r: Raw): PendingDecision {
  return {
    id: str(r.id),
    situation: str(r.situation),
    side: str(r.side, "OFFENSE"),
    clubId: str(r.clubId),
    quarter: num(r.quarter),
    clockSeconds: num(r.clockSeconds),
    down: num(r.down),
    distance: num(r.distance),
    ballOnAbsYd: num(r.ballOnAbsYd),
    homeScore: num(r.homeScore),
    awayScore: num(r.awayScore),
    options: list(r.options, optionFromDto),
    autoOptionCode: str(r.autoOptionCode),
    openedAtSeq: num(r.openedAtSeq),
  };
}

export function liveMatchFromDto(raw: unknown): LiveMatch {
  const r = obj(raw);
  return {
    fixtureId: str(r.fixtureId),
    version: num(r.version),
    engineVersion: num(r.engineVersion, 1),
    rulesVersion: num(r.rulesVersion, 1),
    ruleset: str(r.ruleset, "PRO"),
    weather: str(r.weather, "CLEAR"),
    status: str(r.status, "LIVE"),
    quarter: num(r.quarter),
    clockSeconds: num(r.clockSeconds),
    playClockSeconds: num(r.playClockSeconds),
    possessionClubId: nstr(r.possessionClubId),
    down: num(r.down, 1),
    distance: num(r.distance, 10),
    ballOnAbsYd: num(r.ballOnAbsYd, 50),
    home: teamLineFromDto(r.home),
    away: teamLineFromDto(r.away),
    decision: optional(r.decision, decisionFromDto),
    lastEvents: list(r.lastEvents, eventFromDto),
    lastDrive: optional(r.lastDrive, driveFromDto),
    driveCount: num(r.driveCount),
  };
}

export function matchStateFromDto(raw: unknown): MatchState {
  const r = obj(raw);
  return {
    matchId: str(r.matchId),
    careerId: str(r.careerId),
    fixtureId: str(r.fixtureId),
    week: num(r.week, 1),
    status: status(r.status),
    userClubId: str(r.userClubId),
    userHome: bool(r.userHome),
    playCallingLevel: str(r.playCallingLevel, "KEY_MOMENTS"),
    adjustable: bool(r.adjustable),
    advanceModes: someOf<AdvanceMode>(r.advanceModes, ADVANCE_MODES),
    decisionDeadlineAt: nstr(r.decisionDeadlineAt),
    decisionTimeoutSeconds: num(r.decisionTimeoutSeconds, 30),
    live: liveMatchFromDto(r.live),
  };
}

export function matchEventsFromDto(raw: unknown): MatchEvents {
  const r = obj(raw);
  return { matchId: str(r.matchId), after: num(r.after), lastSeq: num(r.lastSeq), finished: bool(r.finished), events: list(r.events, eventFromDto) };
}

/* ───────────────────────── relatório ───────────────────────── */

function reportSideFromDto(raw: unknown): ReportSide {
  const r = obj(raw);
  return { club: clubRefFromDto(r.club), score: num(r.score), quarters: nums(r.quarters), stats: teamBoxFromDto(r.stats), players: list(r.players, playerBoxFromDto) };
}

function leaderFromDto(r: Raw): Leader {
  return { category: str(r.category), clubId: str(r.clubId), playerId: str(r.playerId), name: str(r.name), position: str(r.position) as Position, value: num(r.value) };
}

export function decisionRecordFromDto(r: Raw): DecisionRecord {
  return { decisionId: str(r.decisionId), situation: nstr(r.situation), clubId: nstr(r.clubId), optionCode: str(r.optionCode), auto: bool(r.auto), atSeq: num(r.atSeq) };
}

export function matchReportFromDto(raw: unknown): MatchReport {
  const r = obj(raw);
  return {
    fixtureId: str(r.fixtureId),
    matchId: nstr(r.matchId),
    week: num(r.week, 1),
    date: nstr(r.date),
    kickoffSlot: nstr(r.kickoffSlot),
    userGame: bool(r.userGame),
    seed: num(r.seed),
    engineVersion: num(r.engineVersion, 1),
    rulesVersion: num(r.rulesVersion, 1),
    weather: nstr(r.weather),
    home: reportSideFromDto(r.home),
    away: reportSideFromDto(r.away),
    overtime: bool(r.overtime),
    winnerClubId: nstr(r.winnerClubId),
    playCount: num(r.playCount),
    leaders: list(r.leaders, leaderFromDto),
    drives: list(r.drives, driveFromDto),
    scoringPlays: list(r.scoringPlays, eventFromDto),
    decisions: list(r.decisions, decisionRecordFromDto),
  };
}
