/**
 * Regras do depth chart no modo mock — port de `ChartSlot` e `DepthChartPolicy` (backend, bloco 6a): vagas por posição e os papéis
 * KR, PR, H e THIRD_DOWN_BACK, mínimo de titulares, disponibilidade (ativo e sem suspensão), fora de posição (papel: posição que
 * não faz o papel; vaga de posição: nem natural nem secundária e overall na vaga 5 abaixo do natural), limite de ativos do dia de
 * jogo e o depth chart padrão (melhores por overall na vaga, cortados na profundidade padrão e aparados até os 46).
 */
import { overall, type Position } from "@/lib/ratings/overall";
import type { DepthIssue, Unit } from "../career-types";
import type { MockPlayer } from "./players";

export interface SlotDef {
  slot: string;
  unit: Unit;
  position: Position | null;
  minStarters: number;
  naturals: Position[];
  defaultDepth: number;
}

const pos = (p: Position, unit: Unit, min: number, depth: number): SlotDef => ({ slot: p, unit, position: p, minStarters: min, naturals: [p], defaultDepth: depth });

/** Ordem de `ChartSlot` (a mesma das vagas no GET). */
export const SLOTS: SlotDef[] = [
  pos("QB", "OFFENSE", 1, 3), pos("RB", "OFFENSE", 1, 3), pos("FB", "OFFENSE", 0, 1), pos("WR", "OFFENSE", 3, 6), pos("TE", "OFFENSE", 1, 3),
  pos("LT", "OFFENSE", 1, 2), pos("LG", "OFFENSE", 1, 2), pos("C", "OFFENSE", 1, 2), pos("RG", "OFFENSE", 1, 2), pos("RT", "OFFENSE", 1, 2),
  pos("DE", "DEFENSE", 2, 4), pos("DT", "DEFENSE", 2, 4), pos("OLB", "DEFENSE", 2, 4), pos("MLB", "DEFENSE", 1, 2), pos("CB", "DEFENSE", 2, 5),
  pos("FS", "DEFENSE", 1, 2), pos("SS", "DEFENSE", 1, 2), pos("K", "SPECIAL_TEAMS", 1, 1), pos("P", "SPECIAL_TEAMS", 1, 1), pos("LS", "SPECIAL_TEAMS", 1, 1),
  { slot: "KR", unit: "SPECIAL_TEAMS", position: null, minStarters: 1, naturals: ["RB", "WR", "CB", "FS", "SS"], defaultDepth: 2 },
  { slot: "PR", unit: "SPECIAL_TEAMS", position: null, minStarters: 1, naturals: ["RB", "WR", "CB", "FS", "SS"], defaultDepth: 2 },
  { slot: "H", unit: "SPECIAL_TEAMS", position: null, minStarters: 1, naturals: ["P", "QB", "K"], defaultDepth: 2 },
  { slot: "THIRD_DOWN_BACK", unit: "OFFENSE", position: null, minStarters: 0, naturals: ["RB", "FB", "WR"], defaultDepth: 1 },
];
export const SLOT_BY_KEY = new Map(SLOTS.map((s) => [s.slot, s]));
export const OUT_OF_POSITION_MARGIN = 5;

export type Chart = Record<string, string[]>;

export function emptyChart(): Chart {
  return Object.fromEntries(SLOTS.map((s) => [s.slot, [] as string[]]));
}

export function available(p: MockPlayer | undefined): boolean {
  return !!p && p.onClub && p.rosterStatus === "ACTIVE" && p.suspensionWeeks === 0;
}

export function naturalOverall(p: MockPlayer): number {
  return overall(p.attributes, p.position);
}

export function overallAt(p: MockPlayer, s: SlotDef): number {
  return overall(p.attributes, s.position ?? p.position);
}

export function outOfPosition(p: MockPlayer, s: SlotDef): boolean {
  if (!s.position) return !s.naturals.includes(p.position);
  if (p.position === s.position || p.secondaryPosition === s.position) return false;
  return overallAt(p, s) < naturalOverall(p) - OUT_OF_POSITION_MARGIN;
}

export interface Validation {
  errors: DepthIssue[];
  warnings: DepthIssue[];
  uniquePlayers: number;
}

export function validate(chart: Chart, players: Map<string, MockPlayer>, gameDayActives: number): Validation {
  const errors: DepthIssue[] = [];
  const warnings: DepthIssue[] = [];
  const unique = new Set<string>();
  for (const s of SLOTS) {
    const ids = chart[s.slot] ?? [];
    const seen = new Set<string>();
    let here = 0;
    ids.forEach((id, i) => {
      const depth = i + 1;
      if (seen.has(id)) {
        errors.push({ code: "DEPTH_CHART_DUPLICATE_PLAYER", slot: s.slot, depth, playerId: id });
        return;
      }
      seen.add(id);
      const p = players.get(id);
      if (!available(p)) {
        errors.push({ code: "DEPTH_CHART_PLAYER_UNAVAILABLE", slot: s.slot, depth, playerId: id });
        return;
      }
      here++;
      unique.add(id);
      if (outOfPosition(p!, s)) warnings.push({ code: "OUT_OF_POSITION", slot: s.slot, depth, playerId: id });
    });
    if (here < s.minStarters) errors.push({ code: "DEPTH_CHART_INCOMPLETE", slot: s.slot, depth: null, playerId: null });
  }
  if (unique.size > gameDayActives) errors.push({ code: "DEPTH_CHART_TOO_MANY_ACTIVES", slot: null, depth: null, playerId: null });
  return { errors, warnings, uniquePlayers: unique.size };
}

const uniqueCount = (chart: Chart) => new Set(Object.values(chart).flat()).size;

/** Depth chart padrão: os melhores disponíveis por overall na vaga, com a profundidade padrão, aparado até os ativos do jogo. */
export function defaultChart(players: MockPlayer[], gameDayActives: number): Chart {
  const pool = players.filter(available);
  const chart = emptyChart();
  const byOverall = (s: SlotDef) => (a: MockPlayer, b: MockPlayer) => overallAt(b, s) - overallAt(a, s) || a.jerseyNumber - b.jerseyNumber;
  for (const s of SLOTS) {
    if (s.position) {
      const naturals = pool.filter((p) => p.position === s.position).sort(byOverall(s));
      const subs = pool.filter((p) => p.position !== s.position && p.secondaryPosition === s.position).sort(byOverall(s));
      let list = [...naturals, ...subs];
      // linha ofensiva sem reserva natural: o melhor da mesma família que ainda não é titular de outra vaga
      if (list.length < s.defaultDepth && ["LT", "LG", "C", "RG", "RT"].includes(s.position)) {
        const extra = pool.filter((p) => ["LT", "LG", "C", "RG", "RT"].includes(p.position) && !list.includes(p)).sort(byOverall(s));
        list = [...list, ...extra];
      }
      chart[s.slot] = list.slice(0, s.defaultDepth).map((p) => p.id);
    }
  }
  const rbs = chart.RB ?? [];
  const byId = new Map(pool.map((p) => [p.id, p]));
  const returners = pool
    .filter((p) => ["RB", "WR", "CB", "FS", "SS"].includes(p.position))
    .sort((a, b) => b.attributes.kicking.kickReturn + b.attributes.physical.speed - (a.attributes.kicking.kickReturn + a.attributes.physical.speed));
  chart.KR = returners.slice(0, 2).map((p) => p.id);
  chart.PR = returners.slice(1, 3).map((p) => p.id);
  chart.H = [...(chart.P ?? []), ...(chart.QB ?? []).slice(1, 2)].filter((id) => byId.has(id)).slice(0, 2);
  chart.THIRD_DOWN_BACK = rbs.length > 1 ? [rbs[1]] : rbs.slice(0, 1);

  while (uniqueCount(chart) > gameDayActives) {
    let deepest: SlotDef | null = null;
    let slack = 0;
    for (const s of SLOTS) {
      const extra = chart[s.slot].length - Math.max(1, s.minStarters);
      if (extra > 0 && extra >= slack) {
        deepest = s;
        slack = extra;
      }
    }
    if (!deepest) break;
    chart[deepest.slot] = chart[deepest.slot].slice(0, -1);
  }
  return chart;
}

/** Tira o jogador de todas as vagas (dispensa, practice squad, IR, inativo). */
export function removeFromChart(chart: Chart, playerId: string): boolean {
  let changed = false;
  for (const k of Object.keys(chart)) {
    const before = chart[k].length;
    chart[k] = chart[k].filter((id) => id !== playerId);
    changed ||= chart[k].length !== before;
  }
  return changed;
}

/** Rótulos das vagas de cada jogador ("QB1", "KR2"). */
export function chartLabels(chart: Chart): Map<string, string[]> {
  const out = new Map<string, string[]>();
  for (const s of SLOTS) {
    (chart[s.slot] ?? []).forEach((id, i) => {
      const l = out.get(id) ?? [];
      l.push(`${s.slot}${i + 1}`);
      out.set(id, l);
    });
  }
  return out;
}
