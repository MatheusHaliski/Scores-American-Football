/**
 * Edição do depth chart no navegador (RF60): rascunho por vaga, mover/subir/descer/tirar e a mesma validação do backend
 * (`DepthChartPolicy`) para mostrar os problemas antes de salvar — vaga incompleta, jogador indisponível ou repetido, mais que os
 * ativos do dia de jogo e fora de posição. O servidor continua sendo quem decide no PUT (422/409); isto só antecipa.
 */
import type { DepthChart, DepthIssue, DepthSlot, Position, RosterStatus } from "@/lib/api/types";

/** Posições que fazem cada papel sem aviso (`ChartSlot.naturalPositions`). */
export const ROLE_NATURALS: Record<string, Position[]> = {
  KR: ["RB", "WR", "CB", "FS", "SS"],
  PR: ["RB", "WR", "CB", "FS", "SS"],
  H: ["P", "QB", "K"],
  THIRD_DOWN_BACK: ["RB", "FB", "WR"],
};
export const OUT_OF_POSITION_MARGIN = 5;

export type Draft = Record<string, string[]>;

/** O que o editor sabe de cada jogador (do elenco e das vagas já salvas). */
export interface DepthPlayerInfo {
  id: string;
  name: string;
  jerseyNumber: number;
  position: Position;
  secondaryPosition: Position | null;
  /** overall na posição natural */
  overall: number;
  tier: string;
  rosterStatus: RosterStatus;
  suspensionWeeks: number;
  /** overall por posição, quando conhecido (página do jogador) */
  byPosition?: Partial<Record<Position, number>>;
}

export function draftFrom(dc: DepthChart): Draft {
  const out: Draft = {};
  for (const u of dc.units) for (const s of u.slots) out[s.slot] = s.players.map((p) => p.playerId);
  return out;
}

export function slotsOf(dc: DepthChart): DepthSlot[] {
  return dc.units.flatMap((u) => u.slots);
}

/** Overall já calculado pelo servidor para cada jogador em cada vaga salva ("SLOT:id" → overall). */
export function knownAtSlot(dc: DepthChart): Map<string, number> {
  const m = new Map<string, number>();
  for (const s of slotsOf(dc)) for (const p of s.players) m.set(`${s.slot}:${p.playerId}`, p.overallAtSlot);
  return m;
}

export function isAvailable(p: DepthPlayerInfo | undefined): boolean {
  return !!p && p.rosterStatus === "ACTIVE" && p.suspensionWeeks === 0;
}

/** Overall do jogador na vaga (nos papéis, o da posição natural); `null` quando ainda não se sabe. */
export function overallAt(p: DepthPlayerInfo, slot: DepthSlot, known: Map<string, number>): number | null {
  if (!slot.position) return p.overall;
  if (slot.position === p.position) return p.overall;
  return p.byPosition?.[slot.position] ?? known.get(`${slot.slot}:${p.id}`) ?? null;
}

/** Fora de posição pela regra do backend; sem o overall na vaga, posição diferente da natural e da secundária conta como fora. */
export function isOutOfPosition(p: DepthPlayerInfo, slot: DepthSlot, known: Map<string, number>): boolean {
  if (!slot.position) return !(ROLE_NATURALS[slot.slot] ?? []).includes(p.position);
  if (p.position === slot.position || p.secondaryPosition === slot.position) return false;
  const at = overallAt(p, slot, known);
  return at === null ? true : at < p.overall - OUT_OF_POSITION_MARGIN;
}

export interface DraftCheck {
  errors: DepthIssue[];
  warnings: DepthIssue[];
  uniquePlayers: number;
}

export function validateDraft(slots: DepthSlot[], draft: Draft, players: Map<string, DepthPlayerInfo>, known: Map<string, number>, gameDayActives: number): DraftCheck {
  const errors: DepthIssue[] = [];
  const warnings: DepthIssue[] = [];
  const unique = new Set<string>();
  for (const s of slots) {
    const ids = draft[s.slot] ?? [];
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
      if (!isAvailable(p)) {
        errors.push({ code: "DEPTH_CHART_PLAYER_UNAVAILABLE", slot: s.slot, depth, playerId: id });
        return;
      }
      here++;
      unique.add(id);
      if (isOutOfPosition(p!, s, known)) warnings.push({ code: "OUT_OF_POSITION", slot: s.slot, depth, playerId: id });
    });
    if (here < s.minStarters) errors.push({ code: "DEPTH_CHART_INCOMPLETE", slot: s.slot, depth: null, playerId: null });
  }
  if (unique.size > gameDayActives) errors.push({ code: "DEPTH_CHART_TOO_MANY_ACTIVES", slot: null, depth: null, playerId: null });
  return { errors, warnings, uniquePlayers: unique.size };
}

/** Troca de lugar com o vizinho (`delta` −1 sobe, +1 desce). */
export function moveInSlot(draft: Draft, slot: string, index: number, delta: number): Draft {
  const ids = [...(draft[slot] ?? [])];
  const to = index + delta;
  if (index < 0 || index >= ids.length || to < 0 || to >= ids.length) return draft;
  [ids[index], ids[to]] = [ids[to], ids[index]];
  return { ...draft, [slot]: ids };
}

/** Leva o jogador da posição `from` para `to` dentro da vaga (arrastar e soltar). */
export function reorder(draft: Draft, slot: string, from: number, to: number): Draft {
  const ids = [...(draft[slot] ?? [])];
  if (from < 0 || from >= ids.length) return draft;
  const [id] = ids.splice(from, 1);
  ids.splice(Math.max(0, Math.min(to, ids.length)), 0, id);
  return { ...draft, [slot]: ids };
}

export function removeFromSlot(draft: Draft, slot: string, playerId: string): Draft {
  return { ...draft, [slot]: (draft[slot] ?? []).filter((id) => id !== playerId) };
}

/** Acrescenta no fim da vaga (sem repetir), respeitando a profundidade máxima. */
export function addToSlot(draft: Draft, slot: string, playerId: string, maxDepth: number): Draft {
  const ids = draft[slot] ?? [];
  if (ids.includes(playerId) || ids.length >= maxDepth) return draft;
  return { ...draft, [slot]: [...ids, playerId] };
}

export function sameDraft(a: Draft, b: Draft): boolean {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const k of keys) if ((a[k] ?? []).join() !== (b[k] ?? []).join()) return false;
  return true;
}

/** Vagas em que `theirs` difere de `base` (o que a outra sessão mudou). */
export function changedSlots(base: Draft, theirs: Draft): string[] {
  const keys = new Set([...Object.keys(base), ...Object.keys(theirs)]);
  return [...keys].filter((k) => (base[k] ?? []).join() !== (theirs[k] ?? []).join());
}

/** Corpo do PUT: todas as vagas, na ordem do depth chart. */
export function toRequest(slots: DepthSlot[], draft: Draft, version: number) {
  return { version, slots: slots.map((s) => ({ slot: s.slot, playerIds: [...(draft[s.slot] ?? [])] })) };
}
