/**
 * Geometria do campo de futebol americano, em metros, centrada no meio-campo: x ao longo do comprimento (o ataque da casa vai
 * para +x), z ao longo da largura. Medidas oficiais: 120 × 53⅓ jardas (100 de jogo + duas end zones de 10). As hash marks
 * mudam com as regras (D-4): profissionais a 70 pés e 9 polegadas da lateral (18 pés e 6 polegadas entre elas, a largura
 * das traves), universitárias a 60 pés (40 pés entre elas).
 *
 * Posição no campo em "jarda absoluta": 0 = linha de gol da casa, 100 = linha de gol do visitante.
 */
import type { Ruleset } from "@/lib/api/meta";

export const YARD_M = 0.9144;
const FOOT_YD = 1 / 3;

export const FIELD = {
  playYd: 100,
  endZoneYd: 10,
  lengthYd: 120,
  widthYd: 160 / 3,
} as const;

export const FIELD_M = {
  length: FIELD.lengthYd * YARD_M,
  width: FIELD.widthYd * YARD_M,
  endZone: FIELD.endZoneYd * YARD_M,
} as const;

/** Distância da lateral até a hash mark, em jardas. */
export const HASH_FROM_SIDELINE_YD: Record<Ruleset, number> = {
  PRO: (70 + 9 / 12) * FOOT_YD,
  COLLEGE: 60 * FOOT_YD,
};

/** Traves: vão entre os postes e altura do travessão (iguais nas duas regras), em metros. */
export const GOALPOST_M = {
  width: (18 + 6 / 12) * FOOT_YD * YARD_M,
  crossbar: 10 * FOOT_YD * YARD_M,
  upright: 35 * FOOT_YD * YARD_M,
} as const;

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

/** x (m) de uma jarda absoluta; aceita a end zone (−10 a 110). */
export function yardToX(absYd: number): number {
  return (absYd - FIELD.playYd / 2) * YARD_M;
}

/** z (m) das duas fileiras de hash marks (−z e +z). */
export function hashZ(ruleset: Ruleset): number {
  return (FIELD.widthYd / 2 - HASH_FROM_SIDELINE_YD[ruleset]) * YARD_M;
}

export interface YardLine {
  absYd: number;
  x: number;
  /** linha de gol (0 e 100) */
  goal: boolean;
  /** número pintado no campo (10, 20 … 50 … 20, 10); ausente nas linhas de 5 */
  label?: number;
}

/** Linhas de 5 em 5 jardas, de gol a gol: 21 linhas. */
export function yardLines(): YardLine[] {
  const out: YardLine[] = [];
  for (let yd = 0; yd <= FIELD.playYd; yd += 5) {
    const line: YardLine = { absYd: yd, x: yardToX(yd), goal: yd === 0 || yd === FIELD.playYd };
    if (yd % 10 === 0 && !line.goal) line.label = yd <= 50 ? yd : FIELD.playYd - yd;
    out.push(line);
  }
  return out;
}

/** Jardas com hash mark: todas entre 1 e 99 que não caem numa linha de 5. */
export function hashYards(): number[] {
  const out: number[] = [];
  for (let yd = 1; yd < FIELD.playYd; yd++) if (yd % 5 !== 0) out.push(yd);
  return out;
}

export interface DownMarkers {
  scrimmageX: number;
  firstDownX: number;
  /** a primeira descida estaria além da linha de gol: "and goal", sem linha amarela */
  goalToGo: boolean;
}

/**
 * Linha de scrimmage (azul) e de primeira descida (amarela) para um ataque indo para +x.
 * @param ballOnAbsYd posição da bola (0–100)
 * @param toGo jardas que faltam para a primeira descida
 */
export function downMarkers(ballOnAbsYd: number, toGo: number): DownMarkers {
  const ball = clamp(ballOnAbsYd, 0, FIELD.playYd);
  const target = ball + Math.max(0, toGo);
  const goalToGo = target >= FIELD.playYd;
  return { scrimmageX: yardToX(ball), firstDownX: yardToX(goalToGo ? FIELD.playYd : target), goalToGo };
}

export interface FieldPosition {
  /** campo de quem ataca (OWN), do adversário (OPP) ou o meio-campo (MID) */
  side: "OWN" | "OPP" | "MID";
  yard: number;
}

/** Posição de transmissão ("own 25", "opp 40", "50") do ponto de vista de quem ataca para +x; o texto vem do i18n. */
export function fieldPosition(absYd: number): FieldPosition {
  const yd = Math.round(clamp(absYd, 0, FIELD.playYd));
  if (yd === 50) return { side: "MID", yard: 50 };
  return yd < 50 ? { side: "OWN", yard: yd } : { side: "OPP", yard: FIELD.playYd - yd };
}
