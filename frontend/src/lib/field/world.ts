/**
 * Do campo da partida para o mundo 3D, sem React (testável em node). O mundo segue `lib/field/geometry`: x ao longo do
 * comprimento (jarda absoluta 0 = linha de gol da casa em −x, 100 = do visitante em +x), y para cima e z ao longo da largura.
 *
 * As jogadas são montadas no "referencial do ataque": `d` são jardas a partir da linha de scrimmage no sentido do ataque
 * (positivo = campo do adversário) e `w` são jardas para a direita de quem ataca. Quem ataca para +x (a casa) tem a direita
 * em +z (o vetor "para a frente × para cima"); quem ataca para −x (o visitante) tem o campo espelhado: x e z trocam de sinal.
 */
import { FIELD, FIELD_M, YARD_M, yardToX } from "./geometry";
import { scrimmageMarkers } from "@/lib/match/live";

/** +1 quando o ataque vai para a jarda 100 (a casa), −1 quando vai para a 0 (o visitante). */
export type AttackDir = 1 | -1;

export const dirOf = (up: boolean): AttackDir => (up ? 1 : -1);

/** Jarda absoluta de um ponto `d` jardas à frente da linha `losAbsYd`, no sentido `dir`. */
export function absYdAt(losAbsYd: number, dir: AttackDir, d: number): number {
  return losAbsYd + dir * d;
}

/** Ponto do referencial do ataque (`d`, `w` em jardas) no mundo: [x, z] em metros. */
export function offenseToWorld(losAbsYd: number, dir: AttackDir, d: number, w: number): [number, number] {
  return [yardToX(absYdAt(losAbsYd, dir, d)), dir * w * YARD_M];
}

/** O inverso de `offenseToWorld` (usado pela câmera do lance e pelos testes). */
export function worldToOffense(losAbsYd: number, dir: AttackDir, x: number, z: number): [number, number] {
  const abs = x / YARD_M + FIELD.playYd / 2;
  return [(abs - losAbsYd) * dir, (z / YARD_M) * dir];
}

/** Jarda absoluta de um x do mundo. */
export function xToAbsYd(x: number): number {
  return x / YARD_M + FIELD.playYd / 2;
}

export interface FieldLines {
  /** x (m) da linha de scrimmage (faixa azul) */
  scrimmageX: number;
  /** x (m) da linha de primeira descida (faixa amarela); null no "and goal", quando ela cairia na linha de gol */
  firstDownX: number | null;
  direction: AttackDir;
}

/**
 * As duas faixas da transmissão no mundo, para os dois sentidos de ataque (usa `scrimmageMarkers`, que já espelha o campo
 * quando o visitante ataca). Sem posse (intervalo, fim), null.
 */
export function fieldLines(ballOnAbsYd: number, distance: number, up: boolean | null): FieldLines | null {
  if (up == null) return null;
  const m = scrimmageMarkers(ballOnAbsYd, distance, up);
  return { scrimmageX: yardToX(m.scrimmageAbsYd), firstDownX: m.goalToGo ? null : yardToX(m.firstDownAbsYd), direction: m.direction };
}

/** Meia largura do campo em jardas (das linhas laterais ao centro). */
export const HALF_WIDTH_YD = FIELD.widthYd / 2;

/** Limites do gramado com as end zones, em metros (as câmeras não passam daqui). */
export const FIELD_X_LIMIT = FIELD_M.length / 2;
