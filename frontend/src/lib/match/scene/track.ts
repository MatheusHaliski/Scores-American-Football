/**
 * Trajetórias dos atletas e da bola num roteiro de lance, sem React (testáveis em node). Tudo no referencial do ataque
 * (`d`, `w` em jardas; altura `h` em metros). Uma trajetória é uma lista de quadros-chave com a ação do atleta a partir dali;
 * entre dois quadros a posição anda em linha reta, e a direção do corpo segue o deslocamento (suavizado) ou um `face` fixo.
 * A fase da passada sai da distância percorrida, então o mesmo instante dá sempre a mesma pose (avançar, voltar e pular a
 * animação não acumulam erro).
 */

/** Ações que a pose sabe desenhar (`pose.ts`). `run` cobre andar e correr; a velocidade decide a amplitude. */
export type Action =
  | "three" | "two" | "up" | "qbUnder" | "kneel"
  | "run" | "backpedal" | "block" | "throw" | "catch" | "handoff" | "dive" | "down" | "kick" | "celebrate" | "spike" | "snap";

export interface Keyframe {
  t: number;
  d: number;
  w: number;
  act: Action;
  /** direção do corpo (rad no referencial do ataque, 0 = sentido do ataque, +π/2 = direita); sem ela, segue o movimento */
  face?: number;
}

export interface TrackSample {
  d: number;
  w: number;
  act: Action;
  /** progresso da ação no trecho (0–1) */
  p: number;
  face: number;
  /** velocidade (jardas/s) */
  speed: number;
  /** distância percorrida até aqui (jardas): fase da passada */
  dist: number;
  /** ação anterior e há quanto tempo a atual começou (mistura de poses sem tranco) */
  prevAct: Action;
  since: number;
}

function segIndex(kfs: Keyframe[], t: number): number {
  let i = 0;
  while (i + 1 < kfs.length && kfs[i + 1].t <= t) i++;
  return i;
}

function posAt(kfs: Keyframe[], t: number): [number, number] {
  if (t <= kfs[0].t) return [kfs[0].d, kfs[0].w];
  const i = segIndex(kfs, t);
  const a = kfs[i];
  const b = kfs[i + 1];
  if (!b) return [a.d, a.w];
  const u = b.t > a.t ? (t - a.t) / (b.t - a.t) : 1;
  return [a.d + (b.d - a.d) * u, a.w + (b.w - a.w) * u];
}

/** Distância acumulada até `t` (jardas). */
function distAt(kfs: Keyframe[], t: number): number {
  let acc = 0;
  for (let i = 0; i + 1 < kfs.length; i++) {
    const a = kfs[i];
    const b = kfs[i + 1];
    if (t <= a.t) break;
    const len = Math.hypot(b.d - a.d, b.w - a.w);
    if (t >= b.t) acc += len;
    else {
      acc += len * ((t - a.t) / Math.max(1e-6, b.t - a.t));
      break;
    }
  }
  return acc;
}

/** Ângulo no referencial do ataque de um deslocamento (dd, dw): 0 = sentido do ataque, +π/2 = direita. */
export const headingOf = (dd: number, dw: number) => Math.atan2(dw, dd);

/** Diferença angular em (−π, π]. */
export function angleDiff(a: number, b: number): number {
  let x = (a - b) % (Math.PI * 2);
  if (x > Math.PI) x -= Math.PI * 2;
  if (x <= -Math.PI) x += Math.PI * 2;
  return x;
}

const WINDOW = 0.14;

/** Amostra a trajetória no instante `t` (antes do primeiro quadro, o primeiro; depois do último, o último). */
export function sampleTrack(kfs: Keyframe[], t: number, defaultFace = 0): TrackSample {
  const i = segIndex(kfs, t);
  const k = kfs[i];
  const next = kfs[i + 1];
  const [d, w] = posAt(kfs, t);
  const p = next && next.t > k.t ? Math.min(1, Math.max(0, (t - k.t) / (next.t - k.t))) : 1;
  // velocidade e direção por diferença centrada (suaviza as curvas)
  const [d0, w0] = posAt(kfs, t - WINDOW);
  const [d1, w1] = posAt(kfs, t + WINDOW);
  const speed = Math.hypot(d1 - d0, w1 - w0) / (2 * WINDOW);
  let face: number;
  if (k.face != null) face = k.face;
  else if (speed > 0.4) face = headingOf(d1 - d0, w1 - w0);
  else {
    // parado: a última direção conhecida (um quadro com face ou o último deslocamento)
    face = defaultFace;
    for (let j = i; j >= 0; j--) {
      const q = kfs[j];
      if (q.face != null) {
        face = q.face;
        break;
      }
      const prev = kfs[j - 1];
      if (prev && Math.hypot(q.d - prev.d, q.w - prev.w) > 0.05) {
        face = headingOf(q.d - prev.d, q.w - prev.w);
        break;
      }
    }
  }
  const prevAct = i > 0 ? kfs[i - 1].act : k.act;
  return { d, w, act: k.act, p, face, speed, dist: distAt(kfs, t), prevAct, since: Math.max(0, t - k.t) };
}

/* ───────────────────────── bola ───────────────────────── */

export type BallSeg =
  | { kind: "held"; t0: number; t1: number; actor: number }
  | { kind: "flight"; t0: number; t1: number; from: [number, number, number]; to: [number, number, number]; apex: number }
  | { kind: "rest"; t0: number; t1: number; at: [number, number, number] };

/** Ponto da bola na mão de um atleta: um pouco à frente do corpo, na altura do peito. */
export const CARRY = { forward: 0.35, height: 1.05 };

export interface BallSample {
  d: number;
  w: number;
  h: number;
  /** em voo (gira) */
  flying: boolean;
  /** atleta com a bola (−1 = ninguém) */
  holder: number;
}

/** Altura de um voo parabólico: `apex` metros acima da reta entre a saída e a chegada, no meio do caminho. */
export function flightHeight(from: number, to: number, apex: number, u: number): number {
  return from + (to - from) * u + 4 * apex * u * (1 - u);
}

/**
 * Bola no instante `t`. `actorAt` dá a posição e a direção de um atleta no mesmo instante (a bola segura acompanha a mão).
 * Fora de todo segmento, vale o último (a bola fica parada no ponto final).
 */
export function sampleBall(segs: BallSeg[], t: number, actorAt: (i: number, t: number) => { d: number; w: number; face: number }): BallSample {
  // os segmentos são contíguos e em ordem: vale o primeiro que ainda não terminou
  const seg = segs.find((s) => t < s.t1) ?? segs[segs.length - 1];
  switch (seg.kind) {
    case "held": {
      const a = actorAt(seg.actor, t);
      const fy = CARRY.forward / 0.9144;
      return { d: a.d + Math.cos(a.face) * fy, w: a.w + Math.sin(a.face) * fy, h: CARRY.height, flying: false, holder: seg.actor };
    }
    case "flight": {
      const u = Math.min(1, Math.max(0, (t - seg.t0) / Math.max(1e-6, seg.t1 - seg.t0)));
      return {
        d: seg.from[0] + (seg.to[0] - seg.from[0]) * u,
        w: seg.from[1] + (seg.to[1] - seg.from[1]) * u,
        h: flightHeight(seg.from[2], seg.to[2], seg.apex, u),
        flying: u < 1,
        holder: -1,
      };
    }
    case "rest":
    default:
      return { d: seg.at[0], w: seg.at[1], h: seg.at[2], flying: false, holder: -1 };
  }
}
