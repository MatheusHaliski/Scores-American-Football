/**
 * Atleta procedural do campo 3D (sem asset): pose por ação e a matriz de cada peça do corpo, sem React (testável em node).
 * O corpo é feito de peças simples (cápsulas, caixas, esferas) desenhadas com instancing: uma malha por peça para os 22
 * atletas, e a cena só atualiza as matrizes. Capacete com grade, ombreiras, camisa (com o número na frente e nas costas),
 * calça, meião e chuteira.
 *
 * Referencial do atleta: frente em +x, cima em +y, direita em +z (o mesmo do mundo quando ele olha para +x). A pose são
 * ângulos (rad) das articulações; a altura do quadril sai da própria pose para o pé de apoio ficar no chão (ninguém flutua
 * nem afunda) e, deitado ou ajoelhado, para o ponto mais baixo do corpo encostar no gramado.
 */
import { Euler, Matrix4, Quaternion, Vector3 } from "three";
import type { Action, TrackSample } from "./track";
import { YARD_M } from "@/lib/field/geometry";

export interface Pose {
  /** inclinação do tronco para a frente */
  lean: number;
  hipL: number;
  hipR: number;
  kneeL: number;
  kneeR: number;
  /** braço para a frente (0 = caído ao lado do corpo, π = esticado para cima), relativo ao tronco */
  shL: number;
  shR: number;
  /** braço aberto para o lado */
  abL: number;
  abR: number;
  elL: number;
  elR: number;
  /** corpo inteiro tombado para a frente (mergulho, no chão) */
  pitch: number;
  /** salto (m) */
  lift: number;
  /** o que encosta no chão: só os pés, ou o ponto mais baixo do corpo (deitado, ajoelhado) */
  ground: "feet" | "all";
}

export const BODY = {
  thigh: 0.46,
  shin: 0.46,
  hipZ: 0.12,
  torso: 0.56,
  shoulderZ: 0.25,
  upperArm: 0.3,
  forearm: 0.29,
  ankle: 0.07,
  helmetR: 0.165,
} as const;

const STAND: Pose = { lean: 0.06, hipL: 0.04, hipR: -0.02, kneeL: 0.1, kneeR: 0.08, shL: 0.12, shR: 0.12, abL: 0.14, abR: 0.14, elL: 0.35, elR: 0.35, pitch: 0, lift: 0, ground: "feet" };

const clamp01 = (v: number) => Math.max(0, Math.min(1, v));
const ease = (u: number) => u * u * (3 - 2 * u);
const lerp = (a: number, b: number, u: number) => a + (b - a) * u;

/** Passada: uma volta completa (dois passos) a cada 2,4 m. */
const STRIDE_M = 2.4;

function runPose(phase: number, amp: number, back = false): Pose {
  const s = Math.sin(phase);
  const k = (c: number) => 0.2 + 1.15 * amp * (0.5 + 0.5 * c);
  const sign = back ? -1 : 1;
  return {
    lean: back ? -0.04 : 0.06 + 0.28 * amp,
    hipL: 0.12 + sign * 0.85 * amp * s,
    hipR: 0.12 - sign * 0.85 * amp * s,
    kneeL: k(Math.cos(phase)),
    kneeR: k(-Math.cos(phase)),
    shL: 0.15 - 0.8 * amp * s,
    shR: 0.15 + 0.8 * amp * s,
    abL: 0.12,
    abR: 0.12,
    elL: 1.35,
    elR: 1.35,
    pitch: 0,
    lift: 0,
    ground: "feet",
  };
}

/** Pose de uma ação: `p` é o progresso das ações com começo e fim (arremesso, chute, mergulho, comemoração). */
export function actionPose(act: Action, p: number, phase: number, speed: number): Pose {
  const amp = Math.max(0.15, Math.min(1, speed / 7));
  switch (act) {
    case "three":
    case "snap":
      // linha: tronco quase na horizontal, a mão direita no chão, a esquerda apoiada na coxa
      return { lean: 1.2, hipL: 1.3, hipR: 0.95, kneeL: 1.75, kneeR: 1.35, shL: 0.7, shR: 1.48, abL: 0.1, abR: 0.05, elL: 1.1, elR: 0.02, pitch: 0, lift: 0, ground: "feet" };
    case "two":
      return { lean: 0.55, hipL: 0.85, hipR: 0.6, kneeL: 1.05, kneeR: 0.85, shL: 0.55, shR: 0.55, abL: 0.18, abR: 0.18, elL: 0.9, elR: 0.9, pitch: 0, lift: 0, ground: "feet" };
    case "qbUnder":
      return { lean: 0.75, hipL: 0.85, hipR: 0.85, kneeL: 1.05, kneeR: 1.05, shL: 1.25, shR: 1.25, abL: 0.05, abR: 0.05, elL: 0.55, elR: 0.55, pitch: 0, lift: 0, ground: "feet" };
    case "kneel":
      return { lean: 0.12, hipL: 1.45, hipR: -0.05, kneeL: 1.55, kneeR: 1.62, shL: 0.9, shR: 0.9, abL: 0.1, abR: 0.1, elL: 1.1, elR: 1.1, pitch: 0, lift: 0, ground: "all" };
    case "run":
      return speed < 0.35 ? STAND : runPose(phase, amp);
    case "backpedal":
      return speed < 0.35 ? STAND : runPose(-phase, amp * 0.6, true);
    case "block": {
      const sh = Math.sin(phase * 2) * 0.12;
      return { lean: 0.45, hipL: 0.75 + sh, hipR: 0.45 - sh, kneeL: 0.95, kneeR: 0.7, shL: 1.95, shR: 1.95, abL: 0.15, abR: 0.15, elL: 0.75, elR: 0.75, pitch: 0, lift: 0, ground: "feet" };
    }
    case "throw": {
      // braço armado atrás da cabeça, soltura rápida e acompanhamento para a frente
      const wind = clamp01(p / 0.5);
      const release = clamp01((p - 0.5) / 0.25);
      const arm = release > 0 ? lerp(Math.PI + 0.55, 0.75, ease(release)) : lerp(0.6, Math.PI + 0.55, ease(wind));
      return { lean: lerp(-0.08, 0.3, release), hipL: 0.38, hipR: -0.28, kneeL: 0.3, kneeR: 0.2, shL: 1.55, shR: arm, abL: 0.25, abR: 0.25, elL: 0.45, elR: release > 0 ? lerp(1.5, 0.2, release) : 1.5, pitch: 0, lift: 0, ground: "feet" };
    }
    case "handoff":
      return { lean: 0.35, hipL: 0.45, hipR: -0.1, kneeL: 0.55, kneeR: 0.35, shL: 0.9, shR: 1.0, abL: 0.2, abR: 0.55, elL: 0.6, elR: 0.35, pitch: 0, lift: 0, ground: "feet" };
    case "catch":
      return { lean: 0.02, hipL: 0.25, hipR: -0.1, kneeL: 0.35, kneeR: 0.25, shL: 2.55, shR: 2.55, abL: 0.18, abR: 0.18, elL: 0.3, elR: 0.3, pitch: 0, lift: 0, ground: "feet" };
    case "dive": {
      const u = ease(clamp01(p));
      return { lean: 0.2, hipL: lerp(0.4, -0.15, u), hipR: lerp(-0.2, -0.1, u), kneeL: lerp(0.6, 0.35, u), kneeR: lerp(0.5, 0.3, u), shL: lerp(1.6, 2.95, u), shR: lerp(1.6, 2.95, u), abL: 0.2, abR: 0.2, elL: 0.25, elR: 0.25, pitch: lerp(0.15, 1.45, u), lift: 0.22 * Math.sin(Math.PI * clamp01(p)), ground: "all" };
    }
    case "down":
      return { lean: 0.1, hipL: -0.1, hipR: 0.05, kneeL: 0.35, kneeR: 0.55, shL: 2.9, shR: 2.6, abL: 0.35, abR: 0.25, elL: 0.3, elR: 0.5, pitch: 1.5, lift: 0, ground: "all" };
    case "kick": {
      const u = clamp01(p);
      const swing = u < 0.45 ? lerp(-0.7, 1.55, ease(u / 0.45)) : lerp(1.55, 0.35, ease((u - 0.45) / 0.55));
      const knee = u < 0.35 ? lerp(1.3, 0.05, ease(u / 0.35)) : lerp(0.05, 0.3, ease((u - 0.35) / 0.65));
      return { lean: -0.15, hipL: 0.12, hipR: swing, kneeL: 0.22, kneeR: knee, shL: 0.6, shR: -0.3, abL: 1.05, abR: 0.8, elL: 0.3, elR: 0.3, pitch: 0, lift: 0, ground: "feet" };
    }
    case "celebrate": {
      const u = clamp01(p);
      const jump = Math.abs(Math.sin(u * Math.PI * 2));
      return { lean: 0, hipL: 0.15 * jump, hipR: 0.15 * jump, kneeL: 0.25 + 0.3 * (1 - jump), kneeR: 0.25 + 0.3 * (1 - jump), shL: Math.PI - 0.15, shR: Math.PI - 0.15, abL: 0.35, abR: 0.35, elL: 0.15, elR: 0.15, pitch: 0, lift: u >= 1 ? 0 : 0.42 * jump, ground: "feet" };
    }
    case "spike": {
      const u = clamp01(p / 0.5);
      return { lean: 0.25, hipL: 0.35, hipR: -0.15, kneeL: 0.4, kneeR: 0.25, shL: 0.4, shR: lerp(Math.PI - 0.2, 0.45, ease(u)), abL: 0.25, abR: 0.15, elL: 0.6, elR: 0.15, pitch: 0, lift: 0, ground: "feet" };
    }
    case "up":
    default:
      return STAND;
  }
}

const BLEND_S = 0.22;

function mix(a: Pose, b: Pose, u: number): Pose {
  return {
    lean: lerp(a.lean, b.lean, u),
    hipL: lerp(a.hipL, b.hipL, u),
    hipR: lerp(a.hipR, b.hipR, u),
    kneeL: lerp(a.kneeL, b.kneeL, u),
    kneeR: lerp(a.kneeR, b.kneeR, u),
    shL: lerp(a.shL, b.shL, u),
    shR: lerp(a.shR, b.shR, u),
    abL: lerp(a.abL, b.abL, u),
    abR: lerp(a.abR, b.abR, u),
    elL: lerp(a.elL, b.elL, u),
    elR: lerp(a.elR, b.elR, u),
    pitch: lerp(a.pitch, b.pitch, u),
    lift: lerp(a.lift, b.lift, u),
    ground: u < 0.5 ? a.ground : b.ground,
  };
}

/** Pose de uma amostra da trajetória, com a troca de ação misturada em ~0,2 s (sem tranco). */
export function poseOf(s: TrackSample): Pose {
  const phase = ((s.dist * YARD_M) / STRIDE_M) * Math.PI * 2;
  const now = actionPose(s.act, s.p, phase, s.speed);
  if (s.prevAct === s.act || s.since >= BLEND_S) return now;
  const before = actionPose(s.prevAct, 1, phase, s.speed);
  return mix(before, now, ease(s.since / BLEND_S));
}

/* ───────────────────────── esqueleto e peças ───────────────────────── */

export type PartName =
  | "torso" | "pads" | "pelvis" | "thigh" | "shin" | "shoe" | "upperArm" | "forearm" | "helmet" | "facemask" | "face" | "numberFront" | "numberBack";

/** Peças por atleta (as duplas são esquerda e direita). */
export const PART_COUNT: Record<PartName, number> = {
  torso: 1, pads: 1, pelvis: 1, thigh: 2, shin: 2, shoe: 2, upperArm: 2, forearm: 2, helmet: 1, facemask: 1, face: 1, numberFront: 1, numberBack: 1,
};
export const PART_NAMES = Object.keys(PART_COUNT) as PartName[];

const UP = new Vector3(0, 1, 0);
const tmpQ = new Quaternion();
const tmpV = new Vector3();
const tmpM = new Matrix4();

/** Rotação em torno de z (tombar para a frente: +y vai para +x com ângulo positivo). */
function pitchVec(v: Vector3, a: number): Vector3 {
  const c = Math.cos(-a);
  const s = Math.sin(-a);
  return v.set(v.x * c - v.y * s, v.x * s + v.y * c, v.z);
}

interface Seg {
  a: Vector3;
  b: Vector3;
}

export interface Skeleton {
  /** pontos no referencial do corpo, com a pelve na origem (antes de pôr no chão) */
  hips: [Vector3, Vector3];
  knees: [Vector3, Vector3];
  ankles: [Vector3, Vector3];
  toes: [Vector3, Vector3];
  shoulders: [Vector3, Vector3];
  elbows: [Vector3, Vector3];
  hands: [Vector3, Vector3];
  chest: Vector3;
  head: Vector3;
  /** altura da pelve para o corpo apoiar no chão */
  pelvisY: number;
  /** direção do tronco */
  torsoDir: Vector3;
  headDir: Vector3;
}

/** Esqueleto da pose: pernas e braços por ângulos, tronco inclinado, corpo tombado e altura da pelve. */
export function skeleton(pose: Pose): Skeleton {
  const legDir = (a: number) => new Vector3(Math.sin(a), -Math.cos(a), 0);
  const tilt = (v: Vector3) => pitchVec(v, pose.pitch);
  const hips: [Vector3, Vector3] = [new Vector3(0, 0, -BODY.hipZ), new Vector3(0, 0, BODY.hipZ)];
  const knees: [Vector3, Vector3] = [
    hips[0].clone().addScaledVector(legDir(pose.hipL), BODY.thigh),
    hips[1].clone().addScaledVector(legDir(pose.hipR), BODY.thigh),
  ];
  const ankles: [Vector3, Vector3] = [
    knees[0].clone().addScaledVector(legDir(pose.hipL - pose.kneeL), BODY.shin),
    knees[1].clone().addScaledVector(legDir(pose.hipR - pose.kneeR), BODY.shin),
  ];
  const toes: [Vector3, Vector3] = [ankles[0].clone().add(new Vector3(0.17, -BODY.ankle, 0)), ankles[1].clone().add(new Vector3(0.17, -BODY.ankle, 0))];
  const heels: [Vector3, Vector3] = [ankles[0].clone().add(new Vector3(-0.06, -BODY.ankle, 0)), ankles[1].clone().add(new Vector3(-0.06, -BODY.ankle, 0))];
  const torsoDir = pitchVec(new Vector3(0, 1, 0), pose.lean);
  const chest = torsoDir.clone().multiplyScalar(BODY.torso);
  const shoulders: [Vector3, Vector3] = [chest.clone().add(new Vector3(0, -0.04, -BODY.shoulderZ)), chest.clone().add(new Vector3(0, -0.04, BODY.shoulderZ))];
  // braço: ângulo para a frente (relativo ao tronco) e abertura lateral
  const armDir = (p: number, ab: number, side: number) => pitchVec(new Vector3(Math.sin(p) * Math.cos(ab), -Math.cos(p) * Math.cos(ab), side * Math.sin(ab)), pose.lean);
  const elbows: [Vector3, Vector3] = [
    shoulders[0].clone().addScaledVector(armDir(pose.shL, pose.abL, -1), BODY.upperArm),
    shoulders[1].clone().addScaledVector(armDir(pose.shR, pose.abR, 1), BODY.upperArm),
  ];
  const hands: [Vector3, Vector3] = [
    elbows[0].clone().addScaledVector(armDir(pose.shL + pose.elL, pose.abL * 0.6, -1), BODY.forearm),
    elbows[1].clone().addScaledVector(armDir(pose.shR + pose.elR, pose.abR * 0.6, 1), BODY.forearm),
  ];
  // a cabeça fica mais em pé que o tronco (o atleta olha para a frente)
  const headDir = pitchVec(new Vector3(0, 1, 0), pose.lean * 0.45);
  const head = chest.clone().addScaledVector(headDir, 0.25);
  const all = [hips, knees, ankles, toes, shoulders, elbows, hands].flat().concat([chest, head]);
  for (const v of all) tilt(v);
  for (const v of heels) tilt(v);
  tilt(torsoDir);
  tilt(headDir);
  // apoio: o pé mais baixo no chão (em pé) ou o ponto mais baixo do corpo (deitado, ajoelhado)
  let minY: number;
  if (pose.ground === "feet") minY = Math.min(toes[0].y, toes[1].y, heels[0].y, heels[1].y);
  else minY = Math.min(...[...toes, ...heels, ...knees, ...hands, ...elbows].map((v) => v.y), chest.y - 0.14, head.y - BODY.helmetR, -0.12);
  const pelvisY = -minY + pose.lift;
  return { hips, knees, ankles, toes, shoulders, elbows, hands, chest, head, pelvisY, torsoDir, headDir };
}

/** Matriz de uma cápsula/caixa entre dois pontos (o eixo +y da geometria vai de `a` para `b`). */
function between(seg: Seg, base: Matrix4, out: Matrix4): Matrix4 {
  tmpV.subVectors(seg.b, seg.a);
  const len = tmpV.length();
  tmpQ.setFromUnitVectors(UP, len > 1e-6 ? tmpV.divideScalar(len) : UP);
  const mid = seg.a.clone().add(seg.b).multiplyScalar(0.5);
  tmpM.compose(mid, tmpQ, new Vector3(1, 1, 1));
  return out.multiplyMatrices(base, tmpM);
}

function along(at: Vector3, dir: Vector3, base: Matrix4, out: Matrix4, scale?: Vector3): Matrix4 {
  tmpQ.setFromUnitVectors(UP, dir.clone().normalize());
  tmpM.compose(at, tmpQ, scale ?? new Vector3(1, 1, 1));
  return out.multiplyMatrices(base, tmpM);
}

/**
 * Escreve as matrizes das peças de um atleta. `out[part][k]` é a k-ésima peça daquele tipo (esquerda, direita). O atleta
 * fica em (x, z) do mundo, olhando para `yaw` (rotação em torno de y; 0 = olhando para +x), na escala `scale`.
 */
export function athleteMatrices(pose: Pose, x: number, z: number, yaw: number, scale: number, out: Record<PartName, Matrix4[]>): Skeleton {
  const sk = skeleton(pose);
  const base = new Matrix4().compose(new Vector3(x, sk.pelvisY * scale, z), new Quaternion().setFromEuler(new Euler(0, yaw, 0)), new Vector3(scale, scale, scale));
  for (let i = 0; i < 2; i++) {
    between({ a: sk.hips[i], b: sk.knees[i] }, base, out.thigh[i]);
    between({ a: sk.knees[i], b: sk.ankles[i] }, base, out.shin[i]);
    between({ a: sk.shoulders[i], b: sk.elbows[i] }, base, out.upperArm[i]);
    between({ a: sk.elbows[i], b: sk.hands[i] }, base, out.forearm[i]);
    // chuteira: plana em relação ao corpo, apontando para a frente
    const foot = sk.ankles[i].clone().add(pitchVec(new Vector3(0.055, -BODY.ankle * 0.55, 0), pose.pitch));
    tmpQ.setFromEuler(new Euler(0, 0, -pose.pitch));
    tmpM.compose(foot, tmpQ, new Vector3(1, 1, 1));
    out.shoe[i].multiplyMatrices(base, tmpM);
  }
  const pelvisTop = sk.torsoDir.clone().multiplyScalar(0.05);
  along(pelvisTop, sk.torsoDir, base, out.pelvis);
  const torsoMid = sk.torsoDir.clone().multiplyScalar(BODY.torso * 0.55);
  along(torsoMid, sk.torsoDir, base, out.torso);
  along(sk.chest.clone().addScaledVector(sk.torsoDir, -0.03), sk.torsoDir, base, out.pads);
  along(sk.head, sk.headDir, base, out.helmet);
  // rosto e grade: à frente do capacete (o eixo +x do corpo, tombado junto)
  const fwd = pitchVec(new Vector3(1, 0, 0), pose.lean * 0.45 + pose.pitch);
  along(sk.head.clone().addScaledVector(fwd, 0.05).addScaledVector(sk.headDir, -0.03), sk.headDir, base, out.face);
  along(sk.head.clone().addScaledVector(fwd, 0.135).addScaledVector(sk.headDir, -0.06), sk.headDir, base, out.facemask);
  // números: na frente e nas costas da camisa
  const torsoFwd = pitchVec(new Vector3(1, 0, 0), pose.lean + pose.pitch);
  const numAt = sk.torsoDir.clone().multiplyScalar(BODY.torso * 0.6);
  numberPlane(numAt.clone().addScaledVector(torsoFwd, 0.158), torsoFwd, sk.torsoDir, base, out.numberFront);
  numberPlane(numAt.clone().addScaledVector(torsoFwd, -0.158), torsoFwd.clone().negate(), sk.torsoDir, base, out.numberBack);
  return sk;
}

/** Plano do número: a normal aponta para `normal`, o "cima" da textura segue o tronco. */
function numberPlane(at: Vector3, normal: Vector3, up: Vector3, base: Matrix4, out: Matrix4) {
  const n = normal.clone().normalize();
  const u = up.clone().normalize();
  const r = new Vector3().crossVectors(u, n).normalize();
  const m = new Matrix4().makeBasis(r, u, n);
  m.setPosition(at);
  out.multiplyMatrices(base, m);
}

/** Yaw do mundo a partir da direção no referencial do ataque (0 = sentido do ataque) e do sentido do ataque. */
export function worldYaw(face: number, dir: 1 | -1): number {
  return Math.atan2(-dir * Math.sin(face), dir * Math.cos(face));
}

/** Escala do atleta (o corpo de referência tem ~1,9 m com capacete). */
export const ATHLETE_SCALE = 1;
