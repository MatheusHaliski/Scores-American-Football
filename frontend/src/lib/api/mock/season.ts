/**
 * Temporada no modo mock (bloco 6c), com as regras e os códigos de erro do backend: classificação com sementes, garantias e eliminação
 * (projeção até a última semana regular), playoffs e chave (re-seed, folga da semente 1, final neutra), notícias da semana por regras
 * nos textos do catálogo `season:news.*`, resumo com prêmios provisórios até a final, avanço só depois da final (422
 * SEASON_NOT_FINISHED; 409 SEASON_ALREADY_ADVANCED com a temporada já fechada), sala de troféus, moral do elenco e analista por regras
 * com limite de 6 pedidos por minuto (429 RATE_LIMITED) e 404 ANALYSIS_NOT_FOUND antes do primeiro relatório.
 *
 * As estatísticas da temporada (prêmios e líderes) e a moral são de demonstração (D-110): saem do overall, do papel no depth chart e da
 * campanha, determinísticas pela carreira; o mock não guarda box score dos jogos da IA. O formato de tudo é o dos goldens.
 */
import { overall, type Position } from "@/lib/ratings/overall";
import { translate } from "@/i18n/runtime";
import { ApiError } from "../problem";
import type { CareerDashboard, Fixture, TeamRecord } from "../career-types";
import type { PlanTendencies } from "../match-types";
import type {
  Analysis,
  AnalystMatchup,
  Award,
  Bracket,
  BracketGame,
  BracketRound,
  BracketTeam,
  DepthChange,
  ExpiringContract,
  SeasonLeader,
  NewsItem,
  Outlook,
  ParamValue,
  PlanAdvice,
  PlayerMorale,
  PlayoffSituation,
  SeasonAdvanceRequest,
  SeasonAdvanceResult,
  SeasonSummary,
  SquadMorale,
  Standings,
  StandingsRow,
  Trophies,
  Trophy,
  UnitRating,
  UserPlayoff,
  WeekNews,
} from "../season-types";
import { LEADER_CATEGORIES } from "../season-types";
import type { UserAccount } from "../types";
import {
  careerFixtures,
  clearWaivers,
  deadMoneyNextSeason,
  mockCareerAccess,
  playedGames,
  regularSchedule,
  seasonNumberOf,
  seasonState,
  summaryOf,
  tiebreakSalt,
  yearOf,
  type MockSeasonRecord,
  type StoredCareer,
} from "./career";
import { available, chartLabels, removeFromChart, SLOTS, overallAt } from "./depth";
import {
  BYE_SEEDS,
  CLUB_BY_ID,
  DIVISION_CHAIN,
  PLAYOFF_KINDS,
  SEEDS_PER_CONFERENCE,
  WILD_CARD_CHAIN,
  WORLD_CLUBS,
  clinchOf,
  clubRef,
  conferenceOrders,
  games as gamesOf,
  pct,
  recordOf,
  seedOfIn,
  streakOf,
  tallies,
  winnerOf,
  type ConferenceOrder,
  type Tally,
  type WorldClub,
} from "./league";
import { COACH_PLANS, opponentRoster } from "./match";
import type { MockPlayer } from "./players";
import { hashString, uuidFrom } from "./rng";
import { CONFERENCES, REGULAR_WEEKS } from "./world-data";

const DIVISIONS_PER_CONFERENCE = 4;
/** Limite de pedidos do analista (a regra do `AuthRateLimitFilter`: 6 por minuto). */
const ANALYSIS_LIMIT = 6;
const ANALYSIS_WINDOW_MS = 60_000;

/* ───────────────────────── contexto da temporada ───────────────────────── */

interface Ctx {
  c: StoredCareer;
  wc: WorldClub;
  table: Map<string, Tally>;
  orders: ConferenceOrder[];
  /** sementes por clube: as congeladas ou a projeção */
  seedOf: Map<string, number>;
  projection: boolean;
  results: Record<string, [number, number]>;
  eliminated: Set<string>;
}

function ctxOf(c: StoredCareer): Ctx {
  const ss = seasonState(c);
  const table = tallies(playedGames(c), regularSchedule());
  const orders = conferenceOrders(table, tiebreakSalt(c));
  const projection = !ss.playoffs;
  const seedOf = new Map<string, number>();
  const seeds = ss.playoffs?.seeds ?? Object.fromEntries(orders.map((o) => [o.shortName, o.seeds]));
  for (const ids of Object.values(seeds)) ids.forEach((id, i) => seedOf.set(id, i + 1));
  const results = c.results ?? {};
  const eliminated = new Set<string>();
  for (const r of ss.playoffs?.rounds ?? []) for (const g of r.games) {
    const w = winnerOf(g, results[g.id]);
    if (w) eliminated.add(w === g.home ? g.away : g.home);
  }
  return { c, wc: WORLD_CLUBS[c.clubIndex], table, orders, seedOf, projection, results, eliminated };
}

const plain = (r: TeamRecord): TeamRecord => ({ wins: r.wins, losses: r.losses, ties: r.ties, summary: r.summary });
const round3 = (v: number) => Math.round(v * 1000) / 1000;
const clamp = (v: number, min: number, max: number) => Math.max(min, Math.min(max, v));
/** Ruído determinístico em [0, 1). */
const noise = (key: string) => (hashString(key) % 10_000) / 10_000;
const ALL_FIXTURES = (x: Ctx) => careerFixtures(x.c);

/* ───────────────────────── RF16 — classificação ───────────────────────── */

function row(x: Ctx, t: Tally, rankN: number, by: string | null): StandingsRow {
  const club = CLUB_BY_ID.get(t.id)!;
  const seed = x.seedOf.get(t.id) ?? null;
  const ss = x.c.season;
  let clinch: string | null = null;
  let eliminated = false;
  if (ss?.playoffs) {
    // temporada regular encerrada: as garantias são as sementes oficiais
    clinch = seed == null ? null : seed <= BYE_SEEDS ? "BYE" : seed <= DIVISIONS_PER_CONFERENCE ? "DIVISION" : "PLAYOFFS";
    eliminated = seed == null;
  } else {
    const m = clinchOf(t.id, x.table);
    if (m === "ELIMINATED") eliminated = true;
    else clinch = m;
  }
  return {
    rank: rankN,
    club: clubRef(club),
    record: recordOf(t),
    winPct: round3(pct(t)),
    pointsFor: t.pointsFor,
    pointsAgainst: t.pointsAgainst,
    pointDiff: t.pointsFor - t.pointsAgainst,
    streak: streakOf(t),
    lastFive: t.results.slice(-5),
    home: recordOf(t.home),
    away: recordOf(t.away),
    division: recordOf(t.division),
    conference: recordOf(t.conference),
    seed,
    clinch,
    eliminated,
    tiebreaker: by,
    userClub: t.id === x.wc.id,
  };
}

function standingsOf(x: Ctx): Standings {
  const c = x.c;
  const throughWeek = Math.min(REGULAR_WEEKS, Math.max(0, c.summary.currentWeek - 1));
  return {
    seasonId: c.summary.seasonId ?? uuidFrom("season", c.summary.id),
    seasonYear: yearOf(c),
    phase: c.summary.seasonPhase ?? "REGULAR",
    throughWeek,
    regularSeasonWeeks: REGULAR_WEEKS,
    official: !x.projection,
    seedsPerConference: SEEDS_PER_CONFERENCE,
    byeSeeds: BYE_SEEDS,
    userClubId: x.wc.id,
    divisionTiebreakers: [...DIVISION_CHAIN],
    wildCardTiebreakers: [...WILD_CARD_CHAIN],
    conferences: x.orders.map((o) => {
      const conf = CONFERENCES.find((k) => k.shortName === o.shortName)!;
      return {
        id: uuidFrom("conference", o.shortName),
        name: conf.name,
        shortName: o.shortName,
        divisions: o.divisions.map((d) => ({ id: uuidFrom("division", d.name), name: d.name, rows: d.order.map((e, i) => row(x, e.t, i + 1, e.by)) })),
        // com a temporada regular encerrada, as sementes congeladas abrem a tabela da conferência
        rows: (x.projection ? o.order : [...o.order].sort((a, b) => (x.seedOf.get(a.t.id) ?? 99) - (x.seedOf.get(b.t.id) ?? 99))).map((e, i) => row(x, e.t, i + 1, e.by)),
      };
    }),
  };
}

/* ───────────────────────── RF66 — playoffs e chave ───────────────────────── */

function team(x: Ctx, id: string): BracketTeam {
  return { club: clubRef(CLUB_BY_ID.get(id)!), seed: x.seedOf.get(id) ?? 0 };
}

function userPlayoff(x: Ctx): UserPlayoff {
  const ss = seasonState(x.c);
  const me = x.wc.id;
  const seed = x.seedOf.get(me) ?? null;
  if (x.projection) return { clubId: me, seed, status: seed == null ? "PROJECTED_OUT" : "PROJECTED_IN", round: null, nextGame: null };
  if (ss.champion) return { clubId: me, seed, status: seed == null ? "MISSED" : me === ss.champion ? "CHAMPION" : me === ss.runnerUp ? "RUNNER_UP" : "ELIMINATED", round: null, nextGame: null };
  if (seed == null) return { clubId: me, seed, status: "MISSED", round: null, nextGame: null };
  if (x.eliminated.has(me)) return { clubId: me, seed, status: "ELIMINATED", round: null, nextGame: null };
  const week = x.c.summary.currentWeek;
  const round = ss.playoffs?.rounds.find((r) => r.week === week)?.kind ?? null;
  const next = ALL_FIXTURES(x).find((f) => f.week > REGULAR_WEEKS && f.userGame && f.homeScore == null) ?? null;
  return { clubId: me, seed, status: next ? "ALIVE" : "BYE", round, nextGame: next };
}

function playoffsOf(x: Ctx): PlayoffSituation {
  const ss = seasonState(x.c);
  const status = ss.champion ? "FINISHED" : ss.playoffs ? "IN_PROGRESS" : "NOT_STARTED";
  const tiebreakOf = new Map<string, string | null>();
  for (const o of x.orders) for (const e of o.order) tiebreakOf.set(e.t.id, e.by);
  return {
    seasonId: x.c.summary.seasonId ?? "",
    status,
    projection: x.projection,
    currentRound: status === "IN_PROGRESS" ? (ss.playoffs?.rounds.find((r) => r.week === x.c.summary.currentWeek)?.kind ?? null) : null,
    seedsPerConference: SEEDS_PER_CONFERENCE,
    byeSeeds: BYE_SEEDS,
    neutralSiteFinal: true,
    rounds: [...PLAYOFF_KINDS],
    conferences: CONFERENCES.map((conf) => {
      const ids = ss.playoffs?.seeds[conf.shortName] ?? x.orders.find((o) => o.shortName === conf.shortName)!.seeds;
      return {
        id: uuidFrom("conference", conf.shortName),
        name: conf.name,
        shortName: conf.shortName,
        seeds: ids.map((id, i) => ({
          seed: i + 1,
          club: clubRef(CLUB_BY_ID.get(id)!),
          record: recordOf(x.table.get(id)!),
          divisionChampion: i < DIVISIONS_PER_CONFERENCE,
          bye: i < BYE_SEEDS,
          status: x.projection ? "PROJECTED" : id === ss.champion ? "CHAMPION" : x.eliminated.has(id) ? "ELIMINATED" : "ALIVE",
          tiebreaker: tiebreakOf.get(id) ?? null,
        })),
      };
    }),
    user: userPlayoff(x),
    champion: ss.champion ? clubRef(CLUB_BY_ID.get(ss.champion)!) : null,
    runnerUp: ss.runnerUp ? clubRef(CLUB_BY_ID.get(ss.runnerUp)!) : null,
  };
}

function bracketOf(x: Ctx): Bracket {
  const ss = seasonState(x.c);
  const fixtures = new Map(ALL_FIXTURES(x).map((f) => [f.id, f]));
  const rounds: BracketRound[] = PLAYOFF_KINDS.map((kind, index) => {
    const week = REGULAR_WEEKS + index + 1;
    const r = ss.playoffs?.rounds[index];
    if (r) {
      const games: BracketGame[] = r.games.map((g) => {
        const f = fixtures.get(g.id);
        const score = x.results[g.id];
        return {
          fixtureId: g.id,
          conference: g.conference,
          home: team(x, g.home),
          away: team(x, g.away),
          homeScore: score ? score[0] : null,
          awayScore: score ? score[1] : null,
          overtime: !!ss.overtime[g.id],
          winnerClubId: winnerOf(g, score),
          neutralSite: g.neutral,
          status: f?.status ?? "SCHEDULED",
          date: f?.date ?? null,
          kickoffSlot: g.slot,
          userGame: g.home === x.wc.id || g.away === x.wc.id,
        };
      });
      const status = week < x.c.summary.currentWeek ? "COMPLETED" : week === x.c.summary.currentWeek ? "CURRENT" : "UPCOMING";
      return { index, kind, week, status, games, byes: r.byes.map((id) => team(x, id)) };
    }
    if (x.projection && index === 0) {
      // antes do fim da temporada regular: a primeira rodada projetada pelas sementes de hoje
      const games: BracketGame[] = [];
      const byes: BracketTeam[] = [];
      for (const o of x.orders) {
        byes.push(...o.seeds.slice(0, BYE_SEEDS).map((id) => team(x, id)));
        const playing = o.seeds.slice(BYE_SEEDS);
        for (let i = 0; i < Math.floor(playing.length / 2); i++) {
          const home = playing[i], away = playing[playing.length - 1 - i];
          games.push({ fixtureId: null, conference: o.shortName, home: team(x, home), away: team(x, away), homeScore: null, awayScore: null, overtime: false, winnerClubId: null, neutralSite: false, status: "PROJECTED", date: null, kickoffSlot: null, userGame: home === x.wc.id || away === x.wc.id });
        }
      }
      return { index, kind, week: null, status: "PROJECTED", games, byes };
    }
    return { index, kind, week: null, status: "PENDING", games: [], byes: [] };
  });
  return { seasonId: x.c.summary.seasonId ?? "", status: ss.champion ? "FINISHED" : ss.playoffs ? "IN_PROGRESS" : "NOT_STARTED", userClubId: x.wc.id, rounds, champion: ss.champion ? clubRef(CLUB_BY_ID.get(ss.champion)!) : null };
}

/* ───────────────────────── RF21 — notícias ───────────────────────── */

function newsItem(x: Ctx, week: number, kind: string, tone: string, params: Record<string, ParamValue>, ref: { clubId?: string | null; fixtureId?: string | null }): NewsItem {
  const p = { ...params };
  return {
    id: uuidFrom("news", `${x.c.summary.seasonId}:${week}:${kind}:${ref.fixtureId ?? ref.clubId ?? ""}`),
    week,
    kind,
    tone,
    headline: translate("season", `news.${kind}.headline`, p),
    body: translate("season", `news.${kind}.body`, p),
    textKey: `news.${kind}`,
    params,
    clubId: ref.clubId ?? null,
    playerId: null,
    fixtureId: ref.fixtureId ?? null,
  };
}

/** Notícias da semana por regras (D-103), no idioma do runtime. Sem box score dos jogos da IA, ficam de fora os destaques individuais. */
function newsOf(x: Ctx, week: number): NewsItem[] {
  const c = x.c;
  if (week >= c.summary.currentWeek) return [];
  const ss = seasonState(c);
  const name = (id: string) => CLUB_BY_ID.get(id)!.name;
  const weekGames = ALL_FIXTURES(x).filter((f) => f.week === week && f.homeScore != null && f.awayScore != null);
  const out: NewsItem[] = [];
  const me = x.wc.id;
  const margin = (f: Fixture) => Math.abs(f.homeScore! - f.awayScore!);
  const winLose = (f: Fixture) => {
    const homeWon = f.homeScore! >= f.awayScore!;
    return { winner: homeWon ? f.homeClubId : f.awayClubId, loser: homeWon ? f.awayClubId : f.homeClubId, ws: Math.max(f.homeScore!, f.awayScore!), ls: Math.min(f.homeScore!, f.awayScore!) };
  };
  if (week > REGULAR_WEEKS) {
    const round = ss.playoffs?.rounds.find((r) => r.week === week);
    for (const f of weekGames) {
      const w = winLose(f);
      const user = f.userGame;
      const tone = user ? (w.winner === me ? "POSITIVE" : "NEGATIVE") : round?.kind === "FINAL" ? "DRAMATIC" : "NEUTRAL";
      if (round?.kind === "FINAL") out.push(newsItem(x, week, "CHAMPION", tone, { team: name(w.winner), opponent: name(w.loser), winnerScore: w.ws, loserScore: w.ls, year: yearOf(c) }, { clubId: w.winner, fixtureId: f.id }));
      else out.push(newsItem(x, week, "PLAYOFF_RESULT", tone, { winner: name(w.winner), loser: name(w.loser), winnerScore: w.ws, loserScore: w.ls, round: translate("season", `news.round.${round?.kind ?? "WILD_CARD"}`), overtime: ss.overtime[f.id] ? 1 : 0 }, { clubId: w.winner, fixtureId: f.id }));
    }
    return out;
  }
  // a classificação até esta semana (as notícias de garantia comparam com a da semana anterior)
  const played = playedGames(c).filter((g) => g.week <= week);
  const after = tallies(played, regularSchedule());
  const before = tallies(played.filter((g) => g.week < week), regularSchedule());
  const mine = weekGames.find((f) => f.userGame);
  if (mine) {
    const home = mine.homeClubId === me;
    const pf = home ? mine.homeScore! : mine.awayScore!, pa = home ? mine.awayScore! : mine.homeScore!;
    const kind = pf > pa ? "USER_WIN" : pf < pa ? "USER_LOSS" : "USER_TIE";
    out.push(newsItem(x, week, kind, pf > pa ? "POSITIVE" : pf < pa ? "NEGATIVE" : "NEUTRAL", { team: name(me), opponent: name(home ? mine.awayClubId : mine.homeClubId), pointsFor: pf, pointsAgainst: pa, record: recordOf(after.get(me)!).summary, overtime: 0 }, { clubId: me, fixtureId: mine.id }));
  }
  const others = weekGames.filter((f) => !f.userGame);
  const blowout = [...others].sort((a, b) => margin(b) - margin(a) || name(a.homeClubId).localeCompare(name(b.homeClubId)))[0];
  if (blowout && margin(blowout) >= 21) {
    const w = winLose(blowout);
    out.push(newsItem(x, week, "BLOWOUT", "DRAMATIC", { winner: name(w.winner), loser: name(w.loser), winnerScore: w.ws, loserScore: w.ls, margin: margin(blowout) }, { clubId: w.winner, fixtureId: blowout.id }));
  }
  const thriller = others.filter((f) => f !== blowout && margin(f) <= 3 && margin(f) > 0).sort((a, b) => margin(a) - margin(b) || name(a.homeClubId).localeCompare(name(b.homeClubId)))[0];
  if (thriller) {
    const w = winLose(thriller);
    out.push(newsItem(x, week, "THRILLER", "DRAMATIC", { winner: name(w.winner), loser: name(w.loser), winnerScore: w.ws, loserScore: w.ls, overtime: 0, margin: margin(thriller) }, { clubId: w.winner, fixtureId: thriller.id }));
  }
  // sequências: a maior de 4+ da liga e a do usuário a partir de 3
  const streaks = [...after.values()].map((t) => ({ id: t.id, s: streakOf(t) })).filter((s) => s.s !== "—").map((s) => ({ id: s.id, mark: s.s[0], n: Number(s.s.slice(1)) }));
  const best = (mark: string) => streaks.filter((s) => s.mark === mark && s.n >= 4).sort((a, b) => b.n - a.n || name(a.id).localeCompare(name(b.id)))[0];
  for (const [mark, kind] of [["W", "WIN_STREAK"], ["L", "LOSS_STREAK"]] as const) {
    const s = best(mark);
    if (s) out.push(newsItem(x, week, kind, s.id === me ? (mark === "W" ? "POSITIVE" : "NEGATIVE") : "NEUTRAL", { team: name(s.id), games: s.n }, { clubId: s.id }));
  }
  const ms = streaks.find((s) => s.id === me && s.n >= 3 && s.mark !== "T");
  if (ms && !out.some((n) => n.clubId === me && (n.kind === "WIN_STREAK" || n.kind === "LOSS_STREAK"))) {
    out.push(newsItem(x, week, ms.mark === "W" ? "WIN_STREAK" : "LOSS_STREAK", ms.mark === "W" ? "POSITIVE" : "NEGATIVE", { team: name(me), games: ms.n }, { clubId: me }));
  }
  // garantias novas da semana (o usuário primeiro, depois pelo nome)
  const clubs = [...WORLD_CLUBS].sort((a, b) => (a.id === me ? 0 : 1) - (b.id === me ? 0 : 1) || a.name.localeCompare(b.name));
  for (const club of clubs) {
    const now = clinchOf(club.id, after), was = clinchOf(club.id, before);
    if (!now || now === was) continue;
    if (now === "BYE") out.push(newsItem(x, week, "CLINCH_BYE", "POSITIVE", { team: club.name }, { clubId: club.id }));
    else if (now === "DIVISION") out.push(newsItem(x, week, "CLINCH_DIVISION", "POSITIVE", { team: club.name, division: club.division }, { clubId: club.id }));
    else if (now === "PLAYOFFS") out.push(newsItem(x, week, "CLINCH_PLAYOFFS", "POSITIVE", { team: club.name }, { clubId: club.id }));
    else out.push(newsItem(x, week, "ELIMINATED", club.id === me ? "NEGATIVE" : "NEUTRAL", { team: club.name }, { clubId: club.id }));
  }
  if (week === REGULAR_WEEKS && ss.playoffs) {
    const seed = seedOfIn(ss.playoffs, me);
    if (seed == null) out.push(newsItem(x, week, "USER_MISSED", "NEGATIVE", { team: name(me) }, { clubId: me }));
    else out.push(newsItem(x, week, "USER_SEED", "POSITIVE", { team: name(me), seed }, { clubId: me }));
    for (const conf of CONFERENCES) {
      const top = ss.playoffs.seeds[conf.shortName]?.[0];
      if (top) out.push(newsItem(x, week, "TOP_SEED", top === me ? "POSITIVE" : "NEUTRAL", { team: name(top), conference: conf.name }, { clubId: top }));
    }
  }
  return out;
}

/* ───────────────────────── estatísticas de demonstração ───────────────────────── */

interface StatLine {
  player: MockPlayer;
  club: WorldClub;
  games: number;
  stats: Record<string, number>;
}

const statCache = new Map<string, StatLine[]>();

function rosterOf(x: Ctx, club: WorldClub): MockPlayer[] {
  return club.id === x.wc.id ? x.c.players.filter((p) => p.onClub && p.rosterStatus === "ACTIVE") : opponentRoster(x.c.summary.id, club.id, club.prestige);
}

const bestAt = (players: MockPlayer[], pos: Position, n: number) => players.filter((p) => p.position === pos && p.rosterStatus === "ACTIVE").sort((a, b) => overall(b.attributes, b.position) - overall(a.attributes, a.position)).slice(0, n);

/** Linhas de estatística da temporada regular para os titulares de cada franquia (determinísticas pelo overall e pelos jogos). */
function seasonStats(x: Ctx): StatLine[] {
  const key = `${x.c.summary.id}:${seasonNumberOf(x.c)}:${Math.min(REGULAR_WEEKS, x.c.summary.currentWeek)}:${x.c.players.length}`;
  const hit = statCache.get(key);
  if (hit) return hit;
  const out: StatLine[] = [];
  for (const club of WORLD_CLUBS) {
    const t = x.table.get(club.id)!;
    const g = gamesOf(t);
    if (g === 0) continue;
    const players = rosterOf(x, club);
    const n = (p: MockPlayer, k: string) => 0.85 + noise(`${p.id}:${k}:${g}`) * 0.3;
    const ov = (p: MockPlayer) => overall(p.attributes, p.position);
    const add = (p: MockPlayer, s: Record<string, number>) => out.push({ player: p, club, games: g, stats: Object.fromEntries(Object.entries(s).map(([k, v]) => [k, Math.max(0, Math.round(v))])) });
    for (const p of bestAt(players, "QB", 1)) add(p, { passYards: g * (170 + (ov(p) - 60) * 5) * n(p, "py"), passTouchdowns: g * (1 + (ov(p) - 60) / 25) * n(p, "ptd"), interceptionsThrown: g * clamp(0.9 - (ov(p) - 60) / 60, 0.2, 1.2) * n(p, "int"), rushYards: g * 12 * n(p, "ry"), rushTouchdowns: g * 0.1 * n(p, "rtd") });
    for (const p of bestAt(players, "RB", 1)) add(p, { rushYards: g * (50 + (ov(p) - 60) * 2.5) * n(p, "ry"), rushTouchdowns: g * (0.35 + (ov(p) - 60) / 60) * n(p, "rtd"), receptions: g * 2.2 * n(p, "rec"), receivingYards: g * 16 * n(p, "recy"), receivingTouchdowns: g * 0.1 * n(p, "rectd") });
    bestAt(players, "WR", 3).forEach((p, i) => {
      const f = [1, 0.7, 0.5][i];
      add(p, { receptions: g * 5 * f * n(p, "rec"), receivingYards: g * (55 + (ov(p) - 60) * 2) * f * n(p, "recy"), receivingTouchdowns: g * 0.4 * f * n(p, "rectd") });
    });
    for (const p of bestAt(players, "TE", 1)) add(p, { receptions: g * 3 * n(p, "rec"), receivingYards: g * 35 * n(p, "recy"), receivingTouchdowns: g * 0.25 * n(p, "rectd") });
    const defense: [Position, number, number, number, number][] = [["DE", 2, 3, 0.6, 0.01], ["DT", 2, 3, 0.3, 0.01], ["OLB", 2, 5, 0.4, 0.04], ["MLB", 1, 7, 0.15, 0.05], ["CB", 2, 4, 0, 0.15], ["FS", 1, 5, 0.05, 0.12], ["SS", 1, 5, 0.1, 0.1]];
    for (const [pos, k, tackles, sacks, ints] of defense)
      for (const p of bestAt(players, pos, k))
        add(p, { soloTackles: g * (tackles + (ov(p) - 60) / 10) * n(p, "tk"), sacks: g * sacks * (1 + (ov(p) - 60) / 40) * n(p, "sk"), interceptions: g * ints * n(p, "in"), forcedFumbles: g * 0.08 * n(p, "ff") });
  }
  statCache.set(key, out);
  return out;
}

const s = (l: StatLine, k: string) => l.stats[k] ?? 0;
/** Produção ofensiva e defensiva da D-98. */
const offenseProduction = (l: StatLine) => s(l, "passYards") / 25 + 4 * s(l, "passTouchdowns") - 2 * s(l, "interceptionsThrown") + s(l, "rushYards") / 10 + 6 * s(l, "rushTouchdowns") + s(l, "receivingYards") / 10 + 6 * s(l, "receivingTouchdowns");
const defenseProduction = (l: StatLine) => s(l, "soloTackles") + 4 * s(l, "sacks") + 5 * s(l, "interceptions") + 3 * s(l, "forcedFumbles");
const isOffense = (l: StatLine) => ["QB", "RB", "WR", "TE"].includes(l.player.position);

function statLineOf(l: StatLine): Record<string, number> {
  if (!isOffense(l)) return { games: l.games, soloTackles: s(l, "soloTackles"), sacks: s(l, "sacks"), interceptions: s(l, "interceptions"), forcedFumbles: s(l, "forcedFumbles") };
  if (l.player.position === "QB") return { games: l.games, passYards: s(l, "passYards"), passTouchdowns: s(l, "passTouchdowns"), interceptionsThrown: s(l, "interceptionsThrown"), rushYards: s(l, "rushYards"), rushTouchdowns: s(l, "rushTouchdowns") };
  return { games: l.games, rushYards: s(l, "rushYards"), rushTouchdowns: s(l, "rushTouchdowns"), receptions: s(l, "receptions"), receivingYards: s(l, "receivingYards"), receivingTouchdowns: s(l, "receivingTouchdowns") };
}

const COACH_FIRST = ["Gonzalo", "Marcus", "Rafael", "Tomás", "Darius", "Henrique", "Logan", "Iván"];
const COACH_LAST = ["Wesard", "Corvane", "Dalmont", "Esperley", "Falquist", "Garford", "Holberg", "Ivarore"];

function awardsOf(x: Ctx): Award[] {
  const lines = seasonStats(x);
  if (!lines.length) return [];
  const award = (type: string, l: StatLine, score: number): Award => ({ type, playerId: l.player.id, name: l.player.name, position: l.player.position, club: clubRef(l.club), score: Math.round(score * 10) / 10, statLine: statLineOf(l) });
  const top = (xs: StatLine[], f: (l: StatLine) => number) => [...xs].sort((a, b) => f(b) - f(a) || a.player.name.localeCompare(b.player.name))[0];
  const off = lines.filter(isOffense), def = lines.filter((l) => !isOffense(l));
  const out: Award[] = [];
  const mvpScore = (l: StatLine) => offenseProduction(l) * (0.6 + pct(x.table.get(l.club.id)!));
  const mvp = top(off, mvpScore);
  if (mvp) out.push(award("MVP", mvp, mvpScore(mvp)));
  const opoy = top(off, offenseProduction);
  if (opoy) out.push(award("OPOY", opoy, offenseProduction(opoy)));
  const dpoy = top(def, defenseProduction);
  if (dpoy) out.push(award("DPOY", dpoy, defenseProduction(dpoy)));
  const oroy = top(off.filter((l) => l.player.experienceYears === 0), offenseProduction);
  if (oroy) out.push(award("OROY", oroy, offenseProduction(oroy)));
  const droy = top(def.filter((l) => l.player.experienceYears === 0), defenseProduction);
  if (droy) out.push(award("DROY", droy, defenseProduction(droy)));
  const coach = WORLD_CLUBS.map((club) => ({ club, t: x.table.get(club.id)! })).filter((e) => gamesOf(e.t) > 0).sort((a, b) => b.t.wins - gamesOf(b.t) * b.club.prestige / 100 - (a.t.wins - gamesOf(a.t) * a.club.prestige / 100) || a.club.name.localeCompare(b.club.name))[0];
  if (coach) {
    const h = hashString(coach.club.id);
    const name = coach.club.id === x.wc.id ? (x.c.summary.coachName ?? "—") : `${COACH_FIRST[h % COACH_FIRST.length]} ${COACH_LAST[(h >>> 4) % COACH_LAST.length]}`;
    const g = gamesOf(coach.t);
    out.push({ type: "COACH", playerId: null, name, position: null, club: clubRef(coach.club), score: Math.round((coach.t.wins - (g * coach.club.prestige) / 100) * 10) / 10, statLine: { games: g, wins: coach.t.wins, prestige: coach.club.prestige } });
  }
  return out;
}

const LEADER_STAT: Record<(typeof LEADER_CATEGORIES)[number], string> = {
  PASS_YARDS: "passYards", PASS_TOUCHDOWNS: "passTouchdowns", RUSH_YARDS: "rushYards", RUSH_TOUCHDOWNS: "rushTouchdowns", RECEIVING_YARDS: "receivingYards",
  RECEPTIONS: "receptions", SACKS: "sacks", INTERCEPTIONS: "interceptions", TACKLES: "soloTackles",
};

function leadersOf(x: Ctx): Record<string, SeasonLeader[]> {
  const lines = seasonStats(x);
  const out: Record<string, SeasonLeader[]> = {};
  for (const cat of LEADER_CATEGORIES) {
    const k = LEADER_STAT[cat];
    out[cat] = lines
      .filter((l) => s(l, k) > 0)
      .sort((a, b) => s(b, k) - s(a, k) || a.player.name.localeCompare(b.player.name))
      .slice(0, 5)
      .map((l) => ({ playerId: l.player.id, name: l.player.name, position: l.player.position, club: clubRef(l.club), value: s(l, k), games: l.games }));
  }
  return out;
}

/* ───────────────────────── RF35 — resumo, avanço e troféus ───────────────────────── */

function expiringOf(c: StoredCareer, year: number): ExpiringContract[] {
  return c.players
    .filter((p) => p.onClub && p.contract.endSeason <= year)
    .map((p) => ({ playerId: p.id, name: p.name, position: p.position, age: p.age, overall: overall(p.attributes, p.position), endSeason: p.contract.endSeason, capHit: p.contract.capHitByYear[Math.max(0, Math.min(p.contract.years - 1, year - p.contract.startSeason))] ?? 0 }))
    .sort((a, b) => b.overall - a.overall || a.name.localeCompare(b.name));
}

function playoffResultOf(x: Ctx): string {
  const ss = seasonState(x.c);
  const me = x.wc.id;
  if (!ss.playoffs) return "IN_PROGRESS";
  if (seedOfIn(ss.playoffs, me) == null) return "MISSED";
  if (ss.champion === me) return "CHAMPION";
  if (ss.runnerUp === me) return "RUNNER_UP";
  for (const r of ss.playoffs.rounds) for (const g of r.games) {
    const w = winnerOf(g, x.results[g.id]);
    if (w && w !== me && (g.home === me || g.away === me)) return r.kind;
  }
  return "IN_PROGRESS";
}

const STEPS_PENDING = [
  { phase: "RENEWALS", status: "PENDING", handledBy: "SEASON_ADVANCE" },
  { phase: "FREE_AGENCY", status: "PENDING", handledBy: "BLOCK_7" },
  { phase: "DRAFT", status: "PENDING", handledBy: "BLOCK_7" },
  { phase: "PRESEASON", status: "PENDING", handledBy: "SEASON_ADVANCE" },
];

function summaryOfSeason(x: Ctx): SeasonSummary {
  const c = x.c;
  const ss = seasonState(c);
  const me = x.wc.id;
  const t = x.table.get(me)!;
  const divRank = x.orders.flatMap((o) => o.divisions).find((d) => d.order.some((e) => e.t.id === me))!.order.findIndex((e) => e.t.id === me) + 1;
  const confOrder = x.orders.find((o) => o.order.some((e) => e.t.id === me))!;
  return {
    seasonId: c.summary.seasonId ?? "",
    number: seasonNumberOf(c),
    year: yearOf(c),
    phase: c.summary.seasonPhase ?? "REGULAR",
    completed: !!ss.champion,
    champion: ss.champion ? clubRef(CLUB_BY_ID.get(ss.champion)!) : null,
    runnerUp: ss.runnerUp ? clubRef(CLUB_BY_ID.get(ss.runnerUp)!) : null,
    userClubId: me,
    campaign: { record: recordOf(t), pointsFor: t.pointsFor, pointsAgainst: t.pointsAgainst, divisionRank: divRank, conferenceRank: confOrder.order.findIndex((e) => e.t.id === me) + 1, seed: x.seedOf.get(me) ?? null, playoffResult: playoffResultOf(x) },
    provisionalAwards: !ss.champion,
    awards: awardsOf(x),
    leaders: leadersOf(x),
    offseason: { canAdvance: !!ss.champion, steps: STEPS_PENDING.map((st) => ({ ...st })), expiringContracts: expiringOf(c, yearOf(c)) },
  };
}

function trophiesOfSeason(x: Ctx, summary: SeasonSummary): Trophy[] {
  const ss = seasonState(x.c);
  const base = { seasonId: summary.seasonId, seasonNumber: summary.number, seasonYear: summary.year };
  const t = (kind: string, id: string): Trophy => {
    const club = CLUB_BY_ID.get(id)!;
    return { ...base, kind, club: clubRef(club), conference: club.conference, division: club.division, userClub: id === x.wc.id };
  };
  const out: Trophy[] = [];
  if (ss.champion) out.push(t("CHAMPIONSHIP", ss.champion));
  if (ss.champion && ss.runnerUp) for (const id of [ss.champion, ss.runnerUp]) out.push(t("CONFERENCE", id));
  for (const ids of Object.values(ss.playoffs?.seeds ?? {})) for (const id of ids.slice(0, DIVISIONS_PER_CONFERENCE)) out.push(t("DIVISION", id));
  return out;
}

/** Envelhecimento e progressão de demonstração pela D-101 (sem sorteio); devolve o overall antes e depois. */
function progress(p: MockPlayer): [number, number] {
  const before = overall(p.attributes, p.position);
  const age = p.age;
  const specialist = p.position === "K" || p.position === "P" || p.position === "LS";
  const a = specialist ? age - 2 : age;
  const groups = p.attributes as unknown as Record<string, Record<string, number>>;
  const bump = (group: string | null, d: number) => {
    for (const [g, attrs] of Object.entries(groups)) {
      if (group === "physical" && g !== "physical") continue;
      if (group === "rest" && g === "physical") continue;
      for (const k of Object.keys(attrs)) attrs[k] = clamp(attrs[k] + d, 25, 99);
    }
  };
  if (a <= 24) bump(null, Math.min(4, Math.max(0, Math.ceil((p.potential - before) / 3))));
  else if (a <= 28) { if (p.potential - before >= 3) bump(null, 1); }
  else if (a <= 30) bump("physical", -1);
  else if (a <= 33) { bump("physical", -2); bump("rest", -1); }
  else { bump("physical", -3); bump("rest", -2); }
  if (a >= 31) for (const k of ["awareness", "leadership"]) groups.mental[k] = clamp(groups.mental[k] + 1, 25, 99);
  p.age += 1;
  p.experienceYears += 1;
  p.injuryWeeksOut = 0;
  p.suspensionWeeks = 0;
  p.condition = 100;
  p.morale = Math.round(p.morale + (60 - p.morale) * 0.3);
  return [before, overall(p.attributes, p.position)];
}

/* ───────────────────────── RF41 — moral ───────────────────────── */

const ROLE_BONUS: Record<string, number> = { STARTER: 6, ROTATION: 2, BACKUP: -3, RESERVE: -6, SPECIALIST: 2 };
const EXPECTED_SNAPS: Partial<Record<Position, number>> = { QB: 0.95, LT: 0.95, LG: 0.95, C: 0.95, RG: 0.95, RT: 0.95, DE: 0.7, DT: 0.65, RB: 0.55, WR: 0.8, TE: 0.75, OLB: 0.8, MLB: 0.9, CB: 0.9, FS: 0.95, SS: 0.95, FB: 0.2 };

const labelOf = (m: number) => (m >= 75 ? "HIGH" : m >= 60 ? "GOOD" : m >= 45 ? "NEUTRAL" : m >= 30 ? "LOW" : "CRITICAL");
const trendOf = (d: number) => (d > 0.5 ? "UP" : d < -0.5 ? "DOWN" : "STABLE");

function roleOf(p: MockPlayer, labels: Map<string, string[]>): string {
  if (p.position === "K" || p.position === "P" || p.position === "LS") return "SPECIALIST";
  const slots = (labels.get(p.id) ?? []).filter((l) => !/^(KR|PR|H|THIRD_DOWN_BACK)\d+$/.test(l));
  if (!slots.length) return "RESERVE";
  const depth = Math.min(...slots.map((l) => Number(l.replace(/^\D+/, "")) || 9));
  const starters = SLOTS.find((s) => slots[0].startsWith(s.slot))?.minStarters ?? 1;
  return depth <= Math.max(1, starters) ? "STARTER" : depth === Math.max(1, starters) + 1 ? "ROTATION" : "BACKUP";
}

function moraleOf(x: Ctx): SquadMorale {
  const c = x.c;
  const me = x.wc.id;
  const labels = chartLabels(c.chart);
  const userGames = ALL_FIXTURES(x).filter((f) => f.userGame && f.homeScore != null).sort((a, b) => a.week - b.week);
  const results = userGames.map((f) => {
    const pf = f.homeClubId === me ? f.homeScore! : f.awayScore!, pa = f.homeClubId === me ? f.awayScore! : f.homeScore!;
    return { res: pf > pa ? 1 : pf < pa ? -1 : 0, diff: pf - pa };
  });
  const t = x.table.get(me)!;
  const streak = streakOf(t);
  const streakBonus = streak !== "—" && Number(streak.slice(1)) >= 3 ? (streak[0] === "W" ? 1 : streak[0] === "L" ? -1 : 0) : 0;
  const players: PlayerMorale[] = c.players
    .filter((p) => p.onClub && ["ACTIVE", "INACTIVE", "PRACTICE_SQUAD"].includes(p.rosterStatus))
    .map((p) => {
      const role = roleOf(p, labels);
      const finalYear = p.contract.endSeason <= yearOf(c);
      const ageBonus = p.age <= 24 ? 2 : p.age >= 31 ? -1 : 0;
      const target = p.morale + ROLE_BONUS[role] + (finalYear ? -5 : 0) + ageBonus;
      const valueAt = (n: number) => {
        let m = p.morale;
        for (let i = 0; i < n; i++) {
          const toward = (target - m) * 0.15;
          const result = results[i].res * 2 + clamp(results[i].diff / 14, -1, 1);
          m = m + clamp(toward + result + (i === n - 1 ? streakBonus : 0), -6, 5);
        }
        return clamp(m, 0, 100);
      };
      const n = results.length;
      const now = valueAt(n), prev = valueAt(Math.max(0, n - 1));
      const lastDelta = round3(n ? now - prev : 0);
      const last = results[n - 1];
      const expected = role === "SPECIALIST" || role === "RESERVE" ? null : role === "STARTER" ? (EXPECTED_SNAPS[p.position] ?? 0.7) : role === "ROTATION" ? 0.3 : 0.05;
      const share = (k: string) => (expected == null ? null : n ? round3(clamp(expected + (noise(`${p.id}:${k}:${n}`) - 0.5) * 0.2, 0, 1)) : 0);
      const morale = Math.round(now);
      const factors: Record<string, number> = n
        ? { BASELINE: round3((target - prev) * 0.15 - (ROLE_BONUS[role] + (finalYear ? -5 : 0) + ageBonus) * 0.15), ROLE: round3(ROLE_BONUS[role] * 0.15), CONTRACT: finalYear ? -0.75 : 0, AGE: round3(ageBonus * 0.15), RESULT: round3(last.res * 2 + clamp(last.diff / 14, -1, 1)), SNAPS: 0, STREAK: streakBonus, WEEK_LIMIT: 0 }
        : { BASELINE: 0, ROLE: 0, CONTRACT: 0, AGE: 0, RESULT: 0, SNAPS: 0, STREAK: 0, WEEK_LIMIT: 0 };
      return {
        playerId: p.id,
        name: p.name,
        position: p.position,
        jerseyNumber: p.jerseyNumber,
        rosterStatus: p.rosterStatus,
        morale,
        label: labelOf(morale),
        trend: trendOf(lastDelta),
        lastDelta,
        role,
        expectedSnapShare: expected,
        lastSnapShare: share("last"),
        seasonSnapShare: share("season"),
        contractFinalYear: finalYear,
        contractYearsLeft: Math.max(0, p.contract.endSeason - yearOf(c)),
        tradeRequest: morale < 35 && n >= 3,
        performanceModifier: round3(((morale - 50) / 50) * 0.05),
        factors,
      };
    });
  const squad = players.filter((p) => p.rosterStatus === "ACTIVE" || p.rosterStatus === "INACTIVE");
  const avg = squad.length ? Math.round(squad.reduce((a, p) => a + p.morale, 0) / squad.length) : 0;
  const avgDelta = squad.length ? round3(squad.reduce((a, p) => a + p.lastDelta, 0) / squad.length) : 0;
  const distribution: Record<string, number> = { HIGH: 0, GOOD: 0, NEUTRAL: 0, LOW: 0, CRITICAL: 0 };
  for (const p of squad) distribution[p.label] += 1;
  return {
    seasonId: c.summary.seasonId ?? "",
    throughWeek: Math.max(0, c.summary.currentWeek - 1),
    average: avg,
    label: labelOf(avg),
    trend: trendOf(avgDelta),
    lastDelta: avgDelta,
    distribution,
    tradeRequests: squad.filter((p) => p.tradeRequest).length,
    teamStreak: streak === "—" ? null : streak,
    record: plain(recordOf(t)),
    players,
  };
}

/* ───────────────────────── analista ───────────────────────── */

type Units = Record<string, number>;
const unitAvg = (players: MockPlayer[], picks: [Position, number][]) => {
  const xs = picks.flatMap(([pos, n]) => bestAt(players, pos, n)).map((p) => overall(p.attributes, p.position));
  return xs.length ? Math.round(xs.reduce((a, v) => a + v, 0) / xs.length) : 0;
};
function unitsOf(players: MockPlayer[]): Units {
  return {
    QB: unitAvg(players, [["QB", 1]]),
    OFFENSIVE_LINE: unitAvg(players, [["LT", 1], ["LG", 1], ["C", 1], ["RG", 1], ["RT", 1]]),
    RUNNING_BACK: unitAvg(players, [["RB", 1]]),
    RECEIVERS: unitAvg(players, [["WR", 3], ["TE", 1]]),
    DEFENSIVE_LINE: unitAvg(players, [["DE", 2], ["DT", 2]]),
    LINEBACKERS: unitAvg(players, [["OLB", 2], ["MLB", 1]]),
    SECONDARY: unitAvg(players, [["CB", 2], ["FS", 1], ["SS", 1]]),
    SPECIAL_TEAMS: unitAvg(players, [["K", 1], ["P", 1]]),
  };
}
const strengthOf = (u: Units) => Math.round(Object.values(u).reduce((a, v) => a + v, 0) / Object.values(u).length);

const unitCache = new Map<string, Units>();
function clubUnits(x: Ctx, club: WorldClub): Units {
  if (club.id === x.wc.id) return unitsOf(rosterOf(x, club));
  const key = `${x.c.summary.id}:${club.id}`;
  let u = unitCache.get(key);
  if (!u) {
    u = unitsOf(rosterOf(x, club));
    unitCache.set(key, u);
  }
  return u;
}

function matchupsOf(ours: Units, theirs: Units): AnalystMatchup[] {
  const m = (key: string, a: number, b: number): AnalystMatchup => ({ key, ours: a, theirs: b, edge: a - b, advantage: a - b >= 3 ? "OURS" : b - a >= 3 ? "THEIRS" : "EVEN" });
  const r = Math.round;
  return [
    m("PASS_OFFENSE_VS_SECONDARY", r((ours.QB + ours.RECEIVERS) / 2), theirs.SECONDARY),
    m("RUN_OFFENSE_VS_FRONT", r((ours.OFFENSIVE_LINE + ours.RUNNING_BACK) / 2), r((theirs.DEFENSIVE_LINE + theirs.LINEBACKERS) / 2)),
    m("OL_VS_DL", ours.OFFENSIVE_LINE, theirs.DEFENSIVE_LINE),
    m("SECONDARY_VS_PASS_OFFENSE", ours.SECONDARY, r((theirs.QB + theirs.RECEIVERS) / 2)),
    m("FRONT_VS_RUN_OFFENSE", r((ours.DEFENSIVE_LINE + ours.LINEBACKERS) / 2), r((theirs.OFFENSIVE_LINE + theirs.RUNNING_BACK) / 2)),
  ];
}

function planOf(c: StoredCareer): PlanTendencies {
  const plan = (c.ext as { plan?: { tendencies: PlanTendencies } } | undefined)?.plan?.tendencies;
  return plan ?? COACH_PLANS[c.coachStyle] ?? COACH_PLANS.OFFENSIVE;
}

function buildAnalysis(x: Ctx, now: Date): Analysis {
  const c = x.c;
  const ours = clubUnits(x, x.wc);
  const others = WORLD_CLUBS.map((club) => clubUnits(x, club));
  const league: Units = Object.fromEntries(Object.keys(ours).map((k) => [k, Math.round(others.reduce((a, u) => a + u[k], 0) / others.length)]));
  const units: UnitRating[] = Object.keys(ours).map((unit) => ({ unit, rating: ours[unit], leagueAverage: league[unit], diff: ours[unit] - league[unit] }));
  const strengths = units.filter((u) => u.diff >= 3).sort((a, b) => b.diff - a.diff);
  const weaknesses = units.filter((u) => u.diff <= -3).sort((a, b) => a.diff - b.diff);
  const upcoming: Outlook[] = ALL_FIXTURES(x)
    .filter((f) => f.userGame && f.homeScore == null && f.week >= c.summary.currentWeek)
    .slice(0, 3)
    .map((f) => {
      const home = f.homeClubId === x.wc.id;
      const opp = CLUB_BY_ID.get(home ? f.awayClubId : f.homeClubId)!;
      const theirs = clubUnits(x, opp);
      const neutral = f.week > REGULAR_WEEKS && f.week === REGULAR_WEEKS + PLAYOFF_KINDS.length;
      const edge = strengthOf(ours) - strengthOf(theirs) + (neutral ? 0 : home ? 1.5 : -1.5);
      const winProbability = round3(1 / (1 + Math.exp(-edge / 4)));
      const matchups = matchupsOf(ours, theirs);
      const threat = [...matchups].sort((a, b) => a.edge - b.edge)[0];
      const threatUnit: Record<string, string> = { PASS_OFFENSE_VS_SECONDARY: "SECONDARY", RUN_OFFENSE_VS_FRONT: "DEFENSIVE_LINE", OL_VS_DL: "DEFENSIVE_LINE", SECONDARY_VS_PASS_OFFENSE: "RECEIVERS", FRONT_VS_RUN_OFFENSE: "RUNNING_BACK" };
      return {
        fixtureId: f.id,
        week: f.week,
        opponent: clubRef(opp),
        home,
        neutralSite: neutral,
        strength: strengthOf(ours),
        opponentStrength: strengthOf(theirs),
        winProbability,
        verdict: winProbability >= 0.6 ? "FAVORITE" : winProbability <= 0.4 ? "UNDERDOG" : "EVEN",
        biggestThreat: threat && threat.edge < 0 ? threatUnit[threat.key] : null,
        matchups,
      };
    });
  // trocas no depth chart: disponível da mesma posição 3+ acima do titular da vaga
  const byId = new Map(c.players.map((p) => [p.id, p]));
  const depthChart: DepthChange[] = [];
  const starting = new Set(SLOTS.filter((s) => s.position).flatMap((s) => (c.chart[s.slot] ?? []).slice(0, Math.max(1, s.minStarters))));
  for (const slot of SLOTS.filter((s) => s.position)) {
    const ids = c.chart[slot.slot] ?? [];
    for (const outId of ids.slice(0, Math.max(1, slot.minStarters))) {
      const out = byId.get(outId);
      if (!out) continue;
      const cand = c.players.filter((p) => available(p) && p.position === slot.position && !starting.has(p.id)).sort((a, b) => overallAt(b, slot) - overallAt(a, slot))[0];
      if (cand && overallAt(cand, slot) - overallAt(out, slot) >= 3) {
        depthChart.push({ slot: slot.slot, inPlayerId: cand.id, inName: cand.name, inOverall: overallAt(cand, slot), outPlayerId: out.id, outName: out.name, outOverall: overallAt(out, slot), gain: overallAt(cand, slot) - overallAt(out, slot) });
        starting.add(cand.id);
      }
    }
  }
  // ajustes do plano salvo para o próximo jogo
  const plan = planOf(c);
  const gamePlan: PlanAdvice[] = [];
  const next = upcoming[0];
  if (next) {
    const [pass, run, , secondary, front] = next.matchups;
    const ratio = clamp(Math.round((50 + (pass.edge - run.edge) * 2) / 5) * 5, 20, 80);
    if (Math.abs(ratio - plan.passRatio) >= 5) gamePlan.push({ field: "passRatio", current: String(plan.passRatio), recommended: String(ratio), reason: pass.edge >= run.edge ? "PASS_EDGE" : "RUN_EDGE" });
    const focus = front.edge < secondary.edge - 3 ? "STOP_RUN" : secondary.edge < front.edge - 3 ? "STOP_PASS" : "BALANCED";
    if (focus !== plan.defensiveFocus) gamePlan.push({ field: "defensiveFocus", current: plan.defensiveFocus, recommended: focus, reason: focus === "STOP_RUN" ? "RUN_THREAT" : focus === "STOP_PASS" ? "PASS_THREAT" : "BALANCED_THREAT" });
    const aggression = next.verdict === "UNDERDOG" ? "AGGRESSIVE" : "NORMAL";
    if (aggression !== plan.fourthDownAggression) gamePlan.push({ field: "fourthDownAggression", current: plan.fourthDownAggression, recommended: aggression, reason: "WIN_PROBABILITY" });
  }
  const best = [...units].sort((a, b) => b.diff - a.diff)[0];
  const worst = [...units].sort((a, b) => a.diff - b.diff)[0];
  const summaryCode = next ? `NEXT_GAME_${next.verdict}` : "SEASON_OVERVIEW";
  const summaryParams: Record<string, ParamValue> = next ? { opponentId: next.opponent.id, winProbability: next.winProbability, bestUnit: best.unit, worstUnit: worst.unit } : { bestUnit: best.unit, worstUnit: worst.unit };
  const unitName = (u: string) => translate("season", `analysis.unit.${u}`);
  const summary = translate("season", `analysis.summary.${summaryCode}`, { best: unitName(best.unit), worst: unitName(worst.unit), opponent: next?.opponent.name ?? "", chance: next?.winProbability ?? 0 });
  return {
    id: uuidFrom("analysis", `${c.summary.id}:${now.getTime()}`),
    seasonId: c.summary.seasonId ?? "",
    week: c.summary.currentWeek,
    source: "RULES",
    generatedAt: now.toISOString(),
    summaryCode,
    summary,
    summaryParams,
    strength: strengthOf(ours),
    leagueStrength: strengthOf(league),
    units,
    strengths,
    weaknesses,
    upcoming,
    depthChart,
    gamePlan,
  };
}

/* ───────────────────────── fachada ───────────────────────── */

export interface MockSeason {
  standings(id: string): Standings;
  weekNews(id: string, week: number): WeekNews;
  playoffs(id: string): PlayoffSituation;
  bracket(id: string): Bracket;
  summary(id: string, number?: number): SeasonSummary;
  advance(id: string, req?: SeasonAdvanceRequest): SeasonAdvanceResult;
  trophies(id: string): Trophies;
  morale(id: string): SquadMorale;
  analysis(id: string): Analysis;
  refreshAnalysis(id: string): Analysis;
  /** o que o 6c acrescenta à central (D-106) */
  dashboardExtras(id: string): Pick<CareerDashboard, "morale" | "analysis" | "news" | "playoffs">;
}

export function mockSeason(user: UserAccount): MockSeason {
  const acc = mockCareerAccess(user);
  const ctx = (id: string) => ctxOf(acc.find(id));

  return {
    standings: (id) => standingsOf(ctx(id)),
    weekNews(id, week) {
      const x = ctx(id);
      const last = acc.lastWeek(x.c);
      if (!Number.isInteger(week) || week < 1 || week > last) throw ApiError.badRequest("INVALID_WEEK", { 0: week, 1: last });
      return { seasonId: x.c.summary.seasonId ?? "", week, weekKind: acc.weekKind(x.c, week), completed: week < x.c.summary.currentWeek, items: newsOf(x, week) };
    },
    playoffs: (id) => playoffsOf(ctx(id)),
    bracket: (id) => bracketOf(ctx(id)),
    summary(id, number) {
      const x = ctx(id);
      const n = seasonNumberOf(x.c);
      if (number == null || number === n) return summaryOfSeason(x);
      const rec = seasonState(x.c).history.find((h) => h.summary.number === number);
      if (!rec) throw ApiError.notFound("season");
      return structuredClone(rec.summary);
    },
    advance(id, req) {
      const c = acc.writable(id);
      const ss = seasonState(c);
      const n = seasonNumberOf(c);
      if (req?.seasonId && req.seasonId !== c.summary.seasonId) {
        const asked = ss.history.find((h) => h.summary.seasonId === req.seasonId);
        if (!asked) throw ApiError.notFound("season");
        throw new ApiError({ status: 409, code: "SEASON_ALREADY_ADVANCED", message: "", details: { seasonId: req.seasonId, currentSeasonId: c.summary.seasonId, currentSeasonNumber: n }, correlationId: null, locale: null, params: { 0: n } });
      }
      if (!ss.champion) {
        throw new ApiError({ status: 422, code: "SEASON_NOT_FINISHED", message: "", details: { phase: c.summary.seasonPhase ?? "REGULAR", currentWeek: c.summary.currentWeek }, correlationId: null, locale: null, params: { 0: c.summary.seasonPhase ?? "REGULAR" } });
      }
      const x = ctxOf(c);
      const summary = summaryOfSeason(x);
      const year = yearOf(c);
      const trophies = trophiesOfSeason(x, summary);
      const mvp = summary.awards.find((a) => a.type === "MVP") ?? null;
      const record: MockSeasonRecord = {
        summary: { ...summary, phase: "OFFSEASON" },
        trophies,
        history: { seasonId: summary.seasonId, number: n, year, completed: true, champion: summary.champion, runnerUp: summary.runnerUp, mvp, userRecord: summary.campaign?.record ?? null, userResult: summary.campaign?.playoffResult ?? null },
        playoffAppearance: summary.campaign?.seed != null,
      };
      const carried = deadMoneyNextSeason(c);
      const aiRenewals = WORLD_CLUBS.filter((club) => club.id !== x.wc.id).reduce((a, club) => a + rosterOf(x, club).filter((p) => p.contract.endSeason <= year).length, 0);
      const previousSeasonId = c.summary.seasonId ?? "";
      // (1) waivers pendentes; (2) contratos que terminam viram agentes livres (D-100)
      clearWaivers(c, c.summary.currentWeek);
      const expired = expiringOf(c, year);
      for (const e of expired) {
        const p = c.players.find((q) => q.id === e.playerId)!;
        const from = p.rosterStatus;
        p.rosterStatus = "FREE_AGENT";
        p.onClub = false;
        if (removeFromChart(c.chart, p.id)) c.chartVersion += 1;
        c.moves.push({ id: uuidFrom("move", `${c.summary.id}:${c.moves.length}:${p.id}`), week: c.summary.currentWeek, kind: "CONTRACT_EXPIRED", fromStatus: from, toStatus: "FREE_AGENT", deadMoney: 0, at: new Date().toISOString(), playerId: p.id, capHitRemoved: 0, season: n });
      }
      // (5) pré-temporada: envelhecimento e progressão (D-101), temporada nova na semana 1
      let improved = 0, declined = 0;
      for (const p of c.players.filter((q) => q.onClub)) {
        const [before, after] = progress(p);
        if (after > before) improved++;
        else if (after < before) declined++;
      }
      ss.history.push(record);
      ss.number = n + 1;
      ss.playoffs = null;
      ss.champion = null;
      ss.runnerUp = null;
      ss.overtime = {};
      c.results = {};
      c.liveFixtureId = null;
      if (c.ext) (c.ext as { sessions?: unknown }).sessions = {};
      c.summary.seasonId = uuidFrom("season", `${c.summary.id}:${ss.number}`);
      c.summary.seasonYear = yearOf(c);
      c.summary.seasonPhase = "REGULAR";
      c.summary.currentWeek = 1;
      c.summary.version += 1;
      c.summary.lastPlayedAt = new Date().toISOString();
      acc.commit();
      return {
        previousSeasonId,
        seasonId: c.summary.seasonId,
        seasonNumber: ss.number,
        seasonYear: yearOf(c),
        phase: "REGULAR",
        currentWeek: 1,
        steps: [
          { phase: "RENEWALS", status: "DONE", handledBy: "SEASON_ADVANCE" },
          { phase: "FREE_AGENCY", status: "SKIPPED", handledBy: "BLOCK_7" },
          { phase: "DRAFT", status: "SKIPPED", handledBy: "BLOCK_7" },
          { phase: "PRESEASON", status: "DONE", handledBy: "SEASON_ADVANCE" },
        ],
        // a IA renova sozinha (Q-42, D-120): no mock os elencos da IA são gerados na hora, então todo contrato da IA que termina conta
        // como renovado e só os do usuário viram agentes livres
        expiredContracts: expired.length,
        aiRenewals,
        userExpiredContracts: expired,
        improved,
        declined,
        deadMoneyCarried: carried,
        career: summaryOf(c),
      };
    },
    trophies(id) {
      const x = ctx(id);
      const ss = seasonState(x.c);
      const all = ss.history.flatMap((h) => h.trophies).map((t) => ({ ...t, userClub: t.club?.id === x.wc.id }));
      const mine = all.filter((t) => t.userClub);
      const current = summaryOfSeason(x);
      const seasons = [
        ...ss.history.map((h) => structuredClone(h.history)),
        { seasonId: current.seasonId, number: current.number, year: current.year, completed: current.completed, champion: current.champion, runnerUp: current.runnerUp, mvp: current.completed ? (current.awards.find((a) => a.type === "MVP") ?? null) : null, userRecord: current.campaign?.record ?? null, userResult: current.campaign?.playoffResult ?? null },
      ];
      return {
        userClubId: x.wc.id,
        championships: mine.filter((t) => t.kind === "CHAMPIONSHIP").length,
        conferenceTitles: mine.filter((t) => t.kind === "CONFERENCE").length,
        divisionTitles: mine.filter((t) => t.kind === "DIVISION").length,
        playoffAppearances: ss.history.filter((h) => h.playoffAppearance).length,
        trophies: all.sort((a, b) => b.seasonNumber - a.seasonNumber || ["CHAMPIONSHIP", "CONFERENCE", "DIVISION"].indexOf(a.kind) - ["CHAMPIONSHIP", "CONFERENCE", "DIVISION"].indexOf(b.kind)),
        seasons: seasons.sort((a, b) => b.number - a.number),
      };
    },
    morale: (id) => moraleOf(ctx(id)),
    analysis(id) {
      const c = acc.find(id);
      const report = c.analysis?.report;
      if (!report) throw ApiError.of(404, "ANALYSIS_NOT_FOUND");
      return structuredClone(report);
    },
    refreshAnalysis(id) {
      const c = acc.find(id);
      const now = Date.now();
      const st = (c.analysis ??= { report: null, calls: [] });
      st.calls = st.calls.filter((t) => now - t < ANALYSIS_WINDOW_MS);
      if (st.calls.length >= ANALYSIS_LIMIT) {
        const retry = Math.max(1, Math.ceil((ANALYSIS_WINDOW_MS - (now - st.calls[0])) / 1000));
        throw new ApiError({ status: 429, code: "RATE_LIMITED", message: "", details: { retryAfterSeconds: retry, rule: "analysis" }, correlationId: null, locale: null });
      }
      st.calls.push(now);
      st.report = buildAnalysis(ctxOf(c), new Date(now));
      acc.commit();
      return structuredClone(st.report);
    },
    dashboardExtras(id) {
      const x = ctx(id);
      const m = moraleOf(x);
      const report = x.c.analysis?.report ?? null;
      const lastWeek = Math.min(acc.lastWeek(x.c), x.c.summary.currentWeek - 1);
      return {
        morale: { average: m.average, label: m.label, trend: m.trend, low: (m.distribution.LOW ?? 0) + (m.distribution.CRITICAL ?? 0), tradeRequests: m.tradeRequests },
        analysis: report ? { id: report.id, week: report.week, generatedAt: report.generatedAt, summaryCode: report.summaryCode, summary: report.summary, nextWinProbability: report.upcoming[0]?.winProbability ?? null } : null,
        news: lastWeek >= 1 ? newsOf(x, lastWeek).slice(0, 3) : [],
        playoffs: userPlayoff(x),
      };
    },
  };
}
