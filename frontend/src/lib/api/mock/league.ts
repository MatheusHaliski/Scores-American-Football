/**
 * Liga do modo mock (bloco 6c), pura e sem estado: as 32 franquias do mundo padrão, a classificação pelos placares gravados, as
 * sementes por conferência, o que cada franquia já garantiu e a chave dos playoffs rodada a rodada. Segue as regras do backend nos
 * números (7 sementes por conferência, 1 folga, re-seed a cada rodada, final em campo neutro com mando da melhor semente), mas a
 * cadeia de desempate é a versão par a par (D-110): percentual, confronto direto, campanha na divisão (mesma divisão) ou na
 * conferência, saldo, projeção (prestígio e nome, só sem jogos) e sorteio pelo nome.
 */
import type { ClubRef, KickoffSlot, TeamRecord } from "../career-types";
import type { PlayoffRound } from "../season-types";
import { hashString, uuidFrom } from "./rng";
import { CLUBS, CONFERENCES, REGULAR_WEEKS } from "./world-data";

export const SEEDS_PER_CONFERENCE = 7;
export const BYE_SEEDS = 1;
/** Rodadas dos playoffs contadas do fim (7 sementes, 1 folga: 4 rodadas, semanas 19 a 22). */
export const PLAYOFF_KINDS: PlayoffRound[] = ["WILD_CARD", "DIVISIONAL", "CONFERENCE", "FINAL"];
export const LAST_PLAYOFF_WEEK = REGULAR_WEEKS + PLAYOFF_KINDS.length;

export interface WorldClub {
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

export const WORLD_CLUBS: WorldClub[] = CLUBS.map(([shortName, name, city, primaryColor, secondaryColor, , conference, division, prestige], index) => ({
  index,
  id: uuidFrom("club", shortName),
  shortName,
  name,
  city,
  primaryColor,
  secondaryColor,
  conference,
  division,
  prestige,
}));
export const CLUB_BY_ID = new Map(WORLD_CLUBS.map((c) => [c.id, c]));

export function clubRef(c: WorldClub): ClubRef {
  return { id: c.id, name: c.name, shortName: c.shortName, city: c.city, primaryColor: c.primaryColor, secondaryColor: c.secondaryColor, conference: c.conference, division: c.division };
}

/* ───────────────────────── campanhas ───────────────────────── */

/** Jogo encerrado (placar gravado). */
export interface PlayedGame {
  id: string;
  week: number;
  home: string;
  away: string;
  homeScore: number;
  awayScore: number;
  overtime?: boolean;
}

type Rec = [number, number, number];
export interface Tally {
  id: string;
  wins: number;
  losses: number;
  ties: number;
  pointsFor: number;
  pointsAgainst: number;
  home: Rec;
  away: Rec;
  division: Rec;
  conference: Rec;
  /** resultados em ordem de semana */
  results: ("W" | "L" | "T")[];
  /** jogos contra cada adversário: [vitórias, derrotas, empates] */
  vs: Map<string, Rec>;
  /** jogos da temporada regular que faltam */
  left: number;
}

const blank = (id: string): Tally => ({ id, wins: 0, losses: 0, ties: 0, pointsFor: 0, pointsAgainst: 0, home: [0, 0, 0], away: [0, 0, 0], division: [0, 0, 0], conference: [0, 0, 0], results: [], vs: new Map(), left: 0 });

export const games = (t: Tally) => t.wins + t.losses + t.ties;
/** Percentual com empate valendo meio jogo; 0 sem jogos. */
export const pct = (t: { wins: number; losses: number; ties: number }) => (t.wins + t.losses + t.ties ? (t.wins + t.ties / 2) / (t.wins + t.losses + t.ties) : 0);
const recPct = (r: Rec) => pct({ wins: r[0], losses: r[1], ties: r[2] });

export function summaryOf(w: number, l: number, t: number): string {
  return t > 0 ? `${w}-${l}-${t}` : `${w}-${l}`;
}
export function recordOf(r: Rec | Tally): TeamRecord {
  const [w, l, t] = Array.isArray(r) ? r : [r.wins, r.losses, r.ties];
  return { wins: w, losses: l, ties: t, summary: summaryOf(w, l, t) };
}

/**
 * Campanhas da temporada regular pelos placares (`played`, só semanas ≤ 18) e jogos que faltam (`scheduled`, todos os jogos da
 * temporada regular, para contar o que sobra).
 */
export function tallies(played: PlayedGame[], scheduled: { week: number; home: string; away: string }[]): Map<string, Tally> {
  const out = new Map(WORLD_CLUBS.map((c) => [c.id, blank(c.id)]));
  const done = new Set(played.map((g) => `${g.week}:${g.home}:${g.away}`));
  for (const f of scheduled) {
    if (f.week > REGULAR_WEEKS || done.has(`${f.week}:${f.home}:${f.away}`)) continue;
    out.get(f.home)!.left++;
    out.get(f.away)!.left++;
  }
  const add = (r: Rec, res: "W" | "L" | "T") => (res === "W" ? r[0]++ : res === "L" ? r[1]++ : r[2]++);
  for (const g of [...played].filter((x) => x.week <= REGULAR_WEEKS).sort((a, b) => a.week - b.week)) {
    const h = CLUB_BY_ID.get(g.home)!, a = CLUB_BY_ID.get(g.away)!;
    for (const [me, opp, pf, pa, isHome] of [[h, a, g.homeScore, g.awayScore, true], [a, h, g.awayScore, g.homeScore, false]] as const) {
      const t = out.get(me.id)!;
      const res = pf > pa ? "W" : pf < pa ? "L" : "T";
      if (res === "W") t.wins++;
      else if (res === "L") t.losses++;
      else t.ties++;
      t.pointsFor += pf;
      t.pointsAgainst += pa;
      add(isHome ? t.home : t.away, res);
      if (me.division === opp.division) add(t.division, res);
      if (me.conference === opp.conference) add(t.conference, res);
      t.results.push(res);
      const v = t.vs.get(opp.id) ?? [0, 0, 0];
      add(v, res);
      t.vs.set(opp.id, v);
    }
  }
  return out;
}

export function streakOf(t: Tally): string {
  const last = t.results[t.results.length - 1];
  if (!last) return "—";
  let n = 0;
  for (let i = t.results.length - 1; i >= 0 && t.results[i] === last; i--) n++;
  return `${last}${n}`;
}

/* ───────────────────────── desempate par a par ───────────────────────── */

export type Breaker = "HEAD_TO_HEAD" | "DIVISION" | "CONFERENCE" | "POINT_DIFFERENTIAL" | "PROJECTION" | "DRAW";
export const DIVISION_CHAIN: Breaker[] = ["HEAD_TO_HEAD", "DIVISION", "CONFERENCE", "POINT_DIFFERENTIAL", "PROJECTION", "DRAW"];
export const WILD_CARD_CHAIN: Breaker[] = ["HEAD_TO_HEAD", "CONFERENCE", "POINT_DIFFERENTIAL", "PROJECTION", "DRAW"];

/**
 * Compara duas franquias (negativo = `a` na frente) e diz o critério que decidiu (null quando o percentual já separou). `salt` é a
 * semente do sorteio (a do calendário, como o backend).
 */
export function compare(a: Tally, b: Tally, chain: Breaker[], salt: string): { order: number; by: Breaker | null } {
  const d = pct(b) - pct(a);
  if (Math.abs(d) > 1e-9) return { order: d, by: null };
  const ca = CLUB_BY_ID.get(a.id)!, cb = CLUB_BY_ID.get(b.id)!;
  for (const by of chain) {
    let o = 0;
    if (by === "HEAD_TO_HEAD") {
      const v = a.vs.get(b.id);
      if (v && v[0] + v[1] + v[2] > 0) o = recPct([v[1], v[0], v[2]]) - recPct(v);
    } else if (by === "DIVISION") {
      if (ca.division === cb.division) o = recPct(b.division) - recPct(a.division);
    } else if (by === "CONFERENCE") {
      o = recPct(b.conference) - recPct(a.conference);
    } else if (by === "POINT_DIFFERENTIAL") {
      o = b.pointsFor - b.pointsAgainst - (a.pointsFor - a.pointsAgainst);
    } else if (by === "PROJECTION") {
      if (games(a) === 0 && games(b) === 0) o = cb.prestige - ca.prestige || ca.name.localeCompare(cb.name);
    } else {
      o = (hashString(`${salt}:${ca.name}`) % 1000) - (hashString(`${salt}:${cb.name}`) % 1000) || ca.name.localeCompare(cb.name);
    }
    if (Math.abs(o) > 1e-9) return { order: o, by };
  }
  return { order: 0, by: null };
}

/** Ordena e anota, em cada linha, o critério que a pôs à frente da empatada logo abaixo (null sem empate de percentual). */
export function rank(list: Tally[], chain: Breaker[], salt: string): { t: Tally; by: Breaker | null }[] {
  const sorted = [...list].sort((a, b) => compare(a, b, chain, salt).order);
  return sorted.map((t, i) => {
    const next = sorted[i + 1];
    return { t, by: next ? compare(t, next, chain, salt).by : null };
  });
}

/* ───────────────────────── sementes e garantias ───────────────────────── */

export interface ConferenceOrder {
  shortName: string;
  /** sementes 1..7 (ids), campeões de divisão primeiro */
  seeds: string[];
  /** a conferência inteira: sementes, depois o resto pela ordem dos wild cards */
  order: { t: Tally; by: Breaker | null }[];
  divisions: { name: string; order: { t: Tally; by: Breaker | null }[] }[];
}

export function conferenceOrders(table: Map<string, Tally>, salt: string): ConferenceOrder[] {
  return CONFERENCES.map((conf) => {
    const divisions = conf.divisions.map((name) => ({ name, order: rank(WORLD_CLUBS.filter((c) => c.division === name).map((c) => table.get(c.id)!), DIVISION_CHAIN, salt) }));
    const leaders = rank(divisions.map((d) => d.order[0].t), WILD_CARD_CHAIN, salt);
    const leaderIds = new Set(leaders.map((x) => x.t.id));
    const rest = rank(WORLD_CLUBS.filter((c) => c.conference === conf.shortName && !leaderIds.has(c.id)).map((c) => table.get(c.id)!), WILD_CARD_CHAIN, salt);
    const order = [...leaders, ...rest];
    return { shortName: conf.shortName, seeds: order.slice(0, SEEDS_PER_CONFERENCE).map((x) => x.t.id), order, divisions };
  });
}

export type ClinchMark = "BYE" | "DIVISION" | "PLAYOFFS" | "ELIMINATED" | null;

/**
 * Garantias por conta conservadora (D-103 do backend, sem desempate): eliminada quando 7 rivais da conferência já têm mais vitórias
 * que o máximo dela; vaga quando menos de 7 rivais ainda podem alcançá-la; divisão quando nenhum rival da divisão alcança; folga
 * quando, além da divisão, nenhum rival da conferência alcança.
 */
export function clinchOf(id: string, table: Map<string, Tally>): ClinchMark {
  const me = table.get(id)!;
  const club = CLUB_BY_ID.get(id)!;
  const rivals = WORLD_CLUBS.filter((c) => c.conference === club.conference && c.id !== id).map((c) => table.get(c.id)!);
  const max = (t: Tally) => t.wins + t.left;
  if (rivals.filter((r) => r.wins > max(me)).length >= SEEDS_PER_CONFERENCE) return "ELIMINATED";
  const catching = rivals.filter((r) => max(r) >= me.wins);
  const division = WORLD_CLUBS.filter((c) => c.division === club.division && c.id !== id).every((c) => max(table.get(c.id)!) < me.wins);
  if (division && catching.length === 0) return "BYE";
  if (division) return "DIVISION";
  if (catching.length < SEEDS_PER_CONFERENCE) return "PLAYOFFS";
  return null;
}

/* ───────────────────────── playoffs ───────────────────────── */

export interface MockPlayoffGame {
  id: string;
  home: string;
  away: string;
  homeSeed: number;
  awaySeed: number;
  /** sigla da conferência; null na final */
  conference: string | null;
  neutral: boolean;
  /** dias depois da quinta-feira da semana */
  day: number;
  slot: KickoffSlot;
}

export interface MockRound {
  kind: PlayoffRound;
  week: number;
  games: MockPlayoffGame[];
  /** ids das sementes que folgam */
  byes: string[];
}

export interface MockPlayoffs {
  /** sementes congeladas por conferência (ids, semente 1 primeiro) */
  seeds: Record<string, string[]>;
  rounds: MockRound[];
}

const SLOTS: [number, KickoffSlot][] = [[3, "SUN_EARLY"], [3, "SUN_EARLY"], [3, "SUN_LATE"], [3, "SUN_LATE"], [3, "SUN_NIGHT"], [4, "MON_NIGHT"]];

export function seedOfIn(p: MockPlayoffs, id: string): number | null {
  for (const ids of Object.values(p.seeds)) {
    const i = ids.indexOf(id);
    if (i >= 0) return i + 1;
  }
  return null;
}

/** Vencedor de um jogo encerrado (empate de playoff já vem resolvido pelo fechamento da semana). */
export function winnerOf(g: { home: string; away: string }, score: [number, number] | undefined): string | null {
  if (!score) return null;
  return score[0] >= score[1] ? g.home : g.away;
}

/**
 * Próxima rodada a partir das sementes e dos placares das rodadas anteriores: na primeira folgam as `BYE_SEEDS` melhores; depois,
 * re-seed (a melhor semente viva pega a pior, mando da melhor); com uma viva por conferência, a final em campo neutro, mando da
 * melhor semente. Null quando a chave acabou ou falta placar.
 */
export function nextRound(p: MockPlayoffs, results: Record<string, [number, number]>, idOf: (round: number, game: number) => string): MockRound | null {
  const index = p.rounds.length;
  if (index >= PLAYOFF_KINDS.length) return null;
  const week = REGULAR_WEEKS + index + 1;
  const alive: Record<string, string[]> = {};
  for (const [conf, ids] of Object.entries(p.seeds)) {
    let left = [...ids];
    for (const r of p.rounds) {
      for (const g of r.games) {
        const w = winnerOf(g, results[g.id]);
        if (!w) return null;
        const loser = w === g.home ? g.away : g.home;
        left = left.filter((x) => x !== loser);
      }
    }
    alive[conf] = left;
  }
  const seed = (id: string) => seedOfIn(p, id) ?? 99;
  const games: MockPlayoffGame[] = [];
  const byes: string[] = [];
  const confs = Object.keys(alive).sort();
  if (confs.every((c) => alive[c].length <= 1)) {
    const champs = confs.map((c) => alive[c][0]).filter(Boolean).sort((a, b) => seed(a) - seed(b));
    if (champs.length < 2) return null;
    games.push({ id: idOf(index, 0), home: champs[0], away: champs[1], homeSeed: seed(champs[0]), awaySeed: seed(champs[1]), conference: null, neutral: true, day: 3, slot: "SUN_NIGHT" });
    return { kind: "FINAL", week, games, byes };
  }
  for (const conf of confs) {
    const ids = [...alive[conf]].sort((a, b) => seed(a) - seed(b));
    const playing = index === 0 ? ids.slice(BYE_SEEDS) : ids;
    if (index === 0) byes.push(...ids.slice(0, BYE_SEEDS));
    for (let i = 0; i < Math.floor(playing.length / 2); i++) {
      const home = playing[i], away = playing[playing.length - 1 - i];
      games.push({ id: "", home, away, homeSeed: seed(home), awaySeed: seed(away), conference: conf, neutral: false, day: 3, slot: "SUN_EARLY" });
    }
  }
  games.forEach((g, i) => {
    const [day, slot] = SLOTS[Math.min(i, SLOTS.length - 1)];
    g.id = idOf(index, i);
    g.day = day;
    g.slot = slot;
  });
  return { kind: PLAYOFF_KINDS[index], week, games, byes };
}
