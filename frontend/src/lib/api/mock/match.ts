/**
 * Plano de jogo, playbook, preparação, semana e partida ao vivo no modo mock (bloco 6b), com as regras e os códigos de erro do
 * backend: versão otimista (409 *_VERSION_CONFLICT / MATCH_VERSION_CONFLICT), 422 com `details.errors`, depth chart conferido no
 * kickoff (422 DEPTH_CHART_INVALID), fim da temporada (409 NO_NEXT_GAME), modos de avanço que param na decisão do manager e no fim,
 * prazo de 30 s da chamada de jogada (vencido, a IA chama: 409 DECISION_CLOSED na chamada atrasada), ajustes só antes do kickoff,
 * entre quartos e no intervalo (422 ADJUSTMENT_NOT_ALLOWED), tempo técnico com a bola morta (422 TIMEOUT_NOT_ALLOWED) e relatório só
 * no fim (422 MATCH_NOT_FINISHED).
 *
 * O "motor" daqui é um simulador simples de demonstração (D-71): descidas, corridas, passes, punts, field goals, pontos extras,
 * kickoffs e o relógio, com os mesmos `textKey`/`params` do motor real para o play-by-play. Não reproduz o motor Java: só dá à tela
 * um jogo plausível para navegar sem servidor. Estado por carreira dentro do armazenamento do 6a (localStorage ou memória).
 */
import { overall, type Position } from "@/lib/ratings/overall";
import { cardTier } from "@/lib/ratings/tier";
import { ApiError } from "../problem";
import type { ClubRef, DepthIssue } from "../career-types";
import type {
  AdvanceMode,
  AdvanceRequest,
  DecisionRecord,
  DecisionsRequest,
  Drive,
  GamePlan,
  LiveMatch,
  MatchEvents,
  MatchReport,
  MatchSessionStatus,
  MatchState,
  Matchup,
  PendingDecision,
  PlanTendencies,
  PlayCallRequest,
  PlayEvent,
  PlayOption,
  Playbook,
  PlaybookWeights,
  PlayerBoxScore,
  PlayerRef,
  Preparation,
  SaveGamePlanRequest,
  SavePlaybookRequest,
  TeamBoxScore,
  WeekBoard,
} from "../match-types";
import type { LiveMatchRef } from "../match-types";
import { ADVANCE_MODES, BASE_DEFENSES, BLITZ_FREQUENCIES, COVERAGES, DEFENSIVE_FOCUSES, FOURTH_DOWN_AGGRESSIONS, PLAYBOOK_SITUATIONS, TEMPOS } from "../match-types";
import type { UserAccount } from "../types";
import { customWeights, playbookDraftFrom, playbookErrorCode, playbookWeights, validatePlan, validatePlaybook, type PlaybookMeta } from "@/lib/match/plan";
import { mockCareerAccess, type StoredCareer } from "./career";
import { generateRoster, rngFrom, type MockPlayer } from "./players";
import { uuidFrom, hashString } from "./rng";
import { REGULAR_WEEKS, SALARY_CAP, SEASON_YEAR } from "./world-data";

/* ───────────────────────── constantes ───────────────────────── */

const RUN_CONCEPTS = ["INSIDE_RUN", "OUTSIDE_RUN", "QB_SNEAK"];
const PASS_CONCEPTS = ["SCREEN", "SHORT_PASS", "MEDIUM_PASS", "DEEP_PASS", "PLAY_ACTION"];
const META: PlaybookMeta = { situations: [...PLAYBOOK_SITUATIONS], runConcepts: RUN_CONCEPTS, passConcepts: PASS_CONCEPTS, coverages: [...COVERAGES], minWeight: 0, maxWeight: 200, neutralWeight: 100 };
const OPTIONS = { minPassRatio: 20, maxPassRatio: 80, fourthDownAggression: [...FOURTH_DOWN_AGGRESSIONS], tempo: [...TEMPOS], defensiveFocus: [...DEFENSIVE_FOCUSES], blitzFrequency: [...BLITZ_FREQUENCIES], baseDefense: [...BASE_DEFENSES] };
/** Prazo da chamada de jogada (D-61). */
export const DECISION_TIMEOUT_SECONDS = 30;
const QUARTER_SECONDS = 900;
const LAST_EVENTS = 20;
const ENGINE_VERSION = 1;
const RULES_VERSION = 1;

/** Plano padrão por estilo do head coach no mock (o backend tem o seu, D-38). */
const COACH_PLANS: Record<string, PlanTendencies> = {
  OFFENSIVE: { passRatio: 58, fourthDownAggression: "AGGRESSIVE", tempo: "NORMAL", defensiveFocus: "BALANCED", blitzFrequency: "NORMAL", baseDefense: "BASE_3_4" },
  DEFENSIVE: { passRatio: 45, fourthDownAggression: "CONSERVATIVE", tempo: "MILK_CLOCK", defensiveFocus: "STOP_RUN", blitzFrequency: "HIGH", baseDefense: "BASE_4_3" },
  DEVELOPER: { passRatio: 50, fourthDownAggression: "NORMAL", tempo: "NORMAL", defensiveFocus: "BALANCED", blitzFrequency: "NORMAL", baseDefense: "BASE_4_3" },
  MOTIVATOR: { passRatio: 52, fourthDownAggression: "NORMAL", tempo: "HURRY_UP", defensiveFocus: "BALANCED", blitzFrequency: "NORMAL", baseDefense: "BASE_4_3" },
};

const neutralBook = (): PlaybookWeights => playbookWeights({ situations: {}, coverages: {} }, META);

/* ───────────────────────── armazenamento ───────────────────────── */

interface StoredPlan {
  tendencies: PlanTendencies;
  version: number;
  saved: boolean;
  updatedAt: string;
}
interface StoredBook {
  weights: PlaybookWeights;
  version: number;
  updatedAt: string;
}

type SideKey = "H" | "A";
interface MPlayer {
  id: string;
  name: string;
  position: Position;
  jerseyNumber: number;
  rating: number;
}
interface Lineup {
  qb: MPlayer;
  rb: MPlayer;
  wrs: MPlayer[];
  te: MPlayer;
  k: MPlayer;
  p: MPlayer;
  kr: MPlayer;
  dl: MPlayer[];
  lb: MPlayer[];
  db: MPlayer[];
}
interface SideInfo {
  clubId: string;
  name: string;
  shortName: string;
  strength: number;
  lineup: Lineup;
  score: number;
  quarters: number[];
  timeouts: number;
  plan: PlanTendencies;
  book: PlaybookWeights;
}
interface OpenDrive {
  seq: number;
  clubId: string;
  quarter: number;
  startClock: number;
  startAbs: number;
  plays: number;
  yards: number;
  top: number;
  redZone: boolean;
}
interface Session {
  id: string;
  fixtureId: string;
  week: number;
  seed: number;
  rng: number;
  userSide: SideKey;
  level: string;
  status: MatchSessionStatus;
  version: number;
  weather: string;
  quarter: number;
  clock: number;
  possession: SideKey | null;
  down: number;
  distance: number;
  ball: number;
  kickoffBy: SideKey | null;
  patBy: SideKey | null;
  secondHalfKicker: SideKey;
  sides: Record<SideKey, SideInfo>;
  events: PlayEvent[];
  drives: Drive[];
  drive: OpenDrive | null;
  driveCount: number;
  decisions: DecisionRecord[];
  pending: PendingDecision | null;
  deadlineAt: string | null;
  team: Record<SideKey, TeamBoxScore>;
  players: Record<string, PlayerBoxScore>;
}
interface Ext {
  plan?: StoredPlan;
  book?: StoredBook;
  sessions?: Record<string, Session>;
}

const extOf = (c: StoredCareer): Ext => {
  if (!c.ext) c.ext = {};
  return c.ext as Ext;
};

/* ───────────────────────── sorteio guardado na sessão ───────────────────────── */

function rand(s: Session): number {
  const a = (s.rng + 0x6d2b79f5) | 0;
  s.rng = a;
  let t = Math.imul(a ^ (a >>> 15), 1 | a);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const between = (s: Session, min: number, max: number) => min + Math.floor(rand(s) * (max - min + 1));
const chance = (s: Session, p: number) => rand(s) < p;
function weighted<T>(s: Session, items: { v: T; w: number }[]): T {
  const total = items.reduce((a, x) => a + Math.max(0, x.w), 0);
  if (total <= 0) return items[0].v;
  let r = rand(s) * total;
  for (const x of items) {
    r -= Math.max(0, x.w);
    if (r < 0) return x.v;
  }
  return items[items.length - 1].v;
}

/* ───────────────────────── escalações ───────────────────────── */

const toM = (p: MockPlayer): MPlayer => ({ id: p.id, name: p.name, position: p.position, jerseyNumber: p.jerseyNumber, rating: overall(p.attributes, p.position) });

function bestBy(players: MockPlayer[], pos: Position, n: number, exclude = new Set<string>()): MPlayer[] {
  return players
    .filter((p) => p.position === pos && !exclude.has(p.id) && p.rosterStatus === "ACTIVE")
    .map(toM)
    .sort((a, b) => b.rating - a.rating)
    .slice(0, n);
}

function lineupFrom(players: MockPlayer[], chart: Record<string, string[]> | null): Lineup {
  const byId = new Map(players.map((p) => [p.id, p]));
  const take = (slot: Position | "KR", n: number, pos: Position): MPlayer[] => {
    const fromChart = (chart?.[slot] ?? []).map((id) => byId.get(id)).filter((p): p is MockPlayer => !!p && p.rosterStatus === "ACTIVE").map(toM);
    const picked = fromChart.slice(0, n);
    if (picked.length < n) picked.push(...bestBy(players, pos, n - picked.length, new Set(picked.map((p) => p.id))));
    return picked;
  };
  const one = (slot: Position | "KR", pos: Position) => take(slot, 1, pos)[0] ?? { id: uuidFrom("ghost", slot), name: slot, position: pos, jerseyNumber: 0, rating: 50 };
  return {
    qb: one("QB", "QB"),
    rb: one("RB", "RB"),
    wrs: take("WR", 3, "WR"),
    te: one("TE", "TE"),
    k: one("K", "K"),
    p: one("P", "P"),
    kr: one("KR", "WR"),
    dl: [...take("DE", 2, "DE"), ...take("DT", 2, "DT")],
    lb: [...take("OLB", 2, "OLB"), ...take("MLB", 1, "MLB")],
    db: [...take("CB", 2, "CB"), ...take("FS", 1, "FS"), ...take("SS", 1, "SS")],
  };
}

function opponentRoster(careerId: string, clubId: string, prestige: number): MockPlayer[] {
  return generateRoster(`${careerId}:opp:${clubId}`, (i) => uuidFrom("player", `${careerId}:${clubId}:${i}`), prestige, SEASON_YEAR, SALARY_CAP);
}

const avg = (xs: number[]) => (xs.length ? Math.round(xs.reduce((a, x) => a + x, 0) / xs.length) : 0);

/* ───────────────────────── box score ───────────────────────── */

function emptyTeam(clubId: string): TeamBoxScore {
  return {
    clubId, points: 0, pointsByQuarter: [0, 0, 0, 0], firstDowns: 0, firstDownsRushing: 0, firstDownsPassing: 0, firstDownsPenalty: 0, thirdDownConversions: 0,
    thirdDownAttempts: 0, fourthDownConversions: 0, fourthDownAttempts: 0, redZoneTouchdowns: 0, redZoneTrips: 0, offensivePlays: 0, totalYards: 0, yardsPerPlay: 0,
    rushAttempts: 0, rushYards: 0, passCompletions: 0, passAttempts: 0, passYards: 0, sacksAllowed: 0, sackYardsLost: 0, interceptionsThrown: 0, fumbles: 0,
    fumblesLost: 0, turnovers: 0, penalties: 0, penaltyYards: 0, timeOfPossessionSeconds: 0, drives: 0, punts: 0, fieldGoalsMade: 0, fieldGoalAttempts: 0,
    defensiveSacks: 0, timeoutsUsed: 0,
  };
}

function line(s: Session, side: SideKey, p: MPlayer): PlayerBoxScore {
  let l = s.players[p.id];
  if (!l) {
    l = { playerId: p.id, clubId: s.sides[side].clubId, name: p.name, position: p.position, jerseyNumber: p.jerseyNumber, snaps: 0, passing: null, rushing: null, receiving: null, defense: null, kicking: null, punting: null, returns: null };
    s.players[p.id] = l;
  }
  return l;
}
const passing = (l: PlayerBoxScore) => (l.passing ??= { completions: 0, attempts: 0, yards: 0, touchdowns: 0, interceptions: 0, sacked: 0, sackYards: 0, rating: 0, longest: 0 });
const rushing = (l: PlayerBoxScore) => (l.rushing ??= { attempts: 0, yards: 0, average: 0, touchdowns: 0, fumbles: 0, fumblesLost: 0, longest: 0 });
const receiving = (l: PlayerBoxScore) => (l.receiving ??= { targets: 0, receptions: 0, yards: 0, yardsAfterCatch: 0, touchdowns: 0, drops: 0, longest: 0 });
const defense = (l: PlayerBoxScore) => (l.defense ??= { soloTackles: 0, assistedTackles: null, tacklesForLoss: 0, sacks: 0, qbHits: null, interceptions: 0, passesDefended: 0, forcedFumbles: 0, fumbleRecoveries: 0, touchdowns: 0 });
const kicking = (l: PlayerBoxScore) => (l.kicking ??= { fgMade: 0, fgAttempts: 0, fgMade0to39: 0, fgAttempts0to39: 0, fgMade40to49: 0, fgAttempts40to49: 0, fgMade50plus: 0, fgAttempts50plus: 0, longest: 0, patMade: 0, patAttempts: 0 });
const punting = (l: PlayerBoxScore) => (l.punting ??= { punts: 0, yards: 0, average: 0, inside20: 0, touchbacks: 0, longest: 0 });

/** Rating de passe da liga profissional (0–158,3). */
function passerRating(c: number, a: number, y: number, td: number, int: number): number {
  if (!a) return 0;
  const cl = (v: number) => Math.max(0, Math.min(2.375, v));
  const r = ((cl((c / a - 0.3) * 5) + cl((y / a - 3) * 0.25) + cl((td / a) * 20) + cl(2.375 - (int / a) * 25)) / 6) * 100;
  return Math.round(r * 10) / 10;
}

function finishLines(s: Session): void {
  for (const l of Object.values(s.players)) {
    if (l.passing) l.passing.rating = passerRating(l.passing.completions, l.passing.attempts, l.passing.yards, l.passing.touchdowns, l.passing.interceptions);
    if (l.rushing) l.rushing.average = l.rushing.attempts ? Math.round((l.rushing.yards / l.rushing.attempts) * 10) / 10 : 0;
    if (l.punting) l.punting.average = l.punting.punts ? Math.round((l.punting.yards / l.punting.punts) * 10) / 10 : 0;
  }
  for (const k of ["H", "A"] as SideKey[]) {
    const t = s.team[k];
    t.points = s.sides[k].score;
    t.pointsByQuarter = [...s.sides[k].quarters];
    t.yardsPerPlay = t.offensivePlays ? Math.round((t.totalYards / t.offensivePlays) * 10) / 10 : 0;
    t.turnovers = t.interceptionsThrown + t.fumblesLost;
    t.timeoutsUsed = 3 - s.sides[k].timeouts;
  }
}

/* ───────────────────────── lances ───────────────────────── */

const other = (k: SideKey): SideKey => (k === "H" ? "A" : "H");
/** A casa ataca rumo à jarda 100; o visitante rumo à 0. */
const dir = (k: SideKey) => (k === "H" ? 1 : -1);
const toGoal = (s: Session, k: SideKey) => (k === "H" ? 100 - s.ball : s.ball);

function push(s: Session, e: Partial<PlayEvent> & { textKey: string; params?: Record<string, string> }): PlayEvent {
  const pos = s.possession;
  const ev: PlayEvent = {
    seq: s.events.length + 1,
    drive: s.drive ? s.drive.seq : s.driveCount,
    quarter: s.quarter,
    clockSeconds: s.clock,
    possessionClubId: pos ? s.sides[pos].clubId : null,
    down: s.down,
    distance: s.distance,
    ballOnAbsYd: s.ball,
    type: null,
    concept: null,
    formation: null,
    defensePackage: null,
    coverage: null,
    blitz: false,
    yards: 0,
    result: null,
    firstDown: false,
    turnover: false,
    points: 0,
    scoringClubId: null,
    penalty: null,
    playerIds: [],
    params: {},
    homeScore: s.sides.H.score,
    awayScore: s.sides.A.score,
    clockStopped: false,
    driveEnded: null,
    callSource: "AI",
    ...e,
  };
  s.events.push(ev);
  return ev;
}

function startDrive(s: Session, k: SideKey): void {
  s.driveCount += 1;
  s.drive = { seq: s.driveCount, clubId: s.sides[k].clubId, quarter: s.quarter, startClock: s.clock, startAbs: s.ball, plays: 0, yards: 0, top: 0, redZone: false };
  s.team[k].drives += 1;
}

function endDrive(s: Session, result: string): void {
  const d = s.drive;
  if (!d) return;
  s.drives.push({ seq: d.seq, clubId: d.clubId, quarter: d.quarter, startClockSeconds: d.startClock, startAbsYd: d.startAbs, endAbsYd: s.ball, plays: d.plays, yards: d.yards, timeOfPossessionSeconds: d.top, reachedRedZone: d.redZone, result });
  const last = s.events[s.events.length - 1];
  if (last) last.driveEnded = result;
  s.drive = null;
}

function score(s: Session, k: SideKey, points: number): void {
  s.sides[k].score += points;
  const q = Math.min(s.quarter, 4) - 1;
  s.sides[k].quarters[q] = (s.sides[k].quarters[q] ?? 0) + points;
}

function spendClock(s: Session, seconds: number, k: SideKey | null): void {
  const used = Math.min(s.clock, seconds);
  s.clock -= used;
  if (k) {
    s.team[k].timeOfPossessionSeconds += used;
    if (s.drive) s.drive.top += used;
  }
}

function playClock(s: Session, k: SideKey, stopped: boolean): number {
  if (stopped) return between(s, 4, 7);
  const tempo = s.sides[k].plan.tempo;
  return tempo === "HURRY_UP" ? between(s, 18, 26) : tempo === "MILK_CLOCK" ? between(s, 36, 42) : between(s, 27, 35);
}

/* ───────────────────────── chamadas ───────────────────────── */

function situationOf(s: Session, k: SideKey): string {
  if (s.clock <= 120 && (s.quarter === 2 || s.quarter === 4)) return "TWO_MINUTE";
  if (toGoal(s, k) <= 20) return "RED_ZONE";
  if (s.down === 3) return s.distance >= 7 ? "THIRD_AND_LONG" : "THIRD_DOWN";
  return "EARLY_DOWN";
}

function weightOf(book: PlaybookWeights, situation: string, item: string): number {
  return book.situations.find((x) => x.situation === situation)?.concepts.find((c) => c.item === item)?.weight ?? 100;
}

/** Chamada da IA numa descida normal: corrida × passe pelo plano, qual corrida/passe pelos pesos do playbook (D-58). */
function aiConcept(s: Session, k: SideKey): string {
  const side = s.sides[k];
  const sit = situationOf(s, k);
  const pass = chance(s, side.plan.passRatio / 100 + (s.distance >= 8 ? 0.15 : s.distance <= 2 ? -0.2 : 0));
  const pool = pass ? PASS_CONCEPTS : s.distance <= 1 ? RUN_CONCEPTS : RUN_CONCEPTS.filter((c) => c !== "QB_SNEAK");
  return weighted(s, pool.map((c) => ({ v: c, w: weightOf(side.book, sit, c) })));
}

const fgDistance = (s: Session, k: SideKey) => toGoal(s, k) + 17;

/** Decisão de 4ª descida da IA (pela agressividade do plano). */
function aiFourth(s: Session, k: SideKey): string {
  const g = toGoal(s, k);
  const aggr = s.sides[k].plan.fourthDownAggression;
  const goLimit = aggr === "AGGRESSIVE" ? 3 : aggr === "NORMAL" ? 1 : 0;
  const trailingLate = s.quarter === 4 && s.clock < 300 && s.sides[k].score < s.sides[other(k)].score;
  if (trailingLate || (s.distance <= goLimit && g <= 60)) return s.distance <= 1 ? "QB_SNEAK" : "SHORT_PASS";
  if (fgDistance(s, k) <= 52) return "FIELD_GOAL";
  return "PUNT";
}

function offenseOptions(s: Session, k: SideKey): PlayOption[] {
  const codes = ["PUNT", ...(fgDistance(s, k) <= 60 ? ["FIELD_GOAL"] : []), ...(s.distance <= 2 ? ["QB_SNEAK"] : []), "INSIDE_RUN", "SHORT_PASS", "MEDIUM_PASS"];
  return codes.map((code) => ({ code, side: "OFFENSE", concept: code, formation: code === "PUNT" || code === "FIELD_GOAL" ? null : code === "QB_SNEAK" ? "GOAL_LINE" : "SHOTGUN", defensePackage: null, coverage: null, blitz: false, labelKey: `match:play.${code}` }));
}

const DEF_OPTIONS: Record<string, [string, string, boolean]> = {
  DEF_BASE_COVER_3: ["BASE_4_3", "COVER_3", false],
  DEF_NICKEL_COVER_2: ["NICKEL", "COVER_2", false],
  DEF_DIME_COVER_4: ["DIME", "COVER_4", false],
  DEF_BLITZ_COVER_1: ["NICKEL", "COVER_1", true],
  DEF_GOAL_LINE_COVER_0: ["GOAL_LINE", "COVER_0", true],
};

function defenseOptions(s: Session, k: SideKey): PlayOption[] {
  const base = s.sides[k].plan.baseDefense;
  const codes = ["DEF_BASE_COVER_3", "DEF_NICKEL_COVER_2", "DEF_BLITZ_COVER_1", s.distance <= 2 ? "DEF_GOAL_LINE_COVER_0" : "DEF_DIME_COVER_4"];
  return codes.map((code) => {
    const [pkg, cov, blitz] = DEF_OPTIONS[code];
    return { code, side: "DEFENSE", concept: null, formation: null, defensePackage: code === "DEF_BASE_COVER_3" ? base : pkg, coverage: cov, blitz, labelKey: `match:play.${code}` };
  });
}

function openDecision(s: Session, side: "OFFENSE" | "DEFENSE", options: PlayOption[], auto: string): void {
  const user = s.userSide;
  s.pending = {
    id: `dec-${s.events.length + 1}`,
    situation: "FOURTH_DOWN",
    side,
    clubId: s.sides[user].clubId,
    quarter: s.quarter,
    clockSeconds: s.clock,
    down: s.down,
    distance: s.distance,
    ballOnAbsYd: s.ball,
    homeScore: s.sides.H.score,
    awayScore: s.sides.A.score,
    options,
    autoOptionCode: auto,
    openedAtSeq: s.events.length,
  };
  s.deadlineAt = new Date(Date.now() + DECISION_TIMEOUT_SECONDS * 1000).toISOString();
  s.status = "AWAITING_DECISION";
}

/* ───────────────────────── uma jogada ───────────────────────── */

interface Call {
  /** opção escolhida na decisão aberta (ataque ou defesa) */
  code: string;
  source: "USER" | "AUTO";
}

function kickoff(s: Session): void {
  const kicker = s.kickoffBy!;
  const recv = other(kicker);
  s.kickoffBy = null;
  s.possession = recv;
  const kk = s.sides[kicker].lineup.k;
  const kr = s.sides[recv].lineup.kr;
  s.ball = recv === "H" ? 25 : 75;
  s.down = 1;
  s.distance = 10;
  if (chance(s, 0.62)) {
    push(s, { type: "KICKOFF", result: "TOUCHBACK", textKey: "match:event.kickoff.touchback", params: { kicker: kk.name }, playerIds: [kk.id], clockStopped: true });
  } else {
    const yards = between(s, 16, 38);
    s.ball = recv === "H" ? yards : 100 - yards;
    spendClock(s, between(s, 4, 7), null);
    push(s, { type: "KICKOFF", result: "KICK_RETURNED", yards, textKey: "match:event.kickoff.return", params: { returner: kr.name, yards: String(yards) }, playerIds: [kr.id] });
  }
  startDrive(s, recv);
}

function conversion(s: Session, call: Call | null): void {
  const k = s.patBy!;
  s.patBy = null;
  const kk = s.sides[k].lineup.k;
  const two = call?.code === "TWO_POINT_RUN" || call?.code === "TWO_POINT_PASS";
  if (two) {
    const ok = chance(s, 0.47);
    if (ok) score(s, k, 2);
    push(s, { type: "TWO_POINT", result: ok ? "TWO_POINT_GOOD" : "TWO_POINT_FAILED", points: ok ? 2 : 0, scoringClubId: ok ? s.sides[k].clubId : null, textKey: ok ? "match:event.twoPoint.good" : "match:event.twoPoint.failed", callSource: call!.source, clockStopped: true });
  } else {
    const ok = chance(s, 0.95);
    const kl = kicking(line(s, k, kk));
    kl.patAttempts += 1;
    if (ok) {
      kl.patMade += 1;
      score(s, k, 1);
    }
    push(s, { type: "EXTRA_POINT", result: ok ? "EXTRA_POINT_GOOD" : "EXTRA_POINT_MISSED", points: ok ? 1 : 0, scoringClubId: ok ? s.sides[k].clubId : null, textKey: ok ? "match:event.extraPoint.good" : "match:event.extraPoint.missed", params: { kicker: kk.name }, playerIds: [kk.id], clockStopped: true });
  }
  s.kickoffBy = k;
}

function changePossession(s: Session, to: SideKey, result: string): void {
  endDrive(s, result);
  s.possession = to;
  s.down = 1;
  s.distance = Math.min(10, toGoal(s, to));
  startDrive(s, to);
}

function touchdown(s: Session, k: SideKey): void {
  score(s, k, 6);
  const d = s.drive;
  if (d?.redZone) s.team[k].redZoneTouchdowns += 1;
  s.ball = k === "H" ? 100 : 0;
  endDrive(s, "TD");
  s.patBy = k;
}

/** Resolve uma jogada de scrimmage, punt ou field goal do time `k` com o conceito `concept`. */
function scrimmage(s: Session, k: SideKey, concept: string, call: Call | null, defCall: string | null): void {
  const off = s.sides[k];
  const defK = other(k);
  const def = s.sides[defK];
  const T = s.team[k];
  const edge = (off.strength - def.strength) / 12;
  const callSource = call?.source ?? "AI";
  const [dp, cov, blitz] = defCall ? DEF_OPTIONS[defCall] : [def.plan.baseDefense, weighted(s, def.book.coverages.map((c) => ({ v: c.item, w: c.weight }))), chance(s, def.plan.blitzFrequency === "HIGH" ? 0.3 : def.plan.blitzFrequency === "LOW" ? 0.08 : 0.16)];
  const base = { concept, defensePackage: dp, coverage: cov, blitz, callSource, down: s.down, distance: s.distance, ballOnAbsYd: s.ball } as const;
  const startBall = s.ball;
  const wasThird = s.down === 3, wasFourth = s.down === 4;
  if (s.drive && !s.drive.redZone && toGoal(s, k) <= 20) {
    s.drive.redZone = true;
    T.redZoneTrips += 1;
  }

  if (concept === "PUNT") {
    const p = off.lineup.p;
    const dist = Math.min(between(s, 38, 54), toGoal(s, k) + 10);
    const landing = toGoal(s, k) - dist;
    const pl = punting(line(s, k, p));
    pl.punts += 1;
    pl.yards += dist;
    pl.longest = Math.max(pl.longest, dist);
    T.punts += 1;
    spendClock(s, between(s, 6, 9), k);
    if (landing <= 0) {
      pl.touchbacks += 1;
      s.ball = defK === "H" ? 20 : 80;
      push(s, { ...base, type: "PUNT", result: "TOUCHBACK", yards: dist, textKey: "match:event.punt.touchback", params: { punter: p.name, yards: String(dist) }, playerIds: [p.id], clockStopped: true });
    } else {
      if (landing <= 20) pl.inside20 += 1;
      s.ball = defK === "H" ? landing : 100 - landing;
      const fair = chance(s, 0.4);
      const ret = def.lineup.kr;
      if (fair) push(s, { ...base, type: "PUNT", result: "FAIR_CATCH", yards: dist, textKey: "match:event.punt.fairCatch", params: { punter: p.name, yards: String(dist), returner: ret.name }, playerIds: [p.id, ret.id], clockStopped: true });
      else {
        const ry = between(s, 0, 14);
        s.ball = Math.max(1, Math.min(99, s.ball + dir(defK) * ry));
        push(s, { ...base, type: "PUNT", result: "KICK_RETURNED", yards: dist, textKey: "match:event.punt.return", params: { punter: p.name, yards: String(dist), returner: ret.name, returnYards: String(ry) }, playerIds: [p.id, ret.id] });
      }
    }
    changePossession(s, defK, "PUNT");
    return;
  }

  if (concept === "FIELD_GOAL") {
    const kk = off.lineup.k;
    const distance = fgDistance(s, k);
    const p = distance < 40 ? 0.92 : distance < 50 ? 0.78 : 0.55;
    const ok = chance(s, p);
    const kl = kicking(line(s, k, kk));
    kl.fgAttempts += 1;
    if (distance < 40) kl.fgAttempts0to39 += 1;
    else if (distance < 50) kl.fgAttempts40to49 += 1;
    else kl.fgAttempts50plus += 1;
    T.fieldGoalAttempts += 1;
    spendClock(s, between(s, 4, 6), k);
    if (ok) {
      kl.fgMade += 1;
      if (distance < 40) kl.fgMade0to39 += 1;
      else if (distance < 50) kl.fgMade40to49 += 1;
      else kl.fgMade50plus += 1;
      kl.longest = Math.max(kl.longest, distance);
      T.fieldGoalsMade += 1;
      score(s, k, 3);
      push(s, { ...base, type: "FIELD_GOAL", result: "FIELD_GOAL_GOOD", points: 3, scoringClubId: off.clubId, textKey: "match:event.fieldGoal.good", params: { kicker: kk.name, distance: String(distance) }, playerIds: [kk.id], clockStopped: true });
      endDrive(s, "FG");
      s.kickoffBy = k;
    } else {
      push(s, { ...base, type: "FIELD_GOAL", result: "FIELD_GOAL_MISSED", textKey: "match:event.fieldGoal.missed", params: { kicker: kk.name, distance: String(distance) }, playerIds: [kk.id], clockStopped: true });
      s.ball = Math.max(defK === "H" ? 20 : 0, Math.min(defK === "H" ? 100 : 80, s.ball - dir(k) * 7));
      changePossession(s, defK, "MISSED_FG");
    }
    return;
  }

  T.offensivePlays += 1;
  if (s.drive) s.drive.plays += 1;
  if (wasThird) T.thirdDownAttempts += 1;
  if (wasFourth) T.fourthDownAttempts += 1;
  const qb = off.lineup.qb;
  const tackler = [...def.lineup.lb, ...def.lineup.db, ...def.lineup.dl][between(s, 0, 9)] ?? def.lineup.lb[0];
  const isRun = RUN_CONCEPTS.includes(concept);
  const goalLineStuff = defCall === "DEF_GOAL_LINE_COVER_0" && isRun ? -1.5 : 0;
  let yards = 0;
  let stopped = false;
  let turnover: "INT" | "FUMBLE" | null = null;
  let textKey = "";
  let params: Record<string, string> = {};
  let playerIds: string[] = [];
  let result = "GAIN";
  const type = isRun ? "RUN" : "PASS";
  line(s, k, qb).snaps += 1;

  if (isRun) {
    const rusher = concept === "QB_SNEAK" ? qb : off.lineup.rb;
    yards = concept === "QB_SNEAK" ? between(s, 0, 3) : Math.round(between(s, -2, 8) + edge + goalLineStuff + (chance(s, 0.06) ? between(s, 10, 45) : 0));
    const rl = rushing(line(s, k, rusher));
    T.rushAttempts += 1;
    if (chance(s, 0.012)) {
      turnover = "FUMBLE";
      rl.fumbles += 1;
      rl.fumblesLost += 1;
      T.fumbles += 1;
      T.fumblesLost += 1;
      defense(line(s, defK, tackler)).fumbleRecoveries += 1;
      textKey = "match:event.run.fumble";
      params = { rusher: rusher.name, recoveredBy: tackler.name };
      result = "FUMBLE_LOST";
      yards = Math.max(0, yards);
    } else {
      textKey = "match:event.run.gain";
      params = { rusher: rusher.name, yards: String(yards) };
    }
    playerIds = [rusher.id, tackler.id];
    yards = clampYards(s, k, yards);
    rl.attempts += 1;
    rl.yards += yards;
    rl.longest = Math.max(rl.longest, yards);
    T.rushYards += yards;
    if (yards >= s.distance && !turnover) T.firstDownsRushing += 1;
  } else {
    const blitzBonus = blitz ? 0.05 : 0;
    const targets = [...off.lineup.wrs, off.lineup.te, off.lineup.rb];
    const target = concept === "SCREEN" ? off.lineup.rb : weighted(s, targets.map((t, i) => ({ v: t, w: [5, 3, 2, 2, 1][i] ?? 1 })));
    const pl = passing(line(s, k, qb));
    if (chance(s, 0.06 + blitzBonus)) {
      const sacker = def.lineup.dl[between(s, 0, def.lineup.dl.length - 1)] ?? tackler;
      const loss = between(s, 3, 9);
      yards = clampYards(s, k, -loss);
      pl.sacked += 1;
      pl.sackYards += -yards;
      T.sacksAllowed += 1;
      T.sackYardsLost += -yards;
      s.team[defK].defensiveSacks += 1;
      const dl = defense(line(s, defK, sacker));
      dl.sacks += 1;
      dl.soloTackles += 1;
      dl.tacklesForLoss += 1;
      textKey = "match:event.sack";
      params = { passer: qb.name, sackedBy: sacker.name, yards: String(-yards) };
      playerIds = [qb.id, sacker.id];
      result = "SACK";
    } else {
      const p = { SCREEN: 0.76, SHORT_PASS: 0.7, MEDIUM_PASS: 0.58, DEEP_PASS: 0.4, PLAY_ACTION: 0.6 }[concept] ?? 0.6;
      pl.attempts += 1;
      T.passAttempts += 1;
      const rl = receiving(line(s, k, target));
      rl.targets += 1;
      if (chance(s, 0.025 + blitzBonus / 2)) {
        const picker = def.lineup.db[between(s, 0, def.lineup.db.length - 1)] ?? tackler;
        turnover = "INT";
        pl.interceptions += 1;
        T.interceptionsThrown += 1;
        defense(line(s, defK, picker)).interceptions += 1;
        textKey = "match:event.pass.interception";
        params = { passer: qb.name, interceptedBy: picker.name };
        playerIds = [qb.id, picker.id];
        result = "INTERCEPTION";
        yards = 0;
        stopped = true;
      } else if (chance(s, Math.min(0.92, p + edge / 20 - blitzBonus))) {
        const range = { SCREEN: [-2, 9], SHORT_PASS: [3, 10], MEDIUM_PASS: [9, 19], DEEP_PASS: [18, 45], PLAY_ACTION: [8, 26] }[concept] ?? [4, 12];
        yards = clampYards(s, k, between(s, range[0], range[1]) + Math.round(edge));
        pl.completions += 1;
        pl.yards += yards;
        pl.longest = Math.max(pl.longest, yards);
        rl.receptions += 1;
        rl.yards += yards;
        rl.yardsAfterCatch += Math.max(0, Math.round(yards * 0.4));
        rl.longest = Math.max(rl.longest, yards);
        T.passCompletions += 1;
        T.passYards += yards;
        textKey = "match:event.pass.complete";
        params = { passer: qb.name, receiver: target.name, yards: String(yards) };
        playerIds = [qb.id, target.id, tackler.id];
        result = "COMPLETE";
        if (yards >= s.distance) T.firstDownsPassing += 1;
      } else {
        textKey = "match:event.pass.incomplete";
        params = { passer: qb.name };
        playerIds = [qb.id, target.id];
        result = "INCOMPLETE";
        stopped = true;
      }
    }
  }
  if (result !== "INCOMPLETE" && result !== "INTERCEPTION" && result !== "SACK") defense(line(s, defK, tackler)).soloTackles += 1;
  T.totalYards += yards;
  if (s.drive) s.drive.yards += yards;
  s.ball = Math.max(0, Math.min(100, startBall + dir(k) * yards));
  spendClock(s, playClock(s, k, stopped), k);

  const td = !turnover && toGoal(s, k) <= 0;
  if (td) {
    if (textKey === "match:event.run.gain") textKey = "match:event.run.touchdown";
    if (textKey === "match:event.pass.complete") textKey = "match:event.pass.touchdown";
    const scorer = s.players[playerIds[textKey === "match:event.pass.touchdown" ? 1 : 0]];
    if (scorer?.rushing && isRun) scorer.rushing.touchdowns += 1;
    if (scorer?.receiving && !isRun) scorer.receiving.touchdowns += 1;
    if (!isRun) passing(line(s, k, qb)).touchdowns += 1;
    const ev = push(s, { ...base, type, result: "TOUCHDOWN", yards, firstDown: true, points: 6, scoringClubId: off.clubId, textKey, params, playerIds, clockStopped: true });
    touchdown(s, k);
    ev.homeScore = s.sides.H.score;
    ev.awayScore = s.sides.A.score;
    if (wasThird) T.thirdDownConversions += 1;
    if (wasFourth) T.fourthDownConversions += 1;
    T.firstDowns += 1;
    return;
  }
  const first = !turnover && yards >= s.distance;
  push(s, { ...base, type, result, yards, firstDown: first, turnover: !!turnover, textKey, params, playerIds, clockStopped: stopped });
  if (turnover) {
    changePossession(s, defK, "TURNOVER");
    return;
  }
  if (first) {
    T.firstDowns += 1;
    if (wasThird) T.thirdDownConversions += 1;
    if (wasFourth) T.fourthDownConversions += 1;
    s.down = 1;
    s.distance = Math.min(10, toGoal(s, k));
  } else if (s.down === 4) {
    changePossession(s, defK, "DOWNS");
  } else {
    s.down += 1;
    s.distance -= yards;
  }
}

/** Jardas que cabem no campo (até a end zone do adversário; sem safety no mock). */
function clampYards(s: Session, k: SideKey, yards: number): number {
  const g = toGoal(s, k);
  const own = 100 - g;
  return Math.max(-(own - 1), Math.min(g, yards));
}

/** Fim de período depois de um lance: BREAK entre quartos, HALFTIME no intervalo, FINISHED no fim (empate no mock, sem prorrogação). */
function checkPeriod(s: Session): void {
  if (s.clock > 0 || s.patBy) return;
  const q = s.quarter;
  if (q === 4) {
    endDrive(s, "END_HALF");
    push(s, { type: "END_OF_PERIOD", result: "END_OF_REGULATION", textKey: "match:event.endOfRegulation", clockStopped: true });
    push(s, { type: "END_OF_PERIOD", result: "END_OF_GAME", textKey: "match:event.final", clockStopped: true });
    s.status = "FINISHED";
    s.possession = null;
    return;
  }
  if (q === 2) {
    endDrive(s, "END_HALF");
    push(s, { type: "END_OF_PERIOD", result: "END_OF_HALF", textKey: "match:event.endOfHalf", clockStopped: true });
    s.quarter = 3;
    s.clock = QUARTER_SECONDS;
    s.status = "HALFTIME";
    s.kickoffBy = s.secondHalfKicker;
    s.possession = null;
    return;
  }
  push(s, { type: "END_OF_PERIOD", result: "END_OF_QUARTER", textKey: "match:event.endOfQuarter", params: { quarter: String(q) }, clockStopped: true });
  s.quarter = q + 1;
  s.clock = QUARTER_SECONDS;
  s.status = "BREAK";
}

/**
 * Um passo do jogo: kickoff, conversão ou jogada de scrimmage. Na 4ª descida que envolve o usuário (nível KEY_MOMENTS), abre a
 * decisão e para sem jogar; `call` resolve a decisão aberta.
 */
function step(s: Session, call: Call | null): void {
  if (s.status === "FINISHED") return;
  if (s.status !== "LIVE") s.status = "LIVE";
  if (s.kickoffBy) {
    kickoff(s);
    checkPeriod(s);
    return;
  }
  if (s.patBy) {
    conversion(s, call);
    return;
  }
  const k = s.possession!;
  const user = s.userSide;
  if (s.down === 4) {
    const ai = aiFourth(s, k);
    if (!call && s.level !== "NONE") {
      if (k === user) return openDecision(s, "OFFENSE", offenseOptions(s, k), ai);
      if (ai !== "PUNT" && ai !== "FIELD_GOAL") return openDecision(s, "DEFENSE", defenseOptions(s, user), "DEF_BASE_COVER_3");
    }
    const concept = call && k === user ? call.code : ai;
    const defCall = call && k !== user ? call.code : null;
    scrimmage(s, k, concept, call && k === user ? call : null, defCall);
  } else {
    scrimmage(s, k, aiConcept(s, k), null, null);
  }
  checkPeriod(s);
}

function resolvePending(s: Session, code: string, source: "USER" | "AUTO"): void {
  const d = s.pending!;
  s.decisions.push({ decisionId: d.id, situation: d.situation, clubId: d.clubId, optionCode: code, auto: source === "AUTO", atSeq: s.events.length + 1 });
  s.pending = null;
  s.deadlineAt = null;
  s.status = "LIVE";
  step(s, { code, source });
  const last = s.events[s.events.length - 1];
  if (last) last.callSource = source;
}

const expired = (s: Session, now = Date.now()) => !!s.deadlineAt && Date.parse(s.deadlineAt) <= now;

/* ───────────────────────── leituras ───────────────────────── */

function liveOf(s: Session): LiveMatch {
  finishLines(s);
  const side = (k: SideKey) => {
    const x = s.sides[k];
    const players = Object.values(s.players).filter((p) => p.clubId === x.clubId);
    return { clubId: x.clubId, name: x.name, shortName: x.shortName, score: x.score, quarters: [...x.quarters], timeouts: x.timeouts, stats: { ...s.team[k], pointsByQuarter: [...x.quarters] }, players: structuredClone(players) };
  };
  const engineStatus = s.status === "AWAITING_DECISION" ? "AWAITING_DECISION" : s.status === "FINISHED" ? "FINISHED" : s.status === "NOT_STARTED" ? "NOT_STARTED" : "LIVE";
  return {
    fixtureId: s.fixtureId,
    version: s.version,
    engineVersion: ENGINE_VERSION,
    rulesVersion: RULES_VERSION,
    ruleset: "PRO",
    weather: s.weather,
    status: engineStatus,
    quarter: s.quarter,
    clockSeconds: s.clock,
    playClockSeconds: 40,
    possessionClubId: s.possession ? s.sides[s.possession].clubId : null,
    down: s.down,
    distance: s.distance,
    ballOnAbsYd: s.ball,
    home: side("H"),
    away: side("A"),
    decision: s.pending ? structuredClone(s.pending) : null,
    lastEvents: s.events.slice(-LAST_EVENTS),
    lastDrive: s.drives.length ? s.drives[s.drives.length - 1] : null,
    driveCount: s.driveCount,
  };
}

function stateOf(s: Session, careerId: string): MatchState {
  const modes: AdvanceMode[] = s.status === "FINISHED" || (s.status === "AWAITING_DECISION" && !expired(s)) ? [] : [...ADVANCE_MODES];
  return {
    matchId: s.id,
    careerId,
    fixtureId: s.fixtureId,
    week: s.week,
    status: s.status,
    userClubId: s.sides[s.userSide].clubId,
    userHome: s.userSide === "H",
    playCallingLevel: s.level,
    adjustable: s.status === "NOT_STARTED" || s.status === "BREAK" || s.status === "HALFTIME",
    advanceModes: modes,
    decisionDeadlineAt: s.status === "AWAITING_DECISION" ? s.deadlineAt : null,
    decisionTimeoutSeconds: DECISION_TIMEOUT_SECONDS,
    live: liveOf(s),
  };
}

function leaders(s: Session): MatchReport["leaders"] {
  const all = Object.values(s.players);
  const out: MatchReport["leaders"] = [];
  const best = (cat: string, value: (p: PlayerBoxScore) => number) => {
    const top = all.map((p) => ({ p, v: value(p) })).filter((x) => x.v > 0).sort((a, b) => b.v - a.v)[0];
    if (top) out.push({ category: cat, clubId: top.p.clubId, playerId: top.p.playerId, name: top.p.name, position: top.p.position, value: top.v });
  };
  best("PASSING", (p) => p.passing?.yards ?? 0);
  best("RUSHING", (p) => p.rushing?.yards ?? 0);
  best("RECEIVING", (p) => p.receiving?.yards ?? 0);
  best("TACKLES", (p) => p.defense?.soloTackles ?? 0);
  best("SACKS", (p) => p.defense?.sacks ?? 0);
  return out;
}

/* ───────────────────────── fachada ───────────────────────── */

export interface MockMatches {
  gamePlan(careerId: string): GamePlan;
  saveGamePlan(careerId: string, req: SaveGamePlanRequest): GamePlan;
  playbook(careerId: string): Playbook;
  savePlaybook(careerId: string, req: SavePlaybookRequest): Playbook;
  preparation(careerId: string): Preparation;
  playCurrent(careerId: string): WeekBoard;
  week(careerId: string, week: number): WeekBoard;
  get(matchId: string): MatchState;
  advance(matchId: string, req?: AdvanceRequest): MatchState;
  decisions(matchId: string, req: DecisionsRequest): MatchState;
  playCall(matchId: string, decisionId: string, req: PlayCallRequest): MatchState;
  events(matchId: string, after?: number, limit?: number): MatchEvents;
  report(matchId: string): MatchReport;
  liveRef(careerId: string): LiveMatchRef | null;
}

export function mockMatches(user: UserAccount): MockMatches {
  const acc = mockCareerAccess(user);

  const planOf = (c: StoredCareer): StoredPlan => {
    const e = extOf(c);
    if (!e.plan) e.plan = { tendencies: { ...(COACH_PLANS[c.coachStyle] ?? COACH_PLANS.OFFENSIVE) }, version: 0, saved: false, updatedAt: new Date().toISOString() };
    return e.plan;
  };
  const bookOf = (c: StoredCareer): StoredBook => {
    const e = extOf(c);
    if (!e.book) e.book = { weights: neutralBook(), version: 0, updatedAt: new Date().toISOString() };
    return e.book;
  };
  const planDto = (c: StoredCareer): GamePlan => {
    const p = planOf(c);
    return { id: uuidFrom("game-plan", c.summary.id), clubId: acc.club(c).id, version: p.version, ...p.tendencies, coachStyle: c.coachStyle, source: p.saved ? "SAVED" : "COACH_STYLE", editable: c.summary.status === "ACTIVE", updatedAt: p.updatedAt, options: structuredClone(OPTIONS) };
  };
  const bookDto = (c: StoredCareer): Playbook => {
    const b = bookOf(c);
    return { id: uuidFrom("playbook", c.summary.id), clubId: acc.club(c).id, version: b.version, ...META, runConcepts: [...RUN_CONCEPTS], passConcepts: [...PASS_CONCEPTS], situations: structuredClone(b.weights.situations), coverages: structuredClone(b.weights.coverages), customWeights: customWeights(playbookDraftFrom(b.weights), META), updatedAt: b.updatedAt };
  };
  const conflict = (code: string, current: number, expected: number | null | undefined) =>
    new ApiError({ status: 409, code, message: "", details: { currentVersion: current, expectedVersion: expected ?? null }, correlationId: null, locale: null, params: { 0: expected ?? "—", 1: current } });

  const checkPlan = (t: PlanTendencies) => {
    const issues = validatePlan(t, OPTIONS);
    if (issues.length) throw new ApiError({ status: 422, code: "GAME_PLAN_INVALID", message: "", details: { errors: issues }, correlationId: null, locale: null, params: { 0: issues[0].field } });
  };
  const checkBook = (w: PlaybookWeights) => {
    const known = new Set(PLAYBOOK_SITUATIONS as string[]);
    const issues = [] as ReturnType<typeof validatePlaybook>;
    for (const x of w.situations ?? []) if (!known.has(x.situation)) issues.push({ code: "UNKNOWN_SITUATION", field: "situations", value: x.situation });
    issues.push(...validatePlaybook(playbookDraftFrom({ situations: (w.situations ?? []).filter((x) => known.has(x.situation)), coverages: w.coverages ?? [] }), META));
    if (issues.length) {
      const code = playbookErrorCode(issues);
      throw new ApiError({ status: 422, code, message: "", details: { errors: issues }, correlationId: null, locale: null, params: { 0: issues[0].field } });
    }
  };

  /** Próximo jogo do usuário a partir da semana atual (o primeiro sem placar). */
  const nextFixture = (c: StoredCareer) => {
    const club = acc.club(c);
    const week = c.summary.currentWeek;
    if (week > REGULAR_WEEKS) throw ApiError.conflict("NO_NEXT_GAME");
    return acc.fixtures(c).find((f) => f.week >= week && (f.homeClubId === club.id || f.awayClubId === club.id) && f.homeScore == null) ?? null;
  };

  const sessionsOf = (c: StoredCareer) => (extOf(c).sessions ??= {});
  const findSession = (matchId: string): { c: StoredCareer; s: Session } => {
    for (const c of acc.all()) for (const s of Object.values(sessionsOf(c))) if (s.id === matchId || s.fixtureId === matchId) return { c, s };
    throw ApiError.notFound("match");
  };

  const sideInfo = (c: StoredCareer, club: ClubRef & { prestige: number }, user: boolean): SideInfo => {
    const players = user ? acc.players(c) : opponentRoster(c.summary.id, club.id, club.prestige);
    const lineup = lineupFrom(players, user ? c.chart : null);
    const strength = avg([lineup.qb, lineup.rb, ...lineup.wrs, lineup.te, ...lineup.dl, ...lineup.lb, ...lineup.db].map((p) => p.rating));
    const opp = COACH_PLANS[["OFFENSIVE", "DEFENSIVE", "DEVELOPER", "MOTIVATOR"][hashString(club.id) % 4]];
    return { clubId: club.id, name: club.name, shortName: club.shortName, strength, lineup, score: 0, quarters: [0, 0, 0, 0], timeouts: 3, plan: user ? { ...planOf(c).tendencies } : { ...opp }, book: user ? structuredClone(bookOf(c).weights) : neutralBook() };
  };

  const weatherFor = (fixtureId: string) => ["CLEAR", "CLEAR", "CLEAR", "RAIN", "WIND", "SNOW", "INDOOR"][hashString(`weather:${fixtureId}`) % 7];

  const createSession = (c: StoredCareer, fixtureId: string, week: number, homeId: string, awayId: string): Session => {
    const me = acc.club(c);
    const userSide: SideKey = homeId === me.id ? "H" : "A";
    const home = acc.clubById(homeId)!;
    const away = acc.clubById(awayId)!;
    const seed = hashString(`${c.summary.seed}:${fixtureId}`);
    return {
      id: uuidFrom("match", `${c.summary.id}:${fixtureId}`),
      fixtureId,
      week,
      seed,
      rng: seed || 1,
      userSide,
      level: "KEY_MOMENTS",
      status: "NOT_STARTED",
      version: 0,
      weather: weatherFor(fixtureId),
      quarter: 1,
      clock: QUARTER_SECONDS,
      possession: null,
      down: 1,
      distance: 10,
      ball: userSide === "H" ? 25 : 75,
      kickoffBy: null,
      patBy: null,
      secondHalfKicker: "H",
      sides: { H: sideInfo(c, home, userSide === "H"), A: sideInfo(c, away, userSide === "A") },
      events: [],
      drives: [],
      drive: null,
      driveCount: 0,
      decisions: [],
      pending: null,
      deadlineAt: null,
      team: { H: emptyTeam(homeId), A: emptyTeam(awayId) },
      players: {},
    };
  };

  const boardOf = (c: StoredCareer, week: number): WeekBoard => {
    const me = acc.club(c);
    const fx = acc.fixtures(c).filter((f) => f.week === week);
    const playing = new Set(fx.flatMap((f) => [f.homeClubId, f.awayClubId]));
    const sessions = sessionsOf(c);
    const userFx = fx.find((f) => f.homeClubId === me.id || f.awayClubId === me.id) ?? null;
    const userSession = userFx ? sessions[userFx.id] : undefined;
    const allClubs = acc.fixtures(c).filter((f) => f.week === 1).flatMap((f) => [f.homeClubId, f.awayClubId]);
    return {
      seasonId: acc.seasonId(c),
      week,
      regularSeasonWeeks: REGULAR_WEEKS,
      currentWeek: c.summary.currentWeek,
      kind: "REGULAR",
      status: week < c.summary.currentWeek ? "COMPLETED" : week === c.summary.currentWeek ? "CURRENT" : "UPCOMING",
      startsOn: fx[0]?.date ?? null,
      completed: fx.length > 0 && fx.every((f) => f.homeScore != null),
      userBye: !userFx,
      userFixtureId: userFx?.id ?? null,
      matchId: userSession?.id ?? null,
      byeClubIds: [...new Set(allClubs)].filter((id) => !playing.has(id)),
      games: fx.map((f) => {
        const s = sessions[f.id];
        return {
          fixture: f,
          home: plain(acc.clubById(f.homeClubId)!),
          away: plain(acc.clubById(f.awayClubId)!),
          live: s ? { matchId: s.id, status: s.status, quarter: s.quarter, clockSeconds: s.clock, possessionClubId: s.possession ? s.sides[s.possession].clubId : null, down: s.down, distance: s.distance, ballOnAbsYd: s.ball, homeScore: s.sides.H.score, awayScore: s.sides.A.score } : null,
        };
      }),
    };
  };

  const finishIfOver = (c: StoredCareer, s: Session) => {
    if (s.status !== "FINISHED") return;
    finishLines(s);
    if (!c.results?.[s.fixtureId]) acc.closeWeek(c, s.week, { [s.fixtureId]: [s.sides.H.score, s.sides.A.score] }, rngFrom(`week:${c.summary.id}:${s.week}`));
  };

  const checkVersion = (s: Session, expected: number | null | undefined, required: boolean) => {
    if (expected == null && !required) return;
    if (expected !== s.version) throw conflict("MATCH_VERSION_CONFLICT", s.version, expected);
  };

  const commit = () => acc.commit();
  const plain = (x: ClubRef): ClubRef => ({ id: x.id, name: x.name, shortName: x.shortName, city: x.city, primaryColor: x.primaryColor, secondaryColor: x.secondaryColor, conference: x.conference, division: x.division });

  const ref = (p: MPlayer): PlayerRef => ({ id: p.id, name: p.name, position: p.position, jerseyNumber: p.jerseyNumber, overall: p.rating, tier: cardTier(p.rating) });
  const matchup = (key: string, side: string, ours: MPlayer[], theirs: MPlayer[]): Matchup => {
    const a = avg(ours.map((p) => p.rating)), b = avg(theirs.map((p) => p.rating));
    return { key, side, ours: a, theirs: b, edge: a - b, advantage: a - b >= 3 ? "OURS" : b - a >= 3 ? "THEIRS" : "EVEN", ourPlayers: ours.map(ref), theirPlayers: theirs.map(ref) };
  };

  return {
    gamePlan(careerId) {
      const c = acc.find(careerId);
      const dto = planDto(c);
      acc.commit();
      return dto;
    },
    saveGamePlan(careerId, req) {
      const c = acc.writable(careerId);
      const p = planOf(c);
      if (req.version == null || req.version !== p.version) throw conflict("GAME_PLAN_VERSION_CONFLICT", p.version, req.version);
      const t: PlanTendencies = { passRatio: req.passRatio, fourthDownAggression: req.fourthDownAggression, tempo: req.tempo, defensiveFocus: req.defensiveFocus, blitzFrequency: req.blitzFrequency, baseDefense: req.baseDefense };
      checkPlan(t);
      c.ext!.plan = { tendencies: t, version: p.version + 1, saved: true, updatedAt: new Date().toISOString() };
      commit();
      return planDto(c);
    },
    playbook(careerId) {
      const c = acc.find(careerId);
      const dto = bookDto(c);
      acc.commit();
      return dto;
    },
    savePlaybook(careerId, req) {
      const c = acc.writable(careerId);
      const b = bookOf(c);
      if (req.version == null || req.version !== b.version) throw conflict("PLAYBOOK_VERSION_CONFLICT", b.version, req.version);
      checkBook(req);
      c.ext!.book = { weights: playbookWeights(playbookDraftFrom(req), META), version: b.version + 1, updatedAt: new Date().toISOString() };
      commit();
      return bookDto(c);
    },
    preparation(careerId) {
      const c = acc.find(careerId);
      const f = nextFixture(c);
      if (!f) throw ApiError.conflict("NO_NEXT_GAME");
      const me = acc.club(c);
      const home = f.homeClubId === me.id;
      const opp = acc.clubById(home ? f.awayClubId : f.homeClubId)!;
      const ours = sideInfo(c, me, true).lineup;
      const theirs = sideInfo(c, opp, false).lineup;
      const v = acc.validation(c);
      const offense = (l: Lineup) => [l.qb, l.rb, ...l.wrs, l.te];
      const defenseOf = (l: Lineup) => [...l.dl, ...l.lb, ...l.db];
      const st = (l: Lineup) => [l.k, l.p, l.kr];
      const unit = (u: string, a: MPlayer[], b: MPlayer[]) => ({ unit: u, ours: avg(a.map((p) => p.rating)), theirs: avg(b.map((p) => p.rating)), edge: avg(a.map((p) => p.rating)) - avg(b.map((p) => p.rating)) });
      const oline = (players: MockPlayer[]) => (["LT", "LG", "C", "RG", "RT"] as Position[]).map((pos) => bestBy(players, pos, 1)[0]).filter(Boolean);
      const oppPlayers = opponentRoster(c.summary.id, opp.id, opp.prestige);
      const myOl = oline(acc.players(c));
      const theirOl = oline(oppPlayers);
      const rush = (l: Lineup) => [...l.dl.filter((p) => p.position === "DE"), l.lb[0]].filter(Boolean);
      const key = (l: Lineup) => [...offense(l), ...defenseOf(l)].sort((a, b) => b.rating - a.rating).slice(0, 3).map(ref);
      const s = sessionsOf(c)[f.id];
      acc.commit();
      return {
        fixture: f,
        home,
        club: plain(me),
        opponent: plain(opp),
        record: acc.record(c, me.id),
        opponentRecord: acc.record(c, opp.id),
        weather: { condition: weatherFor(f.id), stadium: `Arena ${home ? me.city : opp.city}`, roof: "OPEN", surface: "GRASS" },
        units: [unit("OFFENSE", offense(ours), offense(theirs)), unit("DEFENSE", defenseOf(ours), defenseOf(theirs)), unit("SPECIAL_TEAMS", st(ours), st(theirs))],
        keyMatchups: [
          matchup("WR_VS_CB", "OUR_OFFENSE", ours.wrs, theirs.db.filter((p) => p.position === "CB")),
          matchup("OL_VS_DL", "OUR_OFFENSE", myOl, theirs.dl),
          matchup("QB_VS_PASS_RUSH", "OUR_OFFENSE", [ours.qb], rush(theirs)),
          matchup("WR_VS_CB", "THEIR_OFFENSE", ours.db.filter((p) => p.position === "CB"), theirs.wrs),
          matchup("OL_VS_DL", "THEIR_OFFENSE", ours.dl, theirOl),
          matchup("QB_VS_PASS_RUSH", "THEIR_OFFENSE", rush(ours), [theirs.qb]),
        ],
        keyPlayers: key(ours),
        opponentKeyPlayers: key(theirs),
        readiness: { ready: v.errors.length === 0, depthChartVersion: c.chartVersion, errors: v.errors as DepthIssue[], warnings: v.warnings as DepthIssue[] },
        gamePlan: planDto(c),
        playbookCustomWeights: customWeights(playbookDraftFrom(bookOf(c).weights), META),
        playCallingLevel: "KEY_MOMENTS",
        matchId: s?.id ?? null,
      };
    },
    playCurrent(careerId) {
      const c = acc.writable(careerId);
      const week = c.summary.currentWeek;
      if (week > REGULAR_WEEKS) throw ApiError.conflict("NO_NEXT_GAME");
      const me = acc.club(c);
      const f = acc.fixtures(c).find((x) => x.week === week && (x.homeClubId === me.id || x.awayClubId === me.id));
      if (!f) {
        // semana de folga: fecha na hora (D-63)
        acc.closeWeek(c, week, {}, rngFrom(`week:${c.summary.id}:${week}`));
        return boardOf(c, week);
      }
      const sessions = sessionsOf(c);
      if (!sessions[f.id]) {
        const v = acc.validation(c);
        if (v.errors.length) throw new ApiError({ status: 422, code: "DEPTH_CHART_INVALID", message: "", details: { errors: v.errors, warnings: v.warnings }, correlationId: null, locale: null, params: { 0: v.errors[0].code } });
        sessions[f.id] = createSession(c, f.id, week, f.homeClubId, f.awayClubId);
        c.liveFixtureId = f.id;
      }
      commit();
      return boardOf(c, week);
    },
    week(careerId, week) {
      const c = acc.find(careerId);
      if (!Number.isInteger(week) || week < 1 || week > REGULAR_WEEKS) throw ApiError.badRequest("INVALID_WEEK", { 0: week, 1: REGULAR_WEEKS });
      return boardOf(c, week);
    },
    get(matchId) {
      const { c, s } = findSession(matchId);
      return stateOf(s, c.summary.id);
    },
    advance(matchId, req) {
      const { c, s } = findSession(matchId);
      if (s.status === "FINISHED") throw ApiError.conflict("MATCH_FINISHED");
      checkVersion(s, req?.version, false);
      const mode = req?.mode ?? "PLAY";
      if (!ADVANCE_MODES.includes(mode)) throw ApiError.badRequest("INVALID_ADVANCE_MODE", { 0: String(mode) });
      if (s.pending) {
        if (!expired(s)) throw new ApiError({ status: 409, code: "DECISION_PENDING", message: "", details: { decisionId: s.pending.id, deadlineAt: s.deadlineAt }, correlationId: null, locale: null, params: { 0: s.pending.id } });
        resolvePending(s, s.pending.autoOptionCode, "AUTO");
      } else {
        if (s.status === "NOT_STARTED") {
          const recv: SideKey = chance(s, 0.5) ? "H" : "A";
          s.secondHalfKicker = recv;
          s.kickoffBy = other(recv);
          push(s, { type: "COIN_TOSS", result: recv === "H" ? "HOME_RECEIVES" : "AWAY_RECEIVES", textKey: "match:event.coinToss", params: { receiving: s.sides[recv].name }, clockStopped: true, callSource: null });
        }
        s.status = "LIVE";
        step(s, null);
      }
      const startQuarter = s.quarter;
      const startPossession = s.possession;
      // `step` muda a situação: lida por função para o TypeScript não "fixar" o valor do começo
      const now = (): MatchSessionStatus => s.status;
      for (let i = 0; i < 600 && mode !== "PLAY"; i++) {
        const st = now();
        if (st === "FINISHED" || st === "AWAITING_DECISION") break;
        const pause = st === "BREAK" || st === "HALFTIME";
        if (mode === "QUARTER" && (s.quarter !== startQuarter || pause)) break;
        if (mode === "TO_DECISION" && pause) break;
        if (mode === "DRIVE" && (s.possession !== startPossession || pause)) break;
        step(s, null);
      }
      s.version += 1;
      finishIfOver(c, s);
      commit();
      return stateOf(s, c.summary.id);
    },
    decisions(matchId, req) {
      const { c, s } = findSession(matchId);
      if (s.status === "FINISHED") throw ApiError.conflict("MATCH_FINISHED");
      checkVersion(s, req.version, true);
      if (!req.adjustments?.length) throw ApiError.badRequest("INVALID_ADJUSTMENT", { 0: "" });
      const user = s.sides[s.userSide];
      const adjustable = s.status === "NOT_STARTED" || s.status === "BREAK" || s.status === "HALFTIME";
      for (const a of req.adjustments) {
        if (a.type === "GAME_PLAN" || a.type === "PLAYBOOK") {
          if (!adjustable) throw ApiError.of(422, "ADJUSTMENT_NOT_ALLOWED", { 0: s.status });
          if (a.type === "GAME_PLAN") {
            checkPlan(a.gamePlan);
            user.plan = { ...a.gamePlan };
          } else {
            checkBook(a.playbook);
            user.book = playbookWeights(playbookDraftFrom(a.playbook), META);
          }
          s.decisions.push({ decisionId: null, situation: null, clubId: user.clubId, optionCode: "PLAN_CHANGE", auto: false, atSeq: s.events.length });
        } else if (a.type === "TIMEOUT") {
          if (s.status !== "LIVE" || user.timeouts <= 0 || !!s.kickoffBy || !!s.patBy) throw ApiError.of(422, "TIMEOUT_NOT_ALLOWED");
          user.timeouts -= 1;
          push(s, { type: "TIMEOUT", result: "TIMEOUT", textKey: "match:event.timeout", params: { team: user.name, remaining: String(user.timeouts) }, clockStopped: true, callSource: "USER" });
          s.decisions.push({ decisionId: null, situation: null, clubId: user.clubId, optionCode: "TIMEOUT", auto: false, atSeq: s.events.length });
        } else {
          throw ApiError.badRequest("INVALID_ADJUSTMENT", { 0: String((a as { type?: string }).type) });
        }
      }
      s.version += 1;
      commit();
      return stateOf(s, c.summary.id);
    },
    playCall(matchId, decisionId, req) {
      const { c, s } = findSession(matchId);
      checkVersion(s, req.version, true);
      const past = s.decisions.find((d) => d.decisionId === decisionId);
      if (!s.pending || s.pending.id !== decisionId) {
        if (!past) throw new ApiError({ status: 404, code: "DECISION_NOT_FOUND", message: "", details: { decisionId }, correlationId: null, locale: null, params: { 0: decisionId } });
        throw new ApiError({ status: 409, code: "DECISION_CLOSED", message: "", details: { decisionId, resolvedOption: past.optionCode, auto: past.auto }, correlationId: null, locale: null, params: { 0: decisionId } });
      }
      if (expired(s)) {
        // chamada atrasada: a IA decide e a decisão automática fica gravada (D-61)
        const auto = s.pending.autoOptionCode;
        resolvePending(s, auto, "AUTO");
        s.version += 1;
        finishIfOver(c, s);
        commit();
        throw new ApiError({ status: 409, code: "DECISION_CLOSED", message: "", details: { decisionId, resolvedOption: auto, auto: true }, correlationId: null, locale: null, params: { 0: decisionId } });
      }
      if (!s.pending.options.some((o) => o.code === req.optionCode)) {
        throw new ApiError({ status: 422, code: "PLAY_NOT_OFFERED", message: "", details: { options: s.pending.options.map((o) => o.code) }, correlationId: null, locale: null, params: { 0: req.optionCode } });
      }
      resolvePending(s, req.optionCode, "USER");
      s.version += 1;
      finishIfOver(c, s);
      commit();
      return stateOf(s, c.summary.id);
    },
    events(matchId, after = 0, limit = 200) {
      const { s } = findSession(matchId);
      const lim = Math.max(1, Math.min(1000, limit));
      return { matchId: s.id, after, lastSeq: s.events.length, finished: s.status === "FINISHED", events: s.events.filter((e) => e.seq > after).slice(0, lim) };
    },
    report(matchId) {
      const { c, s } = findSession(matchId);
      if (s.status !== "FINISHED") throw ApiError.of(422, "MATCH_NOT_FINISHED");
      finishLines(s);
      const f = acc.fixtures(c).find((x) => x.id === s.fixtureId)!;
      const side = (k: SideKey) => {
        const x = s.sides[k];
        return { club: plain(acc.clubById(x.clubId)!), score: x.score, quarters: [...x.quarters], stats: { ...s.team[k] }, players: structuredClone(Object.values(s.players).filter((p) => p.clubId === x.clubId)) };
      };
      const h = s.sides.H.score, a = s.sides.A.score;
      return {
        fixtureId: s.fixtureId,
        matchId: s.id,
        week: s.week,
        date: f?.date ?? null,
        kickoffSlot: f ? String(f.kickoffSlot) : null,
        userGame: true,
        seed: s.seed,
        engineVersion: ENGINE_VERSION,
        rulesVersion: RULES_VERSION,
        weather: s.weather,
        home: side("H"),
        away: side("A"),
        overtime: false,
        winnerClubId: h === a ? null : h > a ? s.sides.H.clubId : s.sides.A.clubId,
        playCount: s.events.filter((e) => e.type === "RUN" || e.type === "PASS").length,
        leaders: leaders(s),
        drives: structuredClone(s.drives),
        scoringPlays: s.events.filter((e) => e.points > 0),
        decisions: structuredClone(s.decisions),
      };
    },
    liveRef(careerId) {
      const c = acc.find(careerId);
      const s = Object.values(sessionsOf(c)).find((x) => x.status !== "FINISHED");
      return s ? { matchId: s.id, fixtureId: s.fixtureId, week: s.week, status: s.status, homeScore: s.sides.H.score, awayScore: s.sides.A.score } : null;
    },
  };
}
