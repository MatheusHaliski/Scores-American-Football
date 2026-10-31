/**
 * Quem veste cada vaga do lance no campo 3D, sem React (testável em node). O estado da partida não traz os 22 em campo nem o
 * depth chart: a fonte é o box score de `live.home/away.players` (posição, número e snaps de quem já jogou). Cada vaga pega o
 * jogador da posição com mais snaps que ainda não está em campo; os nomeados no lance (`playerIds`: quem correu, passou,
 * recebeu, derrubou, chutou) vão primeiro para as vagas de quem age no roteiro. Sem jogador da posição, a vaga fica sem número
 * (camisa lisa): o front não inventa jogador (Q-60).
 */
import type { PlayerBoxScore } from "@/lib/api/match-types";
import type { Role, Slot } from "./formation";

export interface Wearer {
  playerId: string | null;
  number: number | null;
}

/** Posições do box score que servem a cada papel, em ordem de preferência. */
export const ROLE_POSITIONS: Record<Role, string[]> = {
  QB: ["QB"],
  RB: ["RB", "FB"],
  FB: ["FB", "RB"],
  WR: ["WR"],
  TE: ["TE"],
  LT: ["LT"],
  LG: ["LG"],
  C: ["C"],
  RG: ["RG"],
  RT: ["RT"],
  DE: ["DE"],
  DT: ["DT"],
  OLB: ["OLB", "MLB"],
  MLB: ["MLB", "OLB"],
  CB: ["CB"],
  FS: ["FS", "SS"],
  SS: ["SS", "FS"],
  K: ["K"],
  P: ["P"],
  LS: ["LS"],
  H: ["P", "QB"],
  KR: ["WR", "RB", "CB"],
  ST: ["OLB", "MLB", "TE", "FB", "CB", "SS", "FS", "RB", "WR", "DE"],
};

/**
 * Número de cada vaga de um time. `slots` são só as vagas desse time; `named` mapeia vaga → id de jogador nomeado no lance.
 */
export function lineupFor(players: PlayerBoxScore[], slots: Slot[], named: Map<number, string> = new Map()): Wearer[] {
  const byId = new Map(players.map((p) => [p.playerId, p]));
  const used = new Set<string>();
  const out: Wearer[] = slots.map(() => ({ playerId: null, number: null }));
  // nomeados primeiro (se o jogador é deste time)
  for (const [slot, id] of named) {
    const p = byId.get(id);
    if (!p || used.has(id) || slot < 0 || slot >= slots.length) continue;
    used.add(id);
    out[slot] = { playerId: id, number: p.jerseyNumber };
  }
  const ranked = [...players].sort((a, b) => b.snaps - a.snaps || a.jerseyNumber - b.jerseyNumber);
  slots.forEach((s, i) => {
    if (out[i].playerId) return;
    for (const pos of ROLE_POSITIONS[s.role]) {
      const p = ranked.find((x) => x.position === pos && !used.has(x.playerId));
      if (p) {
        used.add(p.playerId);
        out[i] = { playerId: p.playerId, number: p.jerseyNumber };
        return;
      }
    }
  });
  return out;
}

/**
 * Números das 22 vagas de um roteiro: o ataque (ou quem chuta) com o box score do clube dele, a defesa com o do outro; os
 * nomeados do lance (`named`: vaga → posição em `playerIds`) entram primeiro, cada um no próprio time.
 */
export function numbersFor(
  slots: Slot[],
  offenseClubId: string,
  defenseClubId: string,
  players: Record<string, PlayerBoxScore[]>,
  named: Record<number, number> = {},
  playerIds: string[] = [],
): (number | null)[] {
  const out: (number | null)[] = slots.map(() => null);
  for (const side of ["O", "D"] as const) {
    const club = side === "O" ? offenseClubId : defenseClubId;
    const idx = slots.flatMap((s, i) => (s.side === side ? [i] : []));
    const local = new Map<number, string>();
    idx.forEach((slotIndex, k) => {
      const n = named[slotIndex];
      if (n != null && playerIds[n]) local.set(k, playerIds[n]);
    });
    const wearers = lineupFor(players[club] ?? [], idx.map((i) => slots[i]), local);
    idx.forEach((slotIndex, k) => (out[slotIndex] = wearers[k].number));
  }
  return out;
}
